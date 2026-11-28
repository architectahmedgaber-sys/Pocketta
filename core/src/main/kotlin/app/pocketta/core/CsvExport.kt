package app.pocketta.core

import java.time.LocalDate
import java.util.Locale

object CsvExport {
    data class Row(val epochDay: Long, val type: String, val amount: Double, val currency: String, val rate: Double, val categoryLabel: String, val walletName: String, val note: String, val tags: List<String>)

    private fun cell(v: String): String {
        var s = v
        if (s.isNotEmpty() && (s[0] == '=' || s[0] == '+' || s[0] == '-' || s[0] == '@' || s[0] == '\t' || s[0] == '\r')) s = "'$s"
        return if (s.any { it == '"' || it == ',' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }

    private fun num(v: Double) = String.format(Locale.ROOT, "%.2f", v)
    private fun rate(v: Double): String = if (v == Math.floor(v) && Math.abs(v) < 1e15) v.toLong().toString() else v.toString().removeSuffix(".0")

    /** Rows must already be sorted: date ascending, then id ascending. Amount columns must not hit a half-cent tie (amounts have at most 2 decimals). */
    fun build(rows: List<Row>, baseCurrency: String): String {
        val head = listOf("Date", "Type", "Amount", "Currency", "Rate", "Amount ($baseCurrency)", "Category", "Wallet", "Note", "Tags")
        val sb = StringBuilder("\uFEFF")
        sb.append(head.joinToString(",") { cell(it) }).append("\r\n")
        for (r in rows) {
            sb.append(listOf(LocalDate.ofEpochDay(r.epochDay).toString(), r.type, num(r.amount), r.currency, rate(r.rate), num(r.amount * r.rate), r.categoryLabel, r.walletName, r.note, r.tags.joinToString("|")).joinToString(",") { cell(it) }).append("\r\n")
        }
        return sb.toString()
    }
}
