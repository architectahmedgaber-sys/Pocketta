package app.pocketta.ai

import app.pocketta.core.QuickAi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class QuickAiHttpError(val code: Int) : Exception("Quick AI request failed ($code)") {
    /** A short, clear message in Egyptian Arabic. It never contains the key or the answer body. */
    fun friendly(): String = when (code) {
        401, 403 -> "المفتاح مش مقبول. اتأكد إنك نسخته كله وإنه لسه شغّال."
        400 -> "الطلب اتّرفض. غالباً الرصيد في حسابك على Anthropic خلص أو المفتاح لسه مش مفعّل."
        429 -> "طلبات كتير أو وصلت للحد الشهري اللي حطّيته. جرّب بعد شوية."
        in 500..599 -> "خدمة Anthropic مشغولة دلوقتي. جرّب تاني بعد شوية."
        else -> "مقدرتش أوصل للمساعد (كود $code)."
    }
}

const val QUICK_AI_OFFLINE = "مفيش إنترنت أو الاتصال ضعيف. جرّب تاني."

/** Sends the request to Anthropic. The model is fixed inside [QuickAi.requestBody]; nothing here can change it and nothing retries with another model. */
class QuickAiTransport(private val key: String) : QuickAi.Transport {
    override suspend fun send(requestBody: String): String = withContext(Dispatchers.IO) {
        // The key goes only to api.anthropic.com, and a redirect is never followed (it would carry the key elsewhere).
        check(QuickAi.isAnthropicUrl(QuickAi.API_URL))
        val c = URL(QuickAi.API_URL).openConnection() as HttpURLConnection
        try {
            c.instanceFollowRedirects = false
            c.requestMethod = "POST"
            c.connectTimeout = 20000
            c.readTimeout = 60000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("x-api-key", key)
            c.setRequestProperty("anthropic-version", QuickAi.API_VERSION)
            c.outputStream.use { it.write(requestBody.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            if (code !in 200..299) throw QuickAiHttpError(code)
            c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}
