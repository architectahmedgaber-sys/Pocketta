package app.pocketta.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import app.pocketta.core.Money
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** What one side (this phone, or the cloud) holds, shown when the person has to choose. */
data class SyncSide(val records: Int, val commitments: Int, val at: Long, val device: String)

/** A question for the person: first upload, or two different copies. The answer is "local", "cloud" or null (cancel). */
class SyncChoice(val firstUpload: Boolean, val local: SyncSide, val cloud: SyncSide?, val answer: CompletableDeferred<String?>)

data class SyncUi(val signedIn: Boolean = false, val email: String = "", val lastSyncAt: Long = 0L, val status: String = "", val error: String = "", val busy: Boolean = false, val encrypted: Boolean = false)

/** First setup of the encryption password: shows the recovery key once. The answer is the password, or null (cancel). */
class SetupReq(val recovery: String, val answer: CompletableDeferred<String?>)

/** This account's data is sealed: the answer is the password or recovery key (+ a new password), a wipe request, or null (cancel). */
class UnlockAns(val mode: String, val secret: String = "", val newPass: String = "", val wipe: Boolean = false)
class UnlockReq(val bad: Boolean, val answer: CompletableDeferred<UnlockAns?>)

/**
 * Optional sync: sign in with Google (in the browser) and the whole backup is kept in the person's own row in the cloud
 * (Supabase), so the phone and the web app share the same data. Nothing runs until the person taps "Sign in with Google".
 * The backup is end-to-end encrypted on the phone (see E2ee.kt) before it is sent: the cloud only holds a sealed envelope.
 * The URL and the publishable key are public by design; row level security keeps each row private to its owner.
 * Same logic as web/js/sync.js.
 */
object CloudSync {
    private const val BASE = "https://gnbtetbbxekofmjzbzga.supabase.co"
    private const val KEY = "sb_publishable_rjOgzmPudvrnCbc5nkBevg_xq_jPPFt"
    const val REDIRECT = "app.pocketta://auth"
    /** Things that belong to one device (or change by themselves): not synced, not compared, kept when cloud data is applied. */
    private val DEVICE_KEYS = listOf("theme", "lang", "onlineRates", "prevOnlineRates", "gold", "prevGold", "ratesUpdated", "ratesFetchedAt")

    private lateinit var sp: SharedPreferences
    private lateinit var files: File
    private val mutex = Mutex()
    private var started = false

    private val _ui = MutableStateFlow(SyncUi())
    val ui: StateFlow<SyncUi> = _ui
    private val _choice = MutableStateFlow<SyncChoice?>(null)
    val choice: StateFlow<SyncChoice?> = _choice
    private val _setup = MutableStateFlow<SetupReq?>(null)
    val setup: StateFlow<SetupReq?> = _setup
    private val _unlock = MutableStateFlow<UnlockReq?>(null)
    val unlock: StateFlow<UnlockReq?> = _unlock

    fun init(context: Context) {
        if (::sp.isInitialized) return
        sp = context.applicationContext.getSharedPreferences("pocketta_sync", Context.MODE_PRIVATE)
        files = context.applicationContext.filesDir
        publish()
    }

    fun signedIn(): Boolean = ::sp.isInitialized && sp.contains("access")

    private fun publish(status: String = _ui.value.status, error: String = "", busy: Boolean = false) {
        _ui.value = SyncUi(signedIn(), sp.getString("email", "") ?: "", sp.getLong("lastSyncAt", 0L), status, error, busy, !sp.getString("dek", "").isNullOrEmpty())
    }

    // ---------- Sign in / out ----------

    fun signInUri(): Uri = Uri.parse("$BASE/auth/v1/authorize?provider=google&redirect_to=" + Uri.encode(REDIRECT))

    /** Called when Google sends the person back to the app (app.pocketta://auth#access_token=...). */
    fun handleRedirect(uri: Uri): Boolean {
        if (uri.scheme != "app.pocketta" || uri.host != "auth") return false
        val raw = uri.encodedFragment ?: uri.encodedQuery ?: ""
        val p = raw.split("&").mapNotNull { kv -> kv.split("=", limit = 2).takeIf { it.size == 2 }?.let { Uri.decode(it[0]) to Uri.decode(it[1]) } }.toMap()
        if (p["error"] != null) { publish(error = p["error_description"] ?: p["error"] ?: "error"); return true }
        val access = p["access_token"] ?: return true
        sp.edit().clear()
            .putString("access", access).putString("refresh", p["refresh_token"] ?: "")
            .putLong("expires", p["expires_at"]?.toLongOrNull() ?: (System.currentTimeMillis() / 1000 + (p["expires_in"]?.toLongOrNull() ?: 3600)))
            .apply()
        publish(status = "ok")
        return true
    }

