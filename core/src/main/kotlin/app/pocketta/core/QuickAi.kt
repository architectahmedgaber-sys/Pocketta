package app.pocketta.core

import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Quick AI entry: the user types one Egyptian-Arabic sentence, a small Claude model proposes transactions, the user confirms.
 * Everything here is pure (no network, no database) so the rules can be tested:
 *  - one pinned model, checked in every answer;
 *  - strict JSON shape, anything else becomes "needs review";
 *  - usage and cost counting;
 *  - nothing reaches the [Sink] until [QuickAiSession.Pending.confirm] is called.
 */
object QuickAi {
    /** The ONLY model this feature may use. No setting, fallback or alias changes it. Never a Sonnet or Opus model. */
    const val MODEL = "claude-haiku-5-5"
    const val API_URL = "https://api.anthropic.com/v1/messages"
    const val API_VERSION = "2023-06-01"
    const val MAX_TOKENS = 1024

    /** Price per million tokens in US dollars. Source: https://www.anthropic.com/claude-haiku-5-5 . Edit here when the price changes. */
    const val INPUT_USD_PER_MILLION = 0.10
    const val OUTPUT_USD_PER_MILLION = 0.50
    const val PRICE_SOURCE = "https://www.anthropic.com/claude-haiku-5-5"

    const val MAX_TRANSACTIONS = 20

    val TYPES = listOf("expense", "income", "loan_received", "loan_given", "loan_repaid_given", "loan_repaid_received")

    /** The parser prompt, exactly as written by the owner (prompt.txt). */
    val SYSTEM_PROMPT: String = """
You are the transaction parser inside Pocketta, a personal money app for Egyptian users.

The user writes short messages in Egyptian colloquial Arabic describing money events: expenses, income, loans borrowed, loans lent, and loan repayments.

Convert each message into JSON with this exact shape:
{
  "transactions": [
    {
      "type": "expense" | "income" | "loan_received" | "loan_given" | "loan_repaid_given" | "loan_repaid_received",
      "amount_egp": <number>,
      "category": "<one short Arabic word>",
      "person": "<name as written, or null>",
      "due_date": "<YYYY-MM-DD or null>",
      "date": "<YYYY-MM-DD>",
      "note": "<very short Arabic note>"
    }
  ]
}

Rules:
- Output JSON only. No explanation, no markdown fences, no text before or after the JSON.
- Types:
  - expense = money the user spent. income = money the user received as earnings.
  - "استلفت من X" / "اتسلفت من X" = loan_received (user borrowed, money in).
  - "سلفت X" / "اديت X" / "اديت لـ X" = loan_given (user lent, money out).
  - "رجعت لـ X" / "سددت لـ X" = loan_repaid_given (user paid back a debt).
  - "X رجعلي" / "X سددلي" = loan_repaid_received (someone paid the user back).
- amount_egp is a plain number in Egyptian pounds: "1000ج" = 1000, "جنيه ونص" = 1.5, Arabic-Indic digits (٧٥) = 75. Strip any "ج", "جنيه", "جنية" suffix.
- date: the user message starts with "today=YYYY-MM-DD". "النهاردة" = today, "امبارح" = today minus 1 day, "بكرة" = today plus 1 day. Default is today.
- due_date only for loans (loan_received, loan_given). Resolve relative dates against today: "أول الشهر الجاي" = the 1st of next month, "١٥ الشهر الجاي" = the 15th of next month, "بكرة" = tomorrow. If no repayment date is mentioned, null.
- category: one short Arabic word such as أكل, بقالة, مواصلات, بنزين, فواتير, صحة, ترفيه, تسوق, هدايا, مرتب, شغل, أخرى.
- If the user corrects themselves ("لأ مش ٥٠ دي ١٥٠"), use only the corrected amount.
- If a future plan is described without money already spent, or the amount is missing, or the message is not about money at all, return:
  {"transactions": [], "needs_review": true, "reason": "<short Arabic explanation>"}
- One message can contain several transactions. Keep them in the order mentioned.
""".trim()

