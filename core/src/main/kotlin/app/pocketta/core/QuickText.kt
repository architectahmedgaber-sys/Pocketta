package app.pocketta.core

/** [daysAgo] is 1 when the text says "yesterday" (أمس / امبارح), else 0. */
data class Quick(val amount: Double?, val note: String, val daysAgo: Int = 0)

/** Reads quick entries like "koshary 60", "60 uber", "كشري ٦٠" or "1.5k rent". */
object QuickText {
    private val number = Regex("""(\d+(?:\.\d+)?)([kK](?!\p{L}))?""")

    fun normalizeDigits(s: String): String = buildString {
        for (c in s) append(
            when (c) {
                in '٠'..'٩' -> '0' + (c - '٠')
                in '۰'..'۹' -> '0' + (c - '۰')
                '٫' -> '.'
                '٬', ',' -> ' '
                else -> c
            }
        )
    }

    private val yesterdayWord = Regex("""(?<![\p{L}])(yesterday|أمس|امس|امبارح|إمبارح)(?![\p{L}])""", RegexOption.IGNORE_CASE)
    private val todayWord = Regex("""(?<![\p{L}])(today|النهارده|النهاردة|اليوم)(?![\p{L}])""", RegexOption.IGNORE_CASE)

    /** Like [parseAmount], and also understands a day word: "yesterday taxi 85", "امبارح تاكسي 85". */
    fun parse(input: String): Quick {
        if (yesterdayWord.containsMatchIn(input)) return parseAmount(yesterdayWord.replace(input, " ")).copy(daysAgo = 1)
        if (todayWord.containsMatchIn(input)) return parseAmount(todayWord.replace(input, " "))
        return parseAmount(input)
    }

    private fun parseAmount(input: String): Quick {
        val s = normalizeDigits(input).replace(Regex("(?<=\\d) (?=\\d{3}\\b)"), "")
        val m = number.find(s) ?: return Quick(null, input.trim())
        var amount = m.groupValues[1].toDoubleOrNull()
        if (amount != null && m.groupValues[2].isNotEmpty()) amount *= 1000
        val note = (s.substring(0, m.range.first) + " " + s.substring(m.range.last + 1))
            .replace(Regex("\\s+"), " ").trim()
            .replace(Regex("(^|\\s)(EGP|egp|LE|le|L\\.E\\.?|JOD|ILS|LYD|TND|DZD|MAD|MRU|SDG|SAR|AED|KWD|QAR|BHD|OMR|IQD|LBP|SYP|YER|USD|EUR|جنيه|دينار|شيكل|ريال|درهم|ليرة|ج\\.?م?)$", RegexOption.IGNORE_CASE), "").trim()
        return Quick(amount, note)
    }
}