    fun signOut() {
        sp.edit().clear().apply()
        publish(status = "")
    }

    // ---------- HTTP ----------

    private fun call(method: String, path: String, token: String?, body: String? = null, extra: Map<String, String> = emptyMap()): Pair<Int, String> {
        val c = URL(BASE + path).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.requestMethod = method
            c.setRequestProperty("apikey", KEY)
            if (token != null) c.setRequestProperty("Authorization", "Bearer $token")
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("User-Agent", "Pocketta")
            extra.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (body != null) {
                c.doOutput = true
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            return code to (stream?.bufferedReader()?.use { it.readText() } ?: "")
        } finally {
            c.disconnect()
        }
    }

    private fun token(): String {
        val access = sp.getString("access", null) ?: error("signed out")
        if (sp.getLong("expires", 0L) - 60 > System.currentTimeMillis() / 1000) return access
        val (code, text) = call("POST", "/auth/v1/token?grant_type=refresh_token", null, JSONObject().put("refresh_token", sp.getString("refresh", "")).toString())
        if (code == 400 || code == 401) { signOut(); error("Signed out. Please sign in again.") }
        if (code !in 200..299) throw IOException("offline")
        val j = JSONObject(text)
        sp.edit().putString("access", j.getString("access_token")).putString("refresh", j.optString("refresh_token", ""))
            .putLong("expires", j.optLong("expires_at", System.currentTimeMillis() / 1000 + j.optLong("expires_in", 3600))).apply()
        return j.getString("access_token")
    }

    private fun api(method: String, path: String, body: String? = null, extra: Map<String, String> = emptyMap()): String {
        val (code, text) = call(method, path, token(), body, extra)
        if (code !in 200..299) error("HTTP $code")
        return text
    }

    private fun whoAmI() {
        if (sp.getString("email", "").isNullOrEmpty()) {
            val u = JSONObject(api("GET", "/auth/v1/user"))
            sp.edit().putString("email", u.optString("email", "")).putString("uid", u.optString("id", "")).apply()
        }
    }

    // ---------- What is synced ----------

    /** The row as stored ([raw]: sealed or an old plain copy) and opened ([data]: the plain backup). */
    private class CloudRow(val data: JSONObject, val raw: JSONObject, val wasEnc: Boolean, val updatedAt: String, val device: String)
    private class Fetched(val raw: JSONObject, val updatedAt: String, val device: String)
    private class Opened(val kind: Int, val row: CloudRow?) // 0 ok, 1 retry (cloud was wiped), 2 cancelled

    private suspend fun fullJson(repo: Repo): JSONObject = JSONObject(PortableBackup.write(PortableBackup.Data(
        repo.dao.wallets(), repo.dao.categories(), repo.dao.allTxns(), repo.dao.bills(), repo.dao.installments(), repo.dao.circles(), repo.dao.loans(), repo.dao.settings(),
    )))

    private fun strip(o: JSONObject): JSONObject {
        o.remove("exportedAt")
        o.optJSONObject("settings")?.let { s -> DEVICE_KEYS.forEach { s.remove(it) } }
        return o
    }