    // ---------- Request ----------

    /** The first line of every user message is today's date, as the prompt expects. */
    fun userMessage(today: LocalDate, text: String): String = "today=$today\n${text.trim()}"

    fun requestBody(today: LocalDate, text: String): String =
        "{\"model\":" + Json.quote(MODEL) +
            ",\"max_tokens\":" + MAX_TOKENS +
            ",\"system\":" + Json.quote(SYSTEM_PROMPT) +
            ",\"messages\":[{\"role\":\"user\",\"content\":" + Json.quote(userMessage(today, text)) + "}]}"

    // ---------- Cost ----------

    data class Usage(val inputTokens: Long, val outputTokens: Long)

    fun costUsd(
        inputTokens: Long, outputTokens: Long,
        inputPerMillion: Double = INPUT_USD_PER_MILLION, outputPerMillion: Double = OUTPUT_USD_PER_MILLION,
    ): Double = inputTokens * inputPerMillion / 1_000_000.0 + outputTokens * outputPerMillion / 1_000_000.0

    /** One logged request. [month] is "YYYY-MM". [ok] is false when the answer was thrown away. */
    data class UsageEntry(val month: String, val inputTokens: Long, val outputTokens: Long, val ok: Boolean)

    fun monthCostUsd(entries: List<UsageEntry>, month: String): Double =
        entries.filter { it.month == month }.sumOf { costUsd(it.inputTokens, it.outputTokens) }

    // ---------- Answer ----------

    data class Proposal(
        val type: String, val amountEgp: Double, val category: String, val person: String?,
        val dueDate: LocalDate?, val date: LocalDate, val note: String,
    )

    sealed class Result {
        data class Proposals(val items: List<Proposal>) : Result()
        /** The model could not or would not make transactions, or its answer was not valid. Nothing is proposed. */
        data class NeedsReview(val reason: String, val malformed: Boolean) : Result()
        /** The answer came from a different model than [MODEL]. It was discarded. */
        data class WrongModel(val got: String?) : Result()
    }

    class Reply(val usage: Usage?, val result: Result)

    /** Reads one raw answer of the Messages API. Never throws. */
    fun readReply(responseBody: String): Reply {
        val root = try { Json.parse(responseBody) } catch (e: Json.JsonError) { null }
        if (root !is Map<*, *>) return Reply(null, Result.NeedsReview("الرد مش مفهوم", true))
        val usage = readUsage(root["usage"])
        // The pinned model is checked first. Any other model: the whole answer is discarded.
        val got = root["model"] as? String
        if (got != MODEL) return Reply(usage, Result.WrongModel(got))
        val text = (root["content"] as? List<*>).orEmpty()
            .mapNotNull { it as? Map<*, *> }.filter { it["type"] == "text" }.joinToString("") { it["text"] as? String ?: "" }
        return Reply(usage, parseTransactions(text))
    }

    private fun readUsage(u: Any?): Usage? {
        if (u !is Map<*, *>) return null
        val i = (u["input_tokens"] as? Double) ?: return null
        val o = (u["output_tokens"] as? Double) ?: return null
        if (i < 0 || o < 0 || i.isNaN() || o.isNaN()) return null
        return Usage(i.toLong(), o.toLong())
    }

    private fun malformed(why: String) = Result.NeedsReview("مقدرتش أفهم الرد ($why)، راجع الرسالة", true)

