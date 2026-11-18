package app.pocketta.i18n

import java.util.concurrent.ConcurrentHashMap

/**
 * Tiny translation layer. The app is written in English; when the language is Arabic every text goes through [t],
 * which looks the text up in [ArabicWords.dict]. Numbers inside a text are replaced by "#" for the lookup and put back
 * afterwards, so "12 days left in this month" finds "# days left in this month". Texts it doesn't know stay in English.
 */
object Tr {
    const val AR = "AR"
    const val EN = "EN"

    private val cache = ConcurrentHashMap<String, String>()
    private val number = Regex("""\d[\d,]*(?:\.\d+)?[kKM]?""")
    private val currencyTail = Regex("""^(.*\S) ([A-Z]{3})$""")
    private val hash = Regex("#")
    private val hasLetters = Regex("[A-Za-z]")
    private val months = Regex("""\b(January|February|March|April|May|June|July|August|September|October|November|December|Jan|Feb|Mar|Apr|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\b""")
    private val monthDay = Regex("""\b(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec) (\d{1,2})\b""")
    private val days = Regex("""\b(Mon|Tue|Wed|Thu|Fri|Sat|Sun|AM|PM)\b""")

    fun t(s: String, lang: String): String {
        if (lang != AR || s.isBlank() || !hasLetters.containsMatchIn(s)) return s
        cache[s]?.let { return it }
        val r = translate(s)
        if (cache.size > 3000) cache.clear()
        cache[s] = r
        return r
    }

    private fun translate(s: String): String {
        ArabicWords.dict[s]?.let { return it }
        val nums = number.findAll(s).map { it.value }.toList()
        if (nums.isNotEmpty()) {
            val ar = ArabicWords.dict[number.replace(s, "#")]
            if (ar != null) {
                var i = 0
                return hash.replace(ar) { nums.getOrElse(i++) { "#" } }
            }
        }
        for ((re, to) in ArabicWords.rules) {
            val m = re.matchEntire(s) ?: continue
            var out = to
            for (i in 1 until m.groupValues.size) out = out.replace("@" + i, t(m.groupValues[i], AR))
            return out
        }
        // "A · B → C": translate each piece on its own.
        for (sep in listOf(" · ", " → ")) {
            if (s.contains(sep)) return s.split(sep).joinToString(sep) { t(it, AR) }
        }
        currencyTail.matchEntire(s)?.let { m ->
            val head = m.groupValues[1]
            val ar = t(head, AR)
            if (ar != head) return ar + " " + m.groupValues[2]
        }
        return dates(s)
    }

    private fun dates(s: String): String {
        var out = s
        // Arabic puts the day number first: "5 مارس".
        if (monthDay.containsMatchIn(out)) out = monthDay.replace(out) { m -> m.groupValues[2] + " " + (ArabicWords.months[m.groupValues[1]] ?: m.groupValues[1]) }
        if (months.containsMatchIn(out)) out = months.replace(out) { ArabicWords.months[it.value] ?: it.value }
        if (days.containsMatchIn(out)) out = days.replace(out) { ArabicWords.days[it.value] ?: it.value }
        return out
    }
}
