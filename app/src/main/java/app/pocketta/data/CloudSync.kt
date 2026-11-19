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

data class SyncUi(val signedIn: Boolean = false, val email: String = "", val lastSyncAt: Long = 0L, val status: String = "", val error: String = "", val busy: Boolean = false)

/**
 * Optional sync: sign in with Google (in the browser) and the whole backup is kept in the person's own row in the cloud
 * (Supabase), so the phone and the web app share the same data. Nothing runs until the person taps "Sign in with Google".
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

    fun init(context: Context) {
        if (::sp.isInitialized) return
        sp = context.applicationContext.getSharedPreferences("pocketta_sync", Context.MODE_PRIVATE)
        files = context.applicationContext.filesDir
        publish()
    }

    fun signedIn(): Boolean = ::sp.isInitialized && sp.contains("access")

    private fun publish(status: String = _ui.value.status, error: String = "", busy: Boolean = false) {
        _ui.value = SyncUi(signedIn(), sp.getString("email", "") ?: "", sp.getLong("lastSyncAt", 0L), status, error, busy)
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

    private class CloudRow(val data: JSONObject, val updatedAt: String, val device: String)

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

    private fun fetchCloud(): CloudRow? {
        val rows = JSONArray(api("GET", "/rest/v1/backups?select=data,updated_at,device_name&limit=1"))
        if (rows.length() == 0) return null
        val r = rows.getJSONObject(0)
        return CloudRow(r.getJSONObject("data"), r.getString("updated_at"), r.optString("device_name", ""))
    }

    /** Keeps the version being replaced as a safety copy, and only replaces it if nobody changed it meanwhile (done in the database). */
    private fun upload(cloud: CloudRow?, snap: JSONObject): Boolean {
        val body = JSONObject().put("p_data", snap).put("p_device", "Phone (Android)").put("p_expected", cloud?.updatedAt ?: JSONObject.NULL).toString()
        val text = api("POST", "/rest/v1/rpc/save_backup", body).trim()
        if (text == "null" || text.isEmpty()) return false
        sp.edit().putString("lastHash", hash(snap)).putString("cloudAt", text.trim('"')).apply()
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

    /** Runs a sync. Asks the person only when really needed: first upload, or two different copies. */
    suspend fun syncNow(repo: Repo) {
        if (!signedIn() || !mutex.tryLock()) return
        try {
            publish(status = "syncing", busy = true)
            var status = "ok"
            withContext(Dispatchers.IO) {
                whoAmI()
                for (tries in 0 until 3) {
                    val cloud = fetchCloud()
                    val snap = strip(fullJson(repo))
                    val lh = hash(snap)
                    val lastHash = sp.getString("lastHash", "") ?: ""
                    val cloudAt = sp.getString("cloudAt", "") ?: ""
                    val localAt = sp.getLong("localAt", 0L).takeIf { it > 0 } ?: System.currentTimeMillis()
                    var ok = true
                    if (cloud == null) {
                        if (!isEmpty(snap)) {
                            val pick = if (lastHash.isNotEmpty()) "local" else ask(true, side(snap, localAt), null)
                            if (pick == null) { status = "paused"; break }
                            ok = upload(null, snap)
                        }
                    } else if (cloudAt.isEmpty()) {
                        // This phone never synced with this account.
                        if (isEmpty(snap)) applyCloud(repo, cloud)
                        else if (hash(strip(JSONObject(cloud.data.toString()))) == lh) sp.edit().putString("lastHash", lh).putString("cloudAt", cloud.updatedAt).apply()
                        else {
                            val pick = ask(false, side(snap, localAt), side(cloud.data, parseTime(cloud.updatedAt), cloud.device))
                            if (pick == null) { status = "paused"; break }
                            if (pick == "cloud") applyCloud(repo, cloud) else ok = upload(cloud, snap)
                        }
                    } else {
                        val localChanged = lh != lastHash
                        val cloudChanged = cloud.updatedAt != cloudAt
                        if (localChanged && cloudChanged) {
                            val pick = ask(false, side(snap, localAt), side(cloud.data, parseTime(cloud.updatedAt), cloud.device))
                            if (pick == null) { status = "paused"; break }
                            if (pick == "cloud") applyCloud(repo, cloud) else ok = upload(cloud, snap)
                        } else if (localChanged) ok = upload(cloud, snap)
                        else if (cloudChanged) applyCloud(repo, cloud)
                    }
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
        sp.edit().remove("lastHash").remove("cloudAt").remove("lastSyncAt").apply()
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
