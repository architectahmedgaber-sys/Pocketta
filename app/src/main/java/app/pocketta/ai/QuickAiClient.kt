package app.pocketta.ai

import app.pocketta.core.QuickAi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class QuickAiHttpError(val code: Int) : Exception("Quick AI request failed ($code)") {
    fun friendly(): String = when (code) {
        401, 403 -> "The key was refused. Check that you pasted it fully and that it is still active."
        429 -> "Too many requests, or the balance on your Anthropic account is used up. Try again in a minute."
        in 500..599 -> "The service is busy. Try again in a moment."
        else -> "Could not reach the assistant (code $code)."
    }
}

/** Sends the request to Anthropic. The model is fixed inside [QuickAi.requestBody]; nothing here can change it and nothing retries with another model. */
class QuickAiTransport(private val key: String) : QuickAi.Transport {
    override suspend fun send(requestBody: String): String = withContext(Dispatchers.IO) {
        val c = URL(QuickAi.API_URL).openConnection() as HttpURLConnection
        try {
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