    /** The model's text must be exactly the JSON of the prompt's shape. Anything else (fences, extra text, bad fields) is rejected. */
    fun parseTransactions(text: String): Result {
        val root = try { Json.parse(text) } catch (e: Json.JsonError) { return malformed("not JSON") }
        if (root !is Map<*, *>) return malformed("not an object")
        val list = root["transactions"] as? List<*> ?: return malformed("no transactions")
        if (list.isEmpty()) {
            val reason = root["reason"] as? String
            return if (root["needs_review"] == true && reason != null) Result.NeedsReview(reason, false) else malformed("empty")
        }
        if (root["needs_review"] == true) return malformed("both")
        if (list.size > MAX_TRANSACTIONS) return malformed("too many")
        val out = ArrayList<Proposal>()
        for (raw in list) {
            val o = raw as? Map<*, *> ?: return malformed("item")
            val type = o["type"] as? String
            if (type == null || type !in TYPES) return malformed("type")
            val amount = o["amount_egp"] as? Double
            if (amount == null || amount.isNaN() || amount.isInfinite() || amount <= 0 || amount > 1e9) return malformed("amount")
            val category = o["category"] as? String ?: return malformed("category")
            val note = o["note"] as? String ?: return malformed("note")
            val person = if (!o.containsKey("person") || o["person"] == null) null else (o["person"] as? String ?: return malformed("person"))
            val date = isoDate(o["date"]) ?: return malformed("date")
            val dueRaw = o["due_date"]
            val due = if (dueRaw == null) null else (isoDate(dueRaw) ?: return malformed("due_date"))
            val isLoan = type == "loan_received" || type == "loan_given"
            out += Proposal(type, amount, category.trim(), person?.trim()?.ifEmpty { null }, if (isLoan) due else null, date, note.trim())
        }
        return Result.Proposals(out)
    }

    private fun isoDate(v: Any?): LocalDate? {
        val s = v as? String ?: return null
        if (!Regex("\\d{4}-\\d{2}-\\d{2}").matches(s)) return null
        return try { LocalDate.parse(s) } catch (e: DateTimeParseException) { null }
    }

    // ---------- Session: nothing is saved before confirm ----------

    /** Sends the request body, returns the raw answer body. Throws on a network or HTTP error. */
    fun interface Transport { suspend fun send(requestBody: String): String }

    /** Where confirmed transactions go (the database). Only [QuickAiSession.Pending.confirm] ever calls it. */
    fun interface Sink { suspend fun save(p: Proposal) }

    sealed class Proposed {
        class Ready(val pending: Pending) : Proposed()
        data class NeedsReview(val reason: String, val malformed: Boolean) : Proposed()
        /** The answer came from another model. Discarded, tokens still counted. */
        data class WrongModel(val got: String?, val message: String) : Proposed()
    }

    class Pending internal constructor(val proposals: List<Proposal>, private val sink: Sink) {
        var confirmed: Boolean = false
            private set

        /** The only place a transaction is handed to the database. Saves the chosen proposals (all by default), once. */
        suspend fun confirm(selected: Set<Int> = proposals.indices.toSet()): Int {
            check(!confirmed) { "already confirmed" }
            confirmed = true
            var n = 0
            for ((i, p) in proposals.withIndex()) if (i in selected) { sink.save(p); n++ }
            return n
        }
    }

    class Session(
        private val transport: Transport,
        private val sink: Sink,
        /** Called for every answer that has a usage block, even a discarded one: those tokens were billed. */
        private val onUsage: (Usage, Boolean) -> Unit = { _, _ -> },
        private val onLog: (String) -> Unit = {},
    ) {
        /** Asks the model. Proposes only: the [Sink] is not touched here. */
        suspend fun propose(text: String, today: LocalDate): Proposed {
            if (text.isBlank()) return Proposed.NeedsReview("الرسالة فاضية", false)
            val reply = readReply(transport.send(requestBody(today, text)))
            val ok = reply.result !is Result.WrongModel
            reply.usage?.let { onUsage(it, ok) }
            return when (val r = reply.result) {
                is Result.WrongModel -> {
                    onLog("Discarded an answer from model '${r.got}' (expected '$MODEL').")
                    Proposed.WrongModel(r.got, "The answer came from a different model than expected, so it was thrown away. Nothing was saved.")
                }
                is Result.NeedsReview -> {
                    if (r.malformed) onLog("Discarded an answer that was not valid JSON of the expected shape.")
                    Proposed.NeedsReview(r.reason, r.malformed)
                }
                is Result.Proposals -> Proposed.Ready(Pending(r.items, sink))
            }
        }
    }
}

