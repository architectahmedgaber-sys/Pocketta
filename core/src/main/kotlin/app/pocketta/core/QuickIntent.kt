package app.pocketta.core

/** What a typed sentence means. Loans are never saved from here: the app opens the loan form with a preview first. */
enum class QuickKind { EXPENSE, INCOME, BORROWED, LENT }

/**
 * [confident] is false when the words pull two ways (or there is no amount), so the screen shows the choices
 * (expense / income, category) for the person to pick instead of guessing silently.
 */
data class QuickIntent(
    val kind: QuickKind,
    val amount: Double?,
    val note: String,
    val person: String = "",
    val daysAgo: Int = 0,
    val confident: Boolean = true,
)

/** Same word, same key: lower case, no diacritics, one spelling of alef / teh marbuta / yeh. */
object TextKey {
    fun normalize(s: String): String = buildString {
        for (c in s.trim()) {
            when {
                c in 'ً'..'ْ' || c == 'ٰ' || c == 'ـ' -> {}
                c == 'أ' || c == 'إ' || c == 'آ' -> append('ا')
                c == 'ة' -> append('ه')
                c == 'ى' -> append('ي')
                else -> append(c.lowercaseChar())
            }
        }
    }.replace(Regex("\\s+"), " ")
}

/** Reads sentences like "مرتب 12000", "استلفت ٣٠٠٠ من محمد", "سلفت محمد ٥٠٠", "borrowed 3000 from Sam", "lent Sam 500". */
object QuickIntentParser {
    private val borrowVerbs = setOf("استلفت", "استلف", "اقترضت", "اقترض", "borrowed", "borrow", "استدنت")
    private val lendVerbs = setOf("سلفت", "اسلفت", "سلف", "اقرضت", "اقرض", "lent", "lend", "loaned", "اديت")
    private val incomeWords = setOf("مرتب", "مرتبي", "راتب", "راتبي", "قبضت", "قبض", "استلمت", "salary", "income", "paycheck", "payday", "received")
    private val fromWords = setOf("من", "from")
    private val toWords = setOf("ل", "الى", "to", "لـ")

    fun parse(input: String): QuickIntent {
        val q = QuickText.parse(input)
        val words = TextKey.normalize(q.note).split(' ').filter { it.isNotEmpty() }
        val rawWords = q.note.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val loanIdx = words.indexOfFirst { it in borrowVerbs || it in lendVerbs }
        val hasIncome = words.any { it in incomeWords } || Regex("(?i)got paid|paid me").containsMatchIn(q.note)
        if (loanIdx >= 0) {
            val borrowed = words[loanIdx] in borrowVerbs
            if (hasIncome && words[loanIdx] !in incomeWords) {
                return QuickIntent(QuickKind.EXPENSE, q.amount, q.note, daysAgo = q.daysAgo, confident = false)
            }
            // The person is whatever follows "من / from" (borrowed) or the words after the verb, without "to / ل" (lent).
            val after = rawWords.drop(loanIdx + 1)
            val afterKeys = words.drop(loanIdx + 1)
            val person = if (borrowed) {
                val i = afterKeys.indexOfFirst { it in fromWords }
                if (i >= 0) after.drop(i + 1).joinToString(" ") else after.joinToString(" ")
            } else {
                after.filterIndexed { i, _ -> afterKeys[i] !in toWords }.joinToString(" ")
            }
            return QuickIntent(
                if (borrowed) QuickKind.BORROWED else QuickKind.LENT, q.amount, q.note,
                person = person.trim(), daysAgo = q.daysAgo, confident = q.amount != null,
            )
        }
        if (hasIncome) return QuickIntent(QuickKind.INCOME, q.amount, q.note, daysAgo = q.daysAgo, confident = q.amount != null)
        return QuickIntent(QuickKind.EXPENSE, q.amount, q.note, daysAgo = q.daysAgo, confident = q.amount != null && q.note.isNotBlank())
    }
}
