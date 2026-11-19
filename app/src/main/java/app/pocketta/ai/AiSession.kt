package app.pocketta.ai

import app.pocketta.core.CycleMath
import app.pocketta.data.CircleEntity
import app.pocketta.data.HomeState
import app.pocketta.data.Repo
import app.pocketta.data.WeekData
import app.pocketta.data.Source
import app.pocketta.data.Tags
import app.pocketta.data.Txn
import app.pocketta.data.TxType
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

private fun p(name: String, type: String, desc: String, required: Boolean = false, choices: List<String> = emptyList()) = AiParam(name, type, desc, required, choices)

val aiTools: List<AiTool> = listOf(
    AiTool("get_summary", "This month's money picture: income, spent, what is left for flexible spending this month, budget, upcoming payments.", emptyList(), false),
    AiTool("spent_by_category", "Total spent per category in one month.", listOf(p("months_ago", "integer", "0 = this month, 1 = last month")), false),
    AiTool("list_recent", "The latest transactions with their ids.", listOf(p("limit", "integer", "How many, default 10")), false),
    AiTool("tag_totals", "Everything saved under tags (projects such as a wedding or a trip): total spent per tag and how it splits by category.", emptyList(), false),
    AiTool("list_commitments", "Bills, installments, gam'eyas (savings circles) and open loans with amounts, progress and due dates.", emptyList(), false),
    AiTool("remember", "Save a short fact about the user or their habits so it is remembered next time (for example 'koshary = Food category').", listOf(p("note", "string", "The fact, one short sentence", true)), false),
    AiTool(
        "add_expense", "Record money the user spent.",
        listOf(
            p("amount", "number", "Amount in the base currency", true), p("note", "string", "What it was, for example koshary", true),
            p("category", "string", "Category name from the list, if clear"), p("days_ago", "integer", "0 = today, 1 = yesterday"),
            p("tags", "string", "Comma separated tags (projects) like a wedding, only if the user named one"),
        ),
        true,
    ),
    AiTool(
        "add_income", "Record money the user received (salary, side work, gift, gam'eya payout).",
        listOf(p("amount", "number", "Amount", true), p("note", "string", "What it was", true), p("category", "string", "Income category name"), p("days_ago", "integer", "0 = today"), p("tags", "string", "Comma separated tags (projects), only if the user named one")),
        true,
    ),
    AiTool(
        "add_loan", "Record a loan. direction 'borrowed' = the user took money from someone (the user owes). direction 'lent' = the user gave money to someone (they owe the user).",
        listOf(
            p("person", "string", "The other person", true), p("direction", "string", "borrowed or lent", true, listOf("borrowed", "lent")),
            p("amount", "number", "Amount", true), p("due_date", "string", "Pay back by, YYYY-MM-DD, if said"),
        ),
        true,
    ),
    AiTool("pay_installment", "Mark the next installment of a plan as paid.", listOf(p("name", "string", "Installment name", true)), true),
    AiTool("pay_bill", "Mark a bill or subscription as paid now.", listOf(p("name", "string", "Bill name", true)), true),
    AiTool("pay_circle_share", "Mark this month's share of a gam'eya as paid.", listOf(p("name", "string", "Gam'eya name", true)), true),
    AiTool("receive_circle_payout", "Mark that the user collected the gam'eya payout.", listOf(p("name", "string", "Gam'eya name", true)), true),
    AiTool(
        "add_circle", "Start a new gam'eya (savings circle).",
        listOf(
            p("name", "string", "Name", true), p("share", "number", "Monthly share", true), p("months", "integer", "Number of months", true),
            p("my_turn", "integer", "Which month the user collects, 1 = first", true), p("first_due", "string", "Date of the first share, YYYY-MM-DD", true),
        ),
        true,
    ),
    AiTool(
        "update_transaction", "Change an existing transaction (get the id from list_recent).",
        listOf(p("id", "integer", "Transaction id", true), p("amount", "number", "New amount"), p("note", "string", "New note"), p("category", "string", "New category name"), p("tags", "string", "Replace the tags with these comma separated tags (empty text removes them)")),
        true,
    ),
    AiTool("delete_transaction", "Delete a transaction (get the id from list_recent).", listOf(p("id", "integer", "Transaction id", true)), true),
)

/**
 * One chat with the assistant. It asks the company's model what to do, runs the tools it picks, and asks the user to confirm before anything is written.
 */
