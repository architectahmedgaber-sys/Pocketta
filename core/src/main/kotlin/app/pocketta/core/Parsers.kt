package app.pocketta.core

/** Category-limit alerts: say it once per cycle, only when the limit is passed. */
object LimitAlerts {
    fun isOver(spent: Double, limit: Double): Boolean = limit > 0 && spent > limit

    /** "cycleStartDay|id,id" */
    fun parse(s: String): Pair<Long, Set<Long>> {
        val p = s.split('|')
        val day = p.getOrNull(0)?.toLongOrNull() ?: return 0L to emptySet()
        return day to (p.getOrNull(1)?.split(',')?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet())
    }

    fun format(day: Long, ids: Set<Long>): String = "$day|" + ids.sorted().joinToString(",")

    /** The categories in [over] that were not already announced in this cycle ([cycleDay]). */
    fun fresh(cycleDay: Long, alerted: String, over: List<Long>): List<Long> {
        val (d, ids) = parse(alerted)
        val known = if (d == cycleDay) ids else emptySet()
        return over.filter { it !in known }
    }
}

/** Arabic digits to 0-9, keeping thousands commas and decimal points. */
internal fun digits(s: String): String = buildString {
    for (c in s) append(
        when (c) {
            in '٠'..'٩' -> '0' + (c - '٠')
            in '۰'..'۹' -> '0' + (c - '۰')
            '٫' -> '.'
            '٬' -> ','
            else -> c
        }
    )
}

/** An expense read from a bank text message. */
data class SmsExpense(val amount: Double, val currency: String, val merchant: String)

/** Reads bank SMS text (English and Arabic): spending only, never codes or money coming in. */
object SmsParser {
    private val skip = Regex("""(otp|one[- ]?time|verification|password|رمز|كود|تحقق|cvv)""", RegexOption.IGNORE_CASE)
    private val credit = Regex("""(credited|received|deposit|refund|salary|استلمت|إيداع|ايداع|تم اضافة|تم إضافة|راتب|مسترد)""", RegexOption.IGNORE_CASE)
    private val debit = Regex("""(purchase|spent|debited|withdraw|withdrawn|paid|payment|charged|مشتريات|شراء|سحب|خصم|دفع|عملية)""", RegexOption.IGNORE_CASE)
    private const val CUR = """EGP|LE|L\.E\.?|USD|EUR|SAR|AED|جنيه|ج\.م\.?|ر\.س|\$"""
    private const val NUM = """\d[\d,]*(?:\.\d{1,2})?"""
    private val amountRx = Regex("""(?:($CUR)\s*($NUM)|($NUM)\s*($CUR))""", RegexOption.IGNORE_CASE)
    private val merchantRx = Regex("""(?:\bat|\bfrom|لدى|عند)\s+([A-Za-z0-9؀-ۿ][A-Za-z0-9؀-ۿ &'._*-]{1,30})""", RegexOption.IGNORE_CASE)

    fun parse(body: String): SmsExpense? {
        val text = digits(body)
        if (skip.containsMatchIn(text) || credit.containsMatchIn(text) || !debit.containsMatchIn(text)) return null
        val m = amountRx.find(text) ?: return null
        val cur = m.groupValues[1].ifEmpty { m.groupValues[4] }.trim()
        val num = m.groupValues[2].ifEmpty { m.groupValues[3] }.replace(",", "").toDoubleOrNull() ?: return null
        if (num <= 0) return null
        val code = when (cur.uppercase()) {
            "EGP", "LE", "L.E", "L.E.", "جنيه", "ج.م", "ج.م." -> "EGP"
            "$", "USD" -> "USD"
            "ر.س", "SAR" -> "SAR"
            else -> cur.uppercase()
        }
        val rest = text.substring(m.range.last + 1)
        val merchant = merchantRx.find(rest)?.groupValues?.get(1).orEmpty()
            .split(Regex("""\s+(on|at|in|ending|balance|bal|avail\w*|ref|بتاريخ|الرصيد)\b""", RegexOption.IGNORE_CASE)).first()
            .trim().trimEnd('.', ',', ' ')
        return SmsExpense(num, code, merchant)
    }
}

/** Picks the total from the text read off a receipt photo. */
object ReceiptParser {
    private val totalWord = Regex("""(total|amount due|grand|balance due|الإجمالي|الاجمالي|إجمالي|اجمالي|المجموع|المطلوب|الصافي)""", RegexOption.IGNORE_CASE)
    private val subTotal = Regex("""sub\s*-?\s*total""", RegexOption.IGNORE_CASE)
    private val numberRx = Regex("""\d[\d,]*(?:\.\d{1,2})?""")

    private fun clean(s: String) = digits(s)

    private fun numbers(line: String): List<Double> =
        numberRx.findAll(clean(line)).mapNotNull { it.value.replace(",", "").toDoubleOrNull() }.toList()

    /** The amount on the last "total" line (not sub-total), else the biggest amount with decimals, else null. */
    fun total(text: String): Double? {
        val lines = text.lines()
        val totals = lines.withIndex().filter { (_, l) -> totalWord.containsMatchIn(l) && !subTotal.containsMatchIn(l) }
        for ((i, l) in totals.reversed()) {
            val n = numbers(l).ifEmpty { numbers(lines.getOrElse(i + 1) { "" }) }
            n.maxOrNull()?.let { if (it > 0) return it }
        }
        val dec = lines.flatMap { l ->
            numberRx.findAll(clean(l)).map { it.value.replace(",", "") }.filter { it.contains('.') }.mapNotNull { it.toDoubleOrNull() }.toList()
        }
        return dec.maxOrNull()?.takeIf { it > 0 }
    }
}
