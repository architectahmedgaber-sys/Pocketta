package app.pocketta.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import app.pocketta.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** The only things Pocketta reads from the internet: daily rates and new versions. Nothing is ever sent. */
object Online {
    const val RATES_URL = "https://raw.githubusercontent.com/${BuildConfig.GITHUB_REPO}/rates/rates.json"
    private const val RELEASES_API = "https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest"
    const val RELEASES_PAGE = "https://github.com/${BuildConfig.GITHUB_REPO}/releases/latest"

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 20_000
        c.setRequestProperty("Accept", "application/json")
        c.setRequestProperty("User-Agent", "Pocketta")
        try {
            if (c.responseCode != 200) error("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    /** Downloads today's rates and gold price. Returns false when offline or the file isn't there yet. */
    suspend fun refreshRates(repo: Repo, force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val p = repo.prefs()
        if (!p.autoRates) return@withContext false
        if (!force && System.currentTimeMillis() - p.ratesFetchedAt < 6 * 3_600_000L) return@withContext true
        runCatching {
            val j = JSONObject(get(RATES_URL))
            val rates = j.getJSONObject("rates")
            val m = HashMap<String, Double>()
            for (k in rates.keys()) {
                val v = rates.optDouble(k)
                if (!v.isNaN() && v > 0) m[k.uppercase()] = v
            }
            val gold = HashMap<String, Double>()
            j.optJSONObject("gold")?.let { g -> for (k in g.keys()) g.optDouble(k).takeIf { !it.isNaN() && it > 0 }?.let { gold[k] = it } }
            if (m.isEmpty()) error("empty")
            // Yesterday's prices, for the up/down arrows: the file carries them, else keep what we had before the day changed.
            val prev = j.optJSONObject("previous")
            if (prev != null) {
                repo.dao.put(SettingRow("prevOnlineRates", Prefs.formatRates(readMap(prev.optJSONObject("rates")))))
                repo.dao.put(SettingRow("prevGold", Prefs.formatRates(readMap(prev.optJSONObject("gold")))))
            } else if (p.onlineRates.isNotEmpty() && p.ratesUpdated.take(10) != j.optString("updated").take(10)) {
                repo.dao.put(SettingRow("prevOnlineRates", Prefs.formatRates(p.onlineRates)))
                repo.dao.put(SettingRow("prevGold", Prefs.formatRates(p.gold)))
            }
            repo.dao.put(SettingRow("onlineRates", Prefs.formatRates(m)))
            repo.dao.put(SettingRow("gold", Prefs.formatRates(gold)))
            repo.dao.put(SettingRow("ratesUpdated", j.optString("updated")))
            repo.dao.put(SettingRow("ratesFetchedAt", System.currentTimeMillis().toString()))
            repo.changed()
        }.isSuccess
    }

    private fun readMap(o: JSONObject?): Map<String, Double> {
        val out = HashMap<String, Double>()
        if (o != null) for (k in o.keys()) o.optDouble(k).takeIf { !it.isNaN() && it > 0 }?.let { out[k.uppercase()] = it }
        return out
    }

    data class Release(val version: String, val apkUrl: String)

    /** Checks GitHub for a newer version. Remembers the answer so the app can show a banner. */
    suspend fun checkUpdate(repo: Repo, force: Boolean = false): Release? = withContext(Dispatchers.IO) {
        val p = repo.prefs()
        if (!force && System.currentTimeMillis() - p.updateCheckedAt < 10 * 60_000L) {
            return@withContext if (p.updateVersion.isNotEmpty() && isNewer(p.updateVersion, BuildConfig.VERSION_NAME)) Release(p.updateVersion, p.updateUrl) else null
        }
        val r = runCatching {
            val j = JSONObject(get(RELEASES_API))
            val v = j.getString("tag_name").removePrefix("v")
            val assets = j.getJSONArray("assets")
            var url = ""
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk")) url = a.getString("browser_download_url")
            }
            Release(v, url)
        }.getOrNull()
        repo.dao.put(SettingRow("updateCheckedAt", System.currentTimeMillis().toString()))
        if (r != null) {
            repo.dao.put(SettingRow("updateVersion", r.version))
            repo.dao.put(SettingRow("updateUrl", r.apkUrl))
        }
        repo.changed()
        if (r != null && r.apkUrl.isNotEmpty() && isNewer(r.version, BuildConfig.VERSION_NAME)) r else null
    }

    fun isNewer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val a = parts(remote)
        val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun openFollowing(url: String, hops: Int): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.instanceFollowRedirects = false
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.setRequestProperty("User-Agent", "Pocketta")
        val code = c.responseCode
        if (code in 300..399 && hops > 0) {
            val next = c.getHeaderField("Location") ?: error("redirect")
            c.disconnect()
            return openFollowing(next, hops - 1)
        }
        if (code != 200) error("HTTP $code")
        return c
    }

    /** True when Android lets Pocketta open the installer; otherwise sends the user to the switch for it. */
    fun canInstall(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()) return true
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return false
    }

    /** Downloads the APK and opens Android's installer. [progress] gets 0..1. */
    suspend fun downloadAndInstall(context: Context, r: Release, progress: (Float) -> Unit): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val out = File(dir, "pocketta-${r.version}.apk")
            val c = openFollowing(r.apkUrl, 5)
            val total = c.contentLengthLong
            c.inputStream.use { input ->
                out.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n)
                        done += n
                        if (total > 0) progress(done.toFloat() / total)
                    }
                }
            }
            c.disconnect()
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", out)
            val i = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            withContext(Dispatchers.Main) { context.startActivity(i) }
        }.isSuccess
    }
}