    private fun hash(o: JSONObject): String = MessageDigest.getInstance("SHA-256").digest(o.toString().toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    private fun len(o: JSONObject, k: String) = o.optJSONArray(k)?.length() ?: 0
    private fun isEmpty(o: JSONObject) = o.optJSONObject("settings")?.optBoolean("onboarded", false) != true && len(o, "txns") == 0
    private fun side(o: JSONObject, at: Long, device: String = "") =
        SyncSide(len(o, "txns"), len(o, "bills") + len(o, "installments") + len(o, "circles") + len(o, "loans"), at, device)

    private fun fetchCloud(): Fetched? {
        val rows = JSONArray(api("GET", "/rest/v1/backups?select=data,updated_at,device_name&limit=1"))
        if (rows.length() == 0) return null
        val r = rows.getJSONObject(0)
        return Fetched(r.getJSONObject("data"), r.getString("updated_at"), r.optString("device_name", ""))
    }

    private fun hasKeys() = !sp.getString("dek", "").isNullOrEmpty() && !sp.getString("keys", "").isNullOrEmpty()

    /** Makes sure this phone has the data key: the first time it is made from a new password (and a recovery key shown once). */
    private suspend fun needKeys(): Boolean {
        if (hasKeys()) return true
        val recovery = E2ee.newRecoveryKey()
        val d = CompletableDeferred<String?>()
        _setup.value = SetupReq(recovery, d)
        val pw: String? = try { d.await() } finally { _setup.value = null }
        if (pw == null) return false
        val k = withContext(Dispatchers.Default) { E2ee.createKeys(pw, recovery) }
        sp.edit().putString("dek", k.first).putString("keys", k.second.toString()).apply()
        return true
    }

    private suspend fun askUnlock(bad: Boolean): UnlockAns? {
        val d = CompletableDeferred<UnlockAns?>()
        _unlock.value = UnlockReq(bad, d)
        return try { d.await() } finally { _unlock.value = null }
    }

    /** A sealed copy needs the password (or the recovery key) once per phone. */
    private suspend fun openRow(f: Fetched): Opened {
        val d = f.raw
        if (!d.has("enc")) return Opened(0, CloudRow(d, d, false, f.updatedAt, f.device))
        var bad = false
        while (true) {
            if (sp.getString("dek", "").isNullOrEmpty()) {
                val ans = askUnlock(bad) ?: return Opened(2, null)
                if (ans.wipe) { deleteCloud(); return Opened(1, null) }
                try {
                    val dek = withContext(Dispatchers.Default) { E2ee.unlock(d, ans.mode, ans.secret) }
                    var keys = E2ee.keysOf(d)
                    val ed = sp.edit()
                    if (ans.mode == "rec") {
                        keys = withContext(Dispatchers.Default) { E2ee.rewrap(dek, keys, ans.newPass) }
                        ed.putBoolean("forceUpload", true)
                    }
                    ed.putString("dek", dek).putString("keys", keys.toString()).apply()
                    bad = false
                } catch (e: Exception) {
                    bad = true
                    continue
                }
            }
            try {
                val plain = withContext(Dispatchers.Default) { E2ee.decryptData(sp.getString("dek", "") ?: "", d) }
                return Opened(0, CloudRow(plain, d, true, f.updatedAt, f.device))
            } catch (e: Exception) {
                // Sealed with another key (the cloud was reset elsewhere): ask again.
                sp.edit().remove("dek").remove("keys").apply()
                bad = false
            }
        }
    }

    /** Saves a sealed copy. The database keeps the sealed version being replaced as a safety copy, and only replaces it if nobody changed it meanwhile. */
    private suspend fun upload(cloud: CloudRow?, snap: JSONObject): Boolean {
        val sealed = withContext(Dispatchers.Default) { E2ee.encryptData(sp.getString("dek", "") ?: "", JSONObject(sp.getString("keys", "{}") ?: "{}"), snap) }
        val body = JSONObject().put("p_data", sealed).put("p_device", "Phone (Android)").put("p_expected", cloud?.updatedAt ?: JSONObject.NULL).toString()
        val text = api("POST", "/rest/v1/rpc/save_backup", body).trim()
        if (text == "null" || text.isEmpty()) return false
        sp.edit().putString("lastHash", hash(snap)).putString("cloudAt", text.trim('"')).putBoolean("forceUpload", false).apply()
        return true
    }

    /** Puts cloud data on this phone. The old copy is kept once in a file (safety copy). */
    private suspend fun applyCloud(repo: Repo, cloud: CloudRow) {
        val local = fullJson(repo)
        runCatching { File(files, "pocketta-safety-copy.json").writeText(local.toString()) }
        val data = JSONObject(cloud.data.toString())
        val s = data.optJSONObject("settings") ?: JSONObject().also { data.put("settings", it) }
        val ls = local.optJSONObject("settings")
        if (ls != null) for (k in DEVICE_KEYS) if (ls.has(k)) s.put(k, ls.get(k))
        if (len(data, "wallets") == 0) {
            data.put("wallets", JSONArray().put(JSONObject().put("id", 1).put("name", "Money").put("type", "CASH").put("currency", s.optString("baseCurrency", "EGP")).put("opening", 0).put("sort", 0).put("archived", false)))
        }
        if (!repo.importPortable(data.toString())) error("bad data")
        Money.setCurrency(repo.prefs().baseCurrency)
        repo.changed()
        sp.edit().putString("lastHash", hash(strip(fullJson(repo)))).putString("cloudAt", cloud.updatedAt).putLong("localAt", System.currentTimeMillis()).apply()
    }

    private suspend fun ask(first: Boolean, local: SyncSide, cloud: SyncSide?): String? {
        val d = CompletableDeferred<String?>()
        _choice.value = SyncChoice(first, local, cloud, d)
        return try { d.await() } finally { _choice.value = null }
    }

    /** Runs a sync. Asks only when really needed: the encryption password, first upload, or two different copies. */
    suspend fun syncNow(repo: Repo) {
        if (!signedIn() || !mutex.tryLock()) return
        try {
            publish(status = "syncing", busy = true)
            var status = "ok"
            withContext(Dispatchers.IO) {
                whoAmI()
                for (tries in 0 until 4) {
                    var cloud: CloudRow? = null
                    val f = fetchCloud()
                    if (f != null) {
                        val o = openRow(f)
                        if (o.kind == 1) continue
                        if (o.kind == 2) { status = "paused"; break }
                        cloud = o.row
                    }
                    var snap = strip(fullJson(repo))
                    val lh = hash(snap)
                    val lastHash = sp.getString("lastHash", "") ?: ""
                    val cloudAt = sp.getString("cloudAt", "") ?: ""
                    val localAt = sp.getLong("localAt", 0L).takeIf { it > 0 } ?: System.currentTimeMillis()
                    var ok = true
                    var uploaded = false
                    var paused = false
                    // Every upload goes through here: it needs the data key first.
                    val up: suspend (CloudRow?) -> Boolean = { cl ->
                        if (!needKeys()) { paused = true; true }
                        else { val r = upload(cl, snap); if (r) uploaded = true; r }
                    }
                    if (cloud == null) {
                        if (!isEmpty(snap)) {
                            val pick = if (lastHash.isNotEmpty()) "local" else ask(true, side(snap, localAt), null)
                            if (pick == null) paused = true else ok = up(null)
                        }
                    } else if (cloudAt.isEmpty()) {
                        // This phone never synced with this account.
                        if (isEmpty(snap)) applyCloud(repo, cloud)
                        else if (hash(strip(JSONObject(cloud.data.toString()))) == lh) sp.edit().putString("lastHash", lh).putString("cloudAt", cloud.updatedAt).apply()
                        else {
                            val pick = ask(false, side(snap, localAt), side(cloud.data, parseTime(cloud.updatedAt), cloud.device))
                            if (pick == null) paused = true
                            else if (pick == "cloud") applyCloud(repo, cloud) else ok = up(cloud)
                        }
                    } else {
                        val localChanged = lh != lastHash
                        val cloudChanged = cloud.updatedAt != cloudAt
                        if (localChanged && cloudChanged) {
                            val pick = ask(false, side(snap, localAt), side(cloud.data, parseTime(cloud.updatedAt), cloud.device))
                            if (pick == null) paused = true
                            else if (pick == "cloud") applyCloud(repo, cloud) else ok = up(cloud)
                        } else if (localChanged) ok = up(cloud)
                        else if (cloudChanged) applyCloud(repo, cloud)
                    }
                    // An old copy in the cloud that is not sealed yet (or a new password after using the recovery key): seal and upload it now.
                    if (ok && !paused && !uploaded && cloud != null && (!cloud.wasEnc || sp.getBoolean("forceUpload", false))) {
                        // applyCloud may have changed this phone's data: upload what is here now.
                        snap = strip(fullJson(repo))
                        ok = up(cloud).also { if (!paused && it) uploaded = true }
                    }
                    if (paused) { status = "paused"; break }
                    if (ok) { sp.edit().putLong("lastSyncAt", System.currentTimeMillis()).apply(); break }
                }
            }
            publish(status = status)
        } catch (e: Exception) {
            publish(status = "error", error = if (e is IOException || e.message == "offline") "offline" else (e.message ?: "error"))
        } finally {
            mutex.unlock()
        }
    }

    private fun parseTime(s: String): Long = runCatching { java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli() }.getOrDefault(0L)

    suspend fun deleteCloud() = withContext(Dispatchers.IO) {
        whoAmI()
        api("DELETE", "/rest/v1/backups?user_id=eq." + (sp.getString("uid", "") ?: ""))
        sp.edit().remove("lastHash").remove("cloudAt").remove("lastSyncAt").remove("dek").remove("keys").remove("forceUpload").apply()
        publish(status = "")
    }

    /** Sync a few seconds after every change. Does nothing while signed out. */
    @OptIn(FlowPreview::class)
    fun start(repo: Repo, scope: CoroutineScope) {
        if (started) return
        started = true
        scope.launch {
            repo.version.drop(1).debounce(4000).collect {
                if (signedIn()) {
                    sp.edit().putLong("localAt", System.currentTimeMillis()).apply()
                    syncNow(repo)
                }
            }
        }
    }
}
