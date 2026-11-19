package app.pocketta.data

import android.util.Base64
import org.json.JSONObject
import java.security.SecureRandom
import java.text.Normalizer
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * End-to-end encryption for the cloud backup, the same envelope as web/js/crypto.js (the web app reads this and the other way round).
 * A random data key (DEK) encrypts the backup with AES-256-GCM. The DEK is locked twice: with a key made from the person's
 * encryption password and with a random recovery key (PBKDF2-HMAC-SHA256, 32 bytes). Nothing outside the device can open it
 * without the password or the recovery key. PBKDF2 is written out here (UTF-8 bytes of the password) so every phone and the web agree.
 */
object E2ee {
    const val PASS_ITER = 600_000
    const val REC_ITER = 20_000
    private val AAD = "pocketta-e2ee-1".toByteArray(Charsets.UTF_8)
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private val rng = SecureRandom()

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)
    private fun rand(n: Int) = ByteArray(n).also { rng.nextBytes(it) }
    private fun normPass(s: String) = Normalizer.normalize(s, Normalizer.Form.NFKC)
    private fun normRecovery(s: String) = s.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }

    private fun pbkdf2(password: ByteArray, salt: ByteArray, iter: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(password, "HmacSHA256"))
        mac.update(salt)
        mac.update(byteArrayOf(0, 0, 0, 1))
        var u = mac.doFinal()
        val t = u.copyOf()
        for (i in 1 until iter) {
            u = mac.doFinal(u)
            for (j in t.indices) t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
        }
        return t
    }

    private fun kek(secret: String, salt: ByteArray, iter: Int) = pbkdf2(secret.toByteArray(Charsets.UTF_8), salt, iter)

    private fun seal(key: ByteArray, plain: ByteArray): JSONObject {
        val iv = rand(12)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        c.updateAAD(AAD)
        return JSONObject().put("iv", b64(iv)).put("ct", b64(c.doFinal(plain)))
    }

    private fun open(key: ByteArray, o: JSONObject): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, unb64(o.getString("iv"))))
        c.updateAAD(AAD)
        return c.doFinal(unb64(o.getString("ct")))
    }

    fun newRecoveryKey(): String {
        val r = rand(20)
        return r.map { ALPHABET[(it.toInt() and 0xff) % 32] }.joinToString("").chunked(5).joinToString("-")
    }

    /** First setup: a new data key, locked with the password and with the recovery key. Returns (dek base64, keys). */
    fun createKeys(password: String, recovery: String): Pair<String, JSONObject> {
        val dek = rand(32)
        val salt = rand(16)
        val recSalt = rand(16)
        val keys = JSONObject()
            .put("iter", PASS_ITER).put("salt", b64(salt)).put("wrapPass", seal(kek(normPass(password), salt, PASS_ITER), dek))
            .put("recIter", REC_ITER).put("recSalt", b64(recSalt)).put("wrapRec", seal(kek(normRecovery(recovery), recSalt, REC_ITER), dek))
        return b64(dek) to keys
    }

    /** Opens the data key from an envelope with the password ("pass") or the recovery key ("rec"). Throws if it is wrong. */
    fun unlock(env: JSONObject, mode: String, secret: String): String {
        val bytes = if (mode == "rec") open(kek(normRecovery(secret), unb64(env.getString("recSalt")), env.getInt("recIter")), env.getJSONObject("wrapRec"))
        else open(kek(normPass(secret), unb64(env.getString("salt")), env.getInt("iter")), env.getJSONObject("wrapPass"))
        return b64(bytes)
    }

    /** After a recovery: lock the same data key with a new password. */
    fun rewrap(dek: String, keys: JSONObject, newPassword: String): JSONObject {
        val salt = rand(16)
        return JSONObject(keys.toString()).put("iter", PASS_ITER).put("salt", b64(salt)).put("wrapPass", seal(kek(normPass(newPassword), salt, PASS_ITER), unb64(dek)))
    }

    fun encryptData(dek: String, keys: JSONObject, obj: JSONObject): JSONObject {
        val sealed = seal(unb64(dek), obj.toString().toByteArray(Charsets.UTF_8))
        val env = JSONObject(keys.toString()).put("enc", 1)
        return env.put("iv", sealed.getString("iv")).put("ct", sealed.getString("ct"))
    }

    fun decryptData(dek: String, env: JSONObject): JSONObject =
        JSONObject(String(open(unb64(dek), JSONObject().put("iv", env.getString("iv")).put("ct", env.getString("ct"))), Charsets.UTF_8))

    fun keysOf(env: JSONObject): JSONObject = JSONObject()
        .put("iter", env.getInt("iter")).put("salt", env.getString("salt")).put("wrapPass", env.getJSONObject("wrapPass"))
        .put("recIter", env.getInt("recIter")).put("recSalt", env.getString("recSalt")).put("wrapRec", env.getJSONObject("wrapRec"))
}