class AiSession(
    private val repo: Repo,
    private val cfg: AiConfig,
    /** Shows what is about to be saved and returns what the user answered. */
    private val confirm: suspend (String) -> Boolean,
    /** Called with each thing the assistant says or does, to show in the chat. */
    private val show: (role: String, text: String) -> Unit,
) {
    private val history = ArrayList<AiMsg>()

    suspend fun send(text: String) {
        history += AiMsg.User(text)
        val system = systemPrompt()
        var rounds = 0
        while (rounds++ < 8) {
            val reply = AiClient.chat(cfg, system, history, aiTools)
            history += AiMsg.Assistant(reply.text, reply.calls)
            if (reply.text.isNotBlank()) show("ai", reply.text)
            if (reply.calls.isEmpty()) return
            val results = ArrayList<ToolResult>()
            for (c in reply.calls) {
                val tool = aiTools.firstOrNull { it.name == c.name }
                val out = if (tool == null) "Unknown tool"
                else if (tool.write) {
                    val what = describe(c.name, c.args)
                    if (confirm(what)) {
                        val r = runCatching { run(c.name, c.args) }.getOrElse { "Failed: " + (it.message ?: "error") }
                        show("done", what)
                        r
                    } else "The user said no. Nothing was saved."
                } else runCatching { run(c.name, c.args) }.getOrElse { "Failed: " + (it.message ?: "error") }
                results += ToolResult(c.id, c.name, out)
            }
            history += AiMsg.Results(results)
        }
    }

    private suspend fun systemPrompt(): String {
        val p = repo.prefs()
        val cats = repo.dao.categories().filter { !it.archived }
        val exp = cats.filter { it.kind == TxType.EXPENSE && it.parentId == null }.joinToString(", ") { it.name }
        val inc = cats.filter { it.kind == TxType.INCOME }.joinToString(", ") { it.name }
        val notes = cfg.notes()
        return """
You are Miqrish (مِقرِش), a friendly personal money advisor inside the Pocketta app. Today is ${LocalDate.now()}. Base currency: ${p.baseCurrency}.
Reply in the user's language. If they write Egyptian Arabic, answer in short, simple Egyptian Arabic. Keep answers short.

How Pocketta works:
- Spending: every expense has an amount, a note and a category. Income: salary, side work, gifts, gam'eya payout, borrowed money.
- Bills and subscriptions repeat (monthly, yearly...). Installments are fixed monthly payments (valU, credit card plans). A gam'eya is a savings circle: the user pays a monthly share and collects the full payout in their turn month.
- Loans: 'borrowed' means the user took money and owes it back (it counts as income this month and a payment due later). 'lent' means the user gave money and is owed it back (it leaves their wallet). The Arabic word سلفة / استلفت / واخد سلفة means borrowed; سلّفت / سلّفته means lent. If it is unclear which one the user means, ask before saving.
- Expense categories: $exp
- Income categories: $inc

Rules:
- To record or change anything, call the matching tool. The app shows the user a confirmation first, so call the tool directly instead of asking in words, but only when you have the amount and what it was. If something is missing or unclear, ask one short question.
- Never invent amounts, ids or names. Use list_recent or list_commitments to find them.
- Tags (وسوم) mark projects such as a wedding or a trip; set them only when the user names one. For totals per tag call tag_totals.
- For questions about their money, call get_summary, spent_by_category or list_commitments, then answer from the numbers.
- When the user corrects you or tells you a habit, call remember with one short sentence.
${if (notes.isEmpty()) "" else "\nWhat you already know about this user:\n" + notes.joinToString("\n") { "- $it" }}
        """.trimIndent()
    }

    private fun d(a: JSONObject, k: String): Double? = if (a.has(k) && !a.isNull(k)) a.optDouble(k).takeIf { !it.isNaN() } else null
    private fun s(a: JSONObject, k: String): String = if (a.has(k) && !a.isNull(k)) a.optString(k).trim() else ""
    private fun n(a: JSONObject, k: String): Int? = d(a, k)?.toInt()
    private fun day(a: JSONObject): LocalDate = LocalDate.now().minusDays((n(a, "days_ago") ?: 0).toLong().coerceAtLeast(0))
    private fun date(str: String): LocalDate? = runCatching { LocalDate.parse(str) }.getOrNull()
    private fun money(v: Double) = if (v % 1.0 == 0.0) v.toLong().toString() else String.format("%.2f", v)

    /** One plain sentence about what a write tool is going to do. */
    private fun describe(name: String, a: JSONObject): String = when (name) {
        "add_expense" -> "Record an expense: ${money(d(a, "amount") ?: 0.0)} · ${s(a, "note")}" + s(a, "category").let { if (it.isEmpty()) "" else " · $it" } + s(a, "tags").let { if (it.isEmpty()) "" else " · #$it" } +
            (n(a, "days_ago") ?: 0).let { if (it > 0) " · $it days ago" else "" }
        "add_income" -> "Record income: ${money(d(a, "amount") ?: 0.0)} · ${s(a, "note")}" + s(a, "category").let { if (it.isEmpty()) "" else " · $it" }
        "add_loan" -> if (s(a, "direction") == "lent") "Record a loan you gave: ${money(d(a, "amount") ?: 0.0)} to ${s(a, "person")} (they owe you)"
        else "Record a loan you took: ${money(d(a, "amount") ?: 0.0)} from ${s(a, "person")} (you owe)" + s(a, "due_date").let { if (it.isEmpty()) "" else " · pay back by $it" }
        "pay_installment" -> "Mark the next installment of ${s(a, "name")} as paid"
        "pay_bill" -> "Mark ${s(a, "name")} as paid"
        "pay_circle_share" -> "Mark your share of ${s(a, "name")} as paid"
        "receive_circle_payout" -> "Mark that you collected the payout of ${s(a, "name")}"
        "add_circle" -> "Start a gam'eya ${s(a, "name")}: ${money(d(a, "share") ?: 0.0)} a month for ${n(a, "months")} months, your turn is month ${n(a, "my_turn")}"
        "update_transaction" -> "Change transaction #${n(a, "id")}" + (d(a, "amount")?.let { " · amount ${money(it)}" } ?: "") +
            s(a, "note").let { if (it.isEmpty()) "" else " · note $it" } + s(a, "category").let { if (it.isEmpty()) "" else " · category $it" }
        "delete_transaction" -> "Delete transaction #${n(a, "id")}"
        else -> name
    }

    private suspend fun categoryId(name: String, kind: String): Long? {
        if (name.isBlank()) return null
        val all = repo.dao.categories().filter { it.kind == kind && !it.archived }
        return (all.firstOrNull { it.name.equals(name, true) } ?: all.firstOrNull { it.name.contains(name, true) || name.contains(it.name, true) })?.id
    }

    private suspend fun run(name: String, a: JSONObject): String {
        val dao = repo.dao
        when (name) {
            "get_summary" -> {
                val h = repo.home()
                val up = h.upcoming.take(6).joinToString("; ") { "${it.title} ${money(it.amount)} on ${it.due}" }
                return "Income this month ${money(h.income)}; spent ${money(h.budget.spent)}; budget ${money(h.budget.budget)}; committed (still to pay) ${money(h.budget.committed)}; " +
                    "left for flexible spending ${money(h.budget.flexLeft)} of ${money(h.budget.flexTotal)}; days left ${h.budget.daysLeft}; upcoming: ${up.ifEmpty { "none" }}"
            }
            "spent_by_category" -> {
                val ym = LocalDate.now().minusMonths((n(a, "months_ago") ?: 0).toLong().coerceAtLeast(0))
                val from = ym.withDayOfMonth(1)
                val cats = dao.categories().associateBy { it.id }
                val rows = dao.txns(from.toEpochDay(), from.plusMonths(1).toEpochDay()).filter { it.type == TxType.EXPENSE && !it.spread }
                val by = rows.groupBy { t -> t.categoryId?.let { cats[it] }?.let { c -> (c.parentId?.let { cats[it] } ?: c).name } ?: "Other" }
                    .mapValues { e -> e.value.sumOf { it.base } }.entries.sortedByDescending { it.value }
                return "Month ${from.year}-${from.monthValue}: " + by.joinToString("; ") { "${it.key} ${money(it.value)}" }.ifEmpty { "no spending" } + "; total ${money(by.sumOf { it.value })}"
            }
            "list_recent" -> {
                val lim = (n(a, "limit") ?: 10).coerceIn(1, 30)
                val cats = dao.categories().associateBy { it.id }
                val today = LocalDate.now().toEpochDay()
                val rows = dao.txns(today - 45, today + 1).sortedWith(compareByDescending<Txn> { it.epochDay }.thenByDescending { it.id }).take(lim)
                return JSONArray().apply {
                    rows.forEach { t ->
                        put(JSONObject().put("id", t.id).put("date", LocalDate.ofEpochDay(t.epochDay).toString()).put("type", t.type).put("amount", t.base).put("note", t.note).put("category", t.categoryId?.let { cats[it]?.name } ?: "").put("tags", JSONArray(t.tagList)))
                    }
                }.toString()
            }
            "tag_totals" -> {
                val rows = repo.tagStats()
                return if (rows.isEmpty()) "No tags yet" else JSONArray().apply {
                    rows.forEach { r -> put(JSONObject().put("tag", r.name).put("spent", Math.round(r.spent)).put("earned", Math.round(r.earned)).put("count", r.count).put("by_category", JSONObject().also { o -> r.cats.forEach { (k, v) -> o.put(k, Math.round(v)) } })) }
                }.toString()
            }
            "list_commitments" -> {
                val o = JSONObject()
                o.put("bills", JSONArray().apply { dao.bills().forEach { put(JSONObject().put("name", it.name).put("amount", it.amount).put("next_due", LocalDate.ofEpochDay(it.nextDue).toString())) } })
                o.put("installments", JSONArray().apply { dao.installments().forEach { put(JSONObject().put("name", it.name).put("amount", it.amount).put("paid", it.paid).put("total", it.total)) } })
                o.put("circles", JSONArray().apply {
                    dao.circles().forEach { put(JSONObject().put("name", it.name).put("share", it.share).put("paid", it.paid).put("months", it.months).put("my_turn", it.myTurn).put("payout_received", it.payoutReceived)) }
                })
                o.put("loans", JSONArray().apply {
                    dao.loans().filter { !it.closed }.forEach { put(JSONObject().put("person", it.person).put("direction", it.direction).put("remaining", it.remaining).put("due", it.dueDay?.let { d -> LocalDate.ofEpochDay(d).toString() } ?: "")) }
                })
                return o.toString()
            }
            "remember" -> { cfg.addNote(s(a, "note")); return "Saved." }
            "add_expense" -> {
                val amount = d(a, "amount")?.takeIf { it > 0 } ?: return "Missing amount"
                val note = s(a, "note")
                val p = repo.prefs()
                val cat = categoryId(s(a, "category"), TxType.EXPENSE) ?: repo.guessCategory(note, TxType.EXPENSE) ?: p.lastCategory.takeIf { it != 0L }
                val id = repo.saveTxn(Txn(type = TxType.EXPENSE, amount = amount, currency = p.baseCurrency, rate = 1.0, walletId = repo.newRecordWallet(), categoryId = cat, note = note, epochDay = day(a).toEpochDay(), tags = Tags.join(Tags.parse(s(a, "tags")))))
                return "Saved expense #$id"
            }
            "add_income" -> {
                val amount = d(a, "amount")?.takeIf { it > 0 } ?: return "Missing amount"
                val p = repo.prefs()
                val cat = categoryId(s(a, "category"), TxType.INCOME) ?: repo.categoryNamed("Other income", TxType.INCOME)
                val id = repo.saveTxn(Txn(type = TxType.INCOME, amount = amount, currency = p.baseCurrency, rate = 1.0, walletId = repo.newRecordWallet(), categoryId = cat, note = s(a, "note"), epochDay = day(a).toEpochDay(), tags = Tags.join(Tags.parse(s(a, "tags")))))
                return "Saved income #$id"
            }
            "add_loan" -> {
                val amount = d(a, "amount")?.takeIf { it > 0 } ?: return "Missing amount"
                val person = s(a, "person").ifEmpty { return "Missing person" }
                val lent = s(a, "direction") == "lent"
                repo.addLoan(person, lent, amount, null, LocalDate.now(), date(s(a, "due_date")), "")
                return if (lent) "Saved: $person owes the user ${money(amount)}" else "Saved: the user owes $person ${money(amount)}"
            }
            "pay_installment" -> {
                val i = dao.installments().firstOrNull { it.name.contains(s(a, "name"), true) || s(a, "name").contains(it.name, true) } ?: return "No installment with that name"
                repo.payInstallment(i); return "Marked ${i.name} paid"
            }
            "pay_bill" -> {
                val b = dao.bills().firstOrNull { it.name.contains(s(a, "name"), true) || s(a, "name").contains(it.name, true) } ?: return "No bill with that name"
                repo.payBill(b); return "Marked ${b.name} paid"
            }
            "pay_circle_share" -> {
                val c = dao.circles().firstOrNull { it.name.contains(s(a, "name"), true) || s(a, "name").contains(it.name, true) } ?: return "No gam'eya with that name"
                repo.payCircle(c); return "Marked the share of ${c.name} paid"
            }
            "receive_circle_payout" -> {
                val c = dao.circles().firstOrNull { it.name.contains(s(a, "name"), true) || s(a, "name").contains(it.name, true) } ?: return "No gam'eya with that name"
                repo.receivePayout(c); return "Marked the payout of ${c.name} collected"
            }
            "add_circle" -> {
                val share = d(a, "share")?.takeIf { it > 0 } ?: return "Missing share"
                val months = n(a, "months")?.takeIf { it > 0 } ?: return "Missing months"
                val turn = (n(a, "my_turn") ?: 1).coerceIn(1, months)
                val first = date(s(a, "first_due")) ?: return "Missing first_due date"
                repo.saveCircle(CircleEntity(name = s(a, "name"), share = share, months = months, myTurn = turn, firstDue = first.toEpochDay()))
                return "Gam'eya saved"
            }
            "update_transaction" -> {
                val t = dao.txn((d(a, "id") ?: 0.0).toLong()) ?: return "No transaction with that id"
                var u = t
                d(a, "amount")?.takeIf { it > 0 }?.let { u = u.copy(amount = it) }
                if (s(a, "note").isNotEmpty()) u = u.copy(note = s(a, "note"))
                categoryId(s(a, "category"), t.type)?.let { u = u.copy(categoryId = it) }
                if (a.has("tags")) u = u.copy(tags = Tags.join(Tags.parse(s(a, "tags"))))
                repo.saveTxn(u); return "Updated"
            }
            "delete_transaction" -> {
                val t = dao.txn((d(a, "id") ?: 0.0).toLong()) ?: return "No transaction with that id"
                repo.deleteTxn(t); return "Deleted"
            }
        }
        return "Unknown tool"
    }
}

