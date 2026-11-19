package app.pocketta.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** The three companies the assistant can talk to. The user brings their own key. */
enum class AiProvider(val label: String, val defaultModel: String, val keyHelp: String) {
    GEMINI("Gemini (Google)", "gemini-2.5-flash", "aistudio.google.com/apikey"),
    CLAUDE("Claude (Anthropic)", "claude-haiku-5-5", "console.anthropic.com"),
    OPENAI("ChatGPT (OpenAI)", "gpt-4o-mini", "platform.openai.com/api-keys");

    companion object {
        fun parse(s: String?): AiProvider = entries.firstOrNull { it.name == s } ?: GEMINI
    }
}

/** The key and choices live in a private file on this phone only. They are not part of the database, so backups and exports never contain the key. */
class AiConfig(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("pocketta_ai", Context.MODE_PRIVATE)

    var provider: AiProvider
        get() = AiProvider.parse(sp.getString("provider", null))
        set(v) { sp.edit().putString("provider", v.name).apply() }

    fun key(p: AiProvider = provider): String = sp.getString("key_" + p.name, "").orEmpty()
    fun setKey(p: AiProvider, k: String) { sp.edit().putString("key_" + p.name, k.trim()).apply() }

    fun model(p: AiProvider = provider): String = sp.getString("model_" + p.name, "").orEmpty().ifBlank { p.defaultModel }
    fun setModel(p: AiProvider, m: String) { sp.edit().putString("model_" + p.name, m.trim()).apply() }

    /** Things the assistant learned about you ("koshary = Food"), sent with every chat so it gets to know you. */
    fun notes(): List<String> = sp.getString("notes", "").orEmpty().split('\n').map { it.trim() }.filter { it.isNotEmpty() }
    fun addNote(n: String) {
        val clean = n.replace('\n', ' ').trim().take(200)
        if (clean.isEmpty()) return
        val all = (notes() + clean).distinct().takeLast(40)
        sp.edit().putString("notes", all.joinToString("\n")).apply()
    }
    fun clearNotes() { sp.edit().remove("notes").apply() }
    fun removeKey(p: AiProvider) { sp.edit().remove("key_" + p.name).apply() }
}

data class ToolCall(val id: String, val name: String, val args: JSONObject)
data class ToolResult(val callId: String, val name: String, val content: String)

sealed class AiMsg {
    data class User(val text: String) : AiMsg()
    data class Assistant(val text: String, val calls: List<ToolCall>) : AiMsg()
    data class Results(val results: List<ToolResult>) : AiMsg()
}

class AiError(val code: Int, val body: String) : Exception("AI request failed ($code)") {
    /** A short explanation a person can act on. */
    fun friendly(): String = when (code) {
        400 -> "The company did not accept the request. Check the model name in the assistant settings."
        401, 403 -> "The key was refused. Check that you pasted it fully and that it is still active."
        404 -> "That model name was not found. Check it in the assistant settings."
        429 -> "Too many requests or the free limit is used up. Try again in a minute."
        in 500..599 -> "The company's service is busy. Try again in a moment."
        else -> "Could not reach the assistant (code $code)."
    }
}

class AiParam(val name: String, val type: String, val desc: String, val required: Boolean = false, val choices: List<String> = emptyList())

/** One thing the assistant may do. Tools that change data ([write]) always ask the user first. */
class AiTool(val name: String, val desc: String, val params: List<AiParam>, val write: Boolean) {
    fun schema(): JSONObject {
        val props = JSONObject()
        for (p in params) {
            val o = JSONObject().put("type", p.type).put("description", p.desc)
            if (p.choices.isNotEmpty()) o.put("enum", JSONArray(p.choices))
            props.put(p.name, o)
        }
        val s = JSONObject().put("type", "object").put("properties", props)
        val req = params.filter { it.required }.map { it.name }
        if (req.isNotEmpty()) s.put("required", JSONArray(req))
        return s
    }
}
