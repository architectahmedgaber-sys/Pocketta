package app.pocketta.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AiReply(val text: String, val calls: List<ToolCall>)

/** Talks to Gemini, Claude or ChatGPT with the user's own key and turns each answer into text plus tool calls. */
object AiClient {
    private suspend fun post(url: String, headers: Map<String, String>, body: String): String = withContext(Dispatchers.IO) {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 20000
            c.readTimeout = 90000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            for ((k, v) in headers) c.setRequestProperty(k, v)
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw AiError(code, text.take(500))
            text
        } finally {
            c.disconnect()
        }
    }

    suspend fun chat(cfg: AiConfig, system: String, history: List<AiMsg>, tools: List<AiTool>): AiReply {
        val p = cfg.provider
        val key = cfg.key(p)
        val model = cfg.model(p)
        suspend fun call(m: String): AiReply = when (p) {
            AiProvider.GEMINI -> gemini(key, m, system, history, tools)
            AiProvider.CLAUDE -> claude(key, m, system, history, tools)
            AiProvider.OPENAI -> openai(key, m, system, history, tools)
        }
        return try {
            call(model)
        } catch (e: AiError) {
            // The saved model name may not exist for this key (names change). Find one that does and try once more.
            if (e.code != 404) throw e
            val found = pickModel(p, key)
            if (found == null || found == model) throw e
            cfg.setModel(p, found)
            call(found)
        }
    }

    /** Asks the company which models this key can use and picks a fast one. Returns null when none is found. Throws [AiError] when the key is refused. */
    suspend fun pickModel(p: AiProvider, key: String): String? {
        if (p == AiProvider.GEMINI) return geminiPick(key)
        return withContext(Dispatchers.IO) {
            val claude = p == AiProvider.CLAUDE
            val c = URL(if (claude) "https://api.anthropic.com/v1/models?limit=100" else "https://api.openai.com/v1/models").openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 20000
                c.readTimeout = 30000
                if (claude) { c.setRequestProperty("x-api-key", key); c.setRequestProperty("anthropic-version", "2023-06-01") }
                else c.setRequestProperty("Authorization", "Bearer $key")
                val code = c.responseCode
                val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code !in 200..299) throw AiError(code, text.take(500))
                val arr = JSONObject(text).optJSONArray("data") ?: JSONArray()
                val bad = Regex("audio|realtime|transcribe|tts|image|search|instruct|codex|vision|embedding|moderation|chat-latest")
                val names = ArrayList<String>()
                for (i in 0 until arr.length()) {
                    val id = arr.getJSONObject(i).optString("id")
                    if (claude && id.startsWith("claude-")) names += id
                    if (!claude && id.startsWith("gpt-") && !bad.containsMatchIn(id)) names += id
                }
                val fast = Regex(if (claude) "haiku" else "mini")
                fun rank(n: String) = (if (fast.containsMatchIn(n)) 0 else 5) + (if (Regex("preview|-\\d{3,}$|\\d{4}-\\d{2}-\\d{2}").containsMatchIn(n)) 1 else 0)
                fun ver(n: String) = Regex("(\\d+(?:\\.\\d+)?)").find(n)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                names.sortedWith(compareBy<String> { rank(it) }.thenByDescending { ver(it) }).firstOrNull()
            } finally {
                c.disconnect()
            }
        }
    }

    private fun parseArgs(s: String?): JSONObject = try { JSONObject(if (s.isNullOrBlank()) "{}" else s) } catch (e: Exception) { JSONObject() }

    // ---------- Gemini ----------

    /** Asks Gemini which models this key can use and picks a fast one. Returns null when none is found. Throws [AiError] when the key is refused. */
    suspend fun geminiPick(key: String): String? = withContext(Dispatchers.IO) {
        val c = URL("https://generativelanguage.googleapis.com/v1beta/models?pageSize=200").openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 20000
            c.readTimeout = 30000
            c.setRequestProperty("x-goog-api-key", key)
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw AiError(code, text.take(500))
            val arr = JSONObject(text).optJSONArray("models") ?: JSONArray()
            val bad = Regex("lite|image|tts|live|audio|embedding|thinking|exp|vision|robotics|computer")
            val names = ArrayList<String>()
            for (i in 0 until arr.length()) {
                val m = arr.getJSONObject(i)
                val methods = m.optJSONArray("supportedGenerationMethods") ?: continue
                var ok = false
                for (j in 0 until methods.length()) if (methods.getString(j) == "generateContent") ok = true
                val n = m.optString("name").removePrefix("models/")
                if (ok && n.startsWith("gemini-") && !bad.containsMatchIn(n)) names += n
            }
            fun rank(n: String) = (if (n.contains("flash")) 0 else 5) + (if (Regex("preview|-\\d{3}$").containsMatchIn(n)) 1 else 0)
            fun ver(n: String) = Regex("gemini-(\\d+(?:\\.\\d+)?)").find(n)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
            names.sortedWith(compareBy<String> { rank(it) }.thenByDescending { ver(it) }).firstOrNull()
        } finally {
            c.disconnect()
        }
    }

    private suspend fun gemini(key: String, model: String, system: String, history: List<AiMsg>, tools: List<AiTool>): AiReply {
        val contents = JSONArray()
        for (m in history) when (m) {
            is AiMsg.User -> contents.put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", m.text))))
            is AiMsg.Assistant -> {
                val parts = JSONArray()
                if (m.text.isNotBlank()) parts.put(JSONObject().put("text", m.text))
                for (c in m.calls) parts.put(JSONObject().put("functionCall", JSONObject().put("name", c.name).put("args", c.args)))
                if (parts.length() > 0) contents.put(JSONObject().put("role", "model").put("parts", parts))
            }
            is AiMsg.Results -> {
                val parts = JSONArray()
                for (r in m.results) parts.put(
                    JSONObject().put("functionResponse", JSONObject().put("name", r.name).put("response", JSONObject().put("result", r.content)))
                )
                contents.put(JSONObject().put("role", "user").put("parts", parts))
            }
        }
        val decl = JSONArray()
        for (t in tools) decl.put(JSONObject().put("name", t.name).put("description", t.desc).apply { if (t.params.isNotEmpty()) put("parameters", t.schema()) })
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", contents)
            .put("tools", JSONArray().put(JSONObject().put("functionDeclarations", decl)))
        val res = JSONObject(post("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent", mapOf("x-goog-api-key" to key), body.toString()))
        val parts = res.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
        val text = StringBuilder()
        val calls = ArrayList<ToolCall>()
        for (i in 0 until parts.length()) {
            val part = parts.getJSONObject(i)
            part.optString("text").takeIf { it.isNotEmpty() }?.let { text.append(it) }
            part.optJSONObject("functionCall")?.let { calls += ToolCall("g" + System.nanoTime() + i, it.optString("name"), it.optJSONObject("args") ?: JSONObject()) }
        }
        return AiReply(text.toString().trim(), calls)
    }

    // ---------- Claude ----------

    private suspend fun claude(key: String, model: String, system: String, history: List<AiMsg>, tools: List<AiTool>): AiReply {
        val msgs = JSONArray()
        for (m in history) when (m) {
            is AiMsg.User -> msgs.put(JSONObject().put("role", "user").put("content", m.text))
            is AiMsg.Assistant -> {
                val blocks = JSONArray()
                if (m.text.isNotBlank()) blocks.put(JSONObject().put("type", "text").put("text", m.text))
                for (c in m.calls) blocks.put(JSONObject().put("type", "tool_use").put("id", c.id).put("name", c.name).put("input", c.args))
                if (blocks.length() > 0) msgs.put(JSONObject().put("role", "assistant").put("content", blocks))
            }
            is AiMsg.Results -> {
                val blocks = JSONArray()
                for (r in m.results) blocks.put(JSONObject().put("type", "tool_result").put("tool_use_id", r.callId).put("content", r.content))
                msgs.put(JSONObject().put("role", "user").put("content", blocks))
            }
        }
        val defs = JSONArray()
        for (t in tools) defs.put(JSONObject().put("name", t.name).put("description", t.desc).put("input_schema", t.schema()))
        val body = JSONObject().put("model", model).put("max_tokens", 1500).put("system", system).put("messages", msgs).put("tools", defs)
        val res = JSONObject(post("https://api.anthropic.com/v1/messages", mapOf("x-api-key" to key, "anthropic-version" to "2023-06-01"), body.toString()))
        val content = res.optJSONArray("content") ?: JSONArray()
        val text = StringBuilder()
        val calls = ArrayList<ToolCall>()
        for (i in 0 until content.length()) {
            val b = content.getJSONObject(i)
            when (b.optString("type")) {
                "text" -> text.append(b.optString("text"))
                "tool_use" -> calls += ToolCall(b.optString("id"), b.optString("name"), b.optJSONObject("input") ?: JSONObject())
            }
        }
        return AiReply(text.toString().trim(), calls)
    }

    // ---------- ChatGPT ----------

    private suspend fun openai(key: String, model: String, system: String, history: List<AiMsg>, tools: List<AiTool>): AiReply {
        val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", system))
        for (m in history) when (m) {
            is AiMsg.User -> msgs.put(JSONObject().put("role", "user").put("content", m.text))
            is AiMsg.Assistant -> {
                val o = JSONObject().put("role", "assistant").put("content", if (m.text.isBlank()) JSONObject.NULL else m.text)
                if (m.calls.isNotEmpty()) {
                    val arr = JSONArray()
                    for (c in m.calls) arr.put(
                        JSONObject().put("id", c.id).put("type", "function")
                            .put("function", JSONObject().put("name", c.name).put("arguments", c.args.toString()))
                    )
                    o.put("tool_calls", arr)
                }
                msgs.put(o)
            }
            is AiMsg.Results -> for (r in m.results) msgs.put(JSONObject().put("role", "tool").put("tool_call_id", r.callId).put("content", r.content))
        }
        val defs = JSONArray()
        for (t in tools) defs.put(
            JSONObject().put("type", "function")
                .put("function", JSONObject().put("name", t.name).put("description", t.desc).put("parameters", t.schema()))
        )
        val body = JSONObject().put("model", model).put("messages", msgs).put("tools", defs)
        val res = JSONObject(post("https://api.openai.com/v1/chat/completions", mapOf("Authorization" to "Bearer $key"), body.toString()))
        val msg = res.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message") ?: JSONObject()
        val calls = ArrayList<ToolCall>()
        val arr = msg.optJSONArray("tool_calls") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val c = arr.getJSONObject(i)
            val f = c.optJSONObject("function") ?: continue
            calls += ToolCall(c.optString("id"), f.optString("name"), parseArgs(f.optString("arguments")))
        }
        val text = if (msg.isNull("content")) "" else msg.optString("content")
        return AiReply(text.trim(), calls)
    }
}