/** A small strict JSON reader (the core module has no JSON library). Numbers are Doubles. */
internal object Json {
    class JsonError(msg: String) : Exception(msg)

    fun quote(s: String): String {
        val b = StringBuilder("\"")
        for (c in s) when {
            c == '"' -> b.append("\\\"")
            c == '\\' -> b.append("\\\\")
            c == '\n' -> b.append("\\n")
            c == '\r' -> b.append("\\r")
            c == '\t' -> b.append("\\t")
            c < ' ' -> b.append(String.format("\\u%04x", c.code))
            else -> b.append(c)
        }
        return b.append('"').toString()
    }

    fun parse(s: String): Any? {
        val p = Reader(s)
        p.ws()
        val v = p.value(0)
        p.ws()
        if (p.i != s.length) throw JsonError("extra text")
        return v
    }

    private class Reader(val s: String) {
        var i = 0
        fun ws() { while (i < s.length && (s[i] == ' ' || s[i] == '\n' || s[i] == '\r' || s[i] == '\t')) i++ }
        fun fail(m: String): Nothing = throw JsonError("$m at $i")

        fun value(depth: Int): Any? {
            if (depth > 40) fail("too deep")
            if (i >= s.length) fail("end")
            return when (s[i]) {
                '{' -> obj(depth)
                '[' -> arr(depth)
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> num()
            }
        }

        fun lit(w: String, v: Any?): Any? { if (!s.startsWith(w, i)) fail("bad literal"); i += w.length; return v }

        fun num(): Double {
            val m = Regex("-?(0|[1-9]\\d*)(\\.\\d+)?([eE][+-]?\\d+)?").matchAt(s, i) ?: fail("bad number")
            i += m.value.length
            return m.value.toDouble()
        }

        fun str(): String {
            i++
            val b = StringBuilder()
            while (true) {
                if (i >= s.length) fail("open string")
                val c = s[i++]
                when {
                    c == '"' -> return b.toString()
                    c == '\\' -> {
                        if (i >= s.length) fail("open string")
                        when (val e = s[i++]) {
                            '"' -> b.append('"'); '\\' -> b.append('\\'); '/' -> b.append('/')
                            'b' -> b.append('\b'); 'f' -> b.append('\u000c'); 'n' -> b.append('\n'); 'r' -> b.append('\r'); 't' -> b.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) fail("bad escape")
                                b.append(s.substring(i, i + 4).toIntOrNull(16)?.toChar() ?: fail("bad escape")); i += 4
                            }
                            else -> fail("bad escape '$e'")
                        }
                    }
                    c < ' ' -> fail("control char")
                    else -> b.append(c)
                }
            }
        }

        fun arr(depth: Int): List<Any?> {
            i++
            val out = ArrayList<Any?>()
            ws()
            if (i < s.length && s[i] == ']') { i++; return out }
            while (true) {
                ws(); out += value(depth + 1); ws()
                if (i >= s.length) fail("open array")
                if (s[i] == ',') { i++; continue }
                if (s[i] == ']') { i++; return out }
                fail("bad array")
            }
        }

        fun obj(depth: Int): Map<String, Any?> {
            i++
            val out = LinkedHashMap<String, Any?>()
            ws()
            if (i < s.length && s[i] == '}') { i++; return out }
            while (true) {
                ws()
                if (i >= s.length || s[i] != '"') fail("bad key")
                val k = str(); ws()
                if (i >= s.length || s[i] != ':') fail("no colon")
                i++; ws()
                out[k] = value(depth + 1); ws()
                if (i >= s.length) fail("open object")
                if (s[i] == ',') { i++; continue }
                if (s[i] == '}') { i++; return out }
                fail("bad object")
            }
        }
    }
}