/** The Friday summary written by the user's own AI key: three short lines. Returns null without a key or when the company refuses. */
object AiWeekly {
    suspend fun summary(cfg: AiConfig, currency: String, spent: Double, previous: Double, top: String?, topAmount: Double, flexLeft: Double, flexTotal: Double, arabic: Boolean): String? {
        if (cfg.key().isBlank()) return null
        val system = "You are Miqrish (مِقرِش), a friendly money advisor inside the Pocketta app. Write the Friday summary in " +
            (if (arabic) "short, simple Egyptian Arabic" else "plain English") +
            ". Exactly three short lines: 1) how much was spent in the last 7 days (compare with the 7 days before if given), 2) the biggest category, 3) one practical tip. No greeting, no markdown, no lists."
        val text = "Currency $currency. Last 7 days: spent ${Math.round(spent)}" + (if (previous > 0) ", the 7 days before: ${Math.round(previous)}" else "") +
            ". Biggest category: ${top ?: "none"}" + (if (topAmount > 0) " (${Math.round(topAmount)})" else "") +
            ". Flexible money left this month: ${Math.round(flexLeft)} of ${Math.round(flexTotal)}."
        return runCatching { AiClient.chat(cfg, system, listOf(AiMsg.User(text)), aiTools).text.trim() }.getOrNull()?.takeIf { it.isNotEmpty() }
    }
}

/** Keeps the AI-written Friday summary: made once per week from the user's own key, saved in settings, shared by the card and the morning message. */
object AiWeeklyStore {
    fun weekKey(today: LocalDate): Long = today.toEpochDay() - ((today.dayOfWeek.value - 5 + 7) % 7)

    /** The saved text for this week, or a fresh one when there is a key. Null when there is neither. */
    suspend fun ensure(context: android.content.Context, repo: Repo, s: HomeState, w: WeekData, generate: Boolean = true): String? {
        val p = s.prefs
        val key = weekKey(s.today)
        if (p.weeklyAiWeek == key && p.weeklyAiText.isNotBlank()) return p.weeklyAiText
        if (!generate || !p.weeklyAiOn || w.spent <= 0) return null
        val b = s.budget
        val text = AiWeekly.summary(AiConfig(context), p.baseCurrency, w.spent, w.previous, w.topCategory, w.topAmount, b.flexLeft, b.flexTotal, p.lang == "AR") ?: return null
        repo.set("weeklyAiWeek", key)
        repo.set("weeklyAiText", text)
        return text
    }
}
