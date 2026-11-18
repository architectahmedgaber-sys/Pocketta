package app.pocketta.core

data class Quick(val amount: Double?, val note: String)

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

    fun parse(input: String): Quick {
        val s = normalizeDigits(input).replace(Regex("(?<=\\d) (?=\\d{3}\\b)"), "")
        val m = number.find(s) ?: return Quick(null, input.trim())
        var amount = m.groupValues[1].toDoubleOrNull()
        if (amount != null && m.groupValues[2].isNotEmpty()) amount *= 1000
        val note = (s.substring(0, m.range.first) + " " + s.substring(m.range.last + 1))
            .replace(Regex("\\s+"), " ").trim()
            .replace(Regex("(^|\\s)(EGP|egp|LE|le|L\\.E\\.?|جنيه|ج\\.?م?)$"), "").trim()
        return Quick(amount, note)
    }
}
