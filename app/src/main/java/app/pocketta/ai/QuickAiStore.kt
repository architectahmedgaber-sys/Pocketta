package app.pocketta.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import app.pocketta.core.QuickAi
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.time.YearMonth

/**
 * Quick AI entry: the user's Anthropic key and the usage log.
 * The key is encrypted with an AES-GCM key that lives in the Android Keystore (it never leaves the secure hardware/OS store),
 * and only the encrypted text is saved. The file is excluded from Android backups and device transfer, and it is not part of the
 * database, so exports, web backups and cloud sync never contain it.
 */
class QuickAiStore(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun hasKey(): Boolean = key() != null

    /** The decrypted key, or null when none is saved (or it can no longer be decrypted, for example after a restore on a new phone). */
    fun key(): String? {
        val stored = sp.getString("key_enc", null) ?: return null
        return try {
            val (iv, ct) = stored.split(":").let { Base64.decode(it[0], Base64.NO_WRAP) to Base64.decode(it[1], Base64.NO_WRAP) }
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            c.updateAAD(AAD)
            String(c.doFinal(ct), Charsets.UTF_8).takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            sp.edit().remove("key_enc").apply()
            null
        }
    }

    fun setKey(k: String) {
        val clean = k.trim()
        if (clean.isEmpty()) { removeKey(); return }
        sp.edit().remove("key_enc").commit()
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, secretKey())
        c.updateAAD(AAD)
        val ct = c.doFinal(clean.toByteArray(Charsets.UTF_8))
        sp.edit().putString("key_enc", Base64.encodeToString(c.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(ct, Base64.NO_WRAP)).apply()
    }

    /** Delete: the encrypted text and the Keystore key are both wiped at once (commit, not apply, so it is on disk before this returns). */
    fun removeKey() {
        sp.edit().remove("key_enc").commit()
        try { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(ALIAS) } catch (e: Exception) { /* nothing to delete */ }
    }

    /** What the screen shows for a saved key: only the last 4 characters. */
    fun masked(): String? = key()?.let { QuickAi.maskKey(it) }

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    // ---------- usage log ----------

    fun entries(): List<QuickAi.UsageEntry> = try {
        val a = JSONArray(sp.getString("usage", "[]"))
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            QuickAi.UsageEntry(o.getString("m"), o.getLong("i"), o.getLong("o"), o.optBoolean("ok", true))
        }
    } catch (e: Exception) { emptyList() }

    /** Adds one request's tokens from the answer's "usage" field. Keeps the last [MAX_ENTRIES] requests. */
    @Synchronized
    fun addUsage(u: QuickAi.Usage, ok: Boolean, month: String = YearMonth.now().toString()) {
        val all = (entries() + QuickAi.UsageEntry(month, u.inputTokens, u.outputTokens, ok)).takeLast(MAX_ENTRIES)
        val a = JSONArray()
        all.forEach { a.put(JSONObject().put("m", it.month).put("i", it.inputTokens).put("o", it.outputTokens).put("ok", it.ok)) }
        sp.edit().putString("usage", a.toString()).apply()
    }

    fun monthCostUsd(month: String = YearMonth.now().toString()): Double = QuickAi.monthCostUsd(entries(), month)
    fun monthRequests(month: String = YearMonth.now().toString()): Int = entries().count { it.month == month }

    /** Short problems (a wrong model, bad JSON) kept for the user to see. */
    fun addLog(line: String) {
        val old = sp.getString("log", "").orEmpty().split('\n').filter { it.isNotBlank() }
        sp.edit().putString("log", (old + "${java.time.LocalDateTime.now().withNano(0)} $line").takeLast(30).joinToString("\n")).apply()
        android.util.Log.w("QuickAi", line)
    }
    fun log(): List<String> = sp.getString("log", "").orEmpty().split('\n').filter { it.isNotBlank() }

    companion object {
        const val FILE = "pocketta_quickai"
        private const val ALIAS = "pocketta_quickai_key"
        private val AAD = "pocketta.quickai.key.v1".toByteArray(Charsets.UTF_8)
        private const val MAX_ENTRIES = 600
    }
}
