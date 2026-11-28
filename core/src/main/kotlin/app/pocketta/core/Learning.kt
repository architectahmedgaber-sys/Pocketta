package app.pocketta.core

/** One past expense, as far as favourites and guesses need it. */
data class FavEntry(val note: String, val categoryId: Long?, val amount: Double, val epochDay: Long)

data class Favourite(val note: String, val categoryId: Long?, val amount: Double, val count: Int) {
    /** What "remove this chip" remembers. */
    val key: String get() = Favourites.key(note, amount)
}

/** The chips at the top of the add sheet: what you add most often lately ("قهوة 30"). Computed on the phone from the last 60 days. */
object Favourites {
    fun key(note: String, amount: Double) = TextKey.normalize(note) + "|" + Math.round(amount * 100)

    fun top(entries: List<FavEntry>, today: Long, hidden: Set<String> = emptySet(), limit: Int = 6, days: Int = 60, minCount: Int = 2): List<Favourite> {
        val recent = entries.filter { it.note.isNotBlank() && it.epochDay > today - days && it.epochDay <= today }
        data class G(var n: Int, var last: Long, var latest: FavEntry)
        val groups = LinkedHashMap<String, G>()
        for (e in recent.sortedBy { it.epochDay }) {
            val k = key(e.note, e.amount) + "|" + (e.categoryId ?: 0L)
            val g = groups.getOrPut(k) { G(0, 0, e) }
            g.n++; g.last = e.epochDay; g.latest = e
        }
        return groups.values
            .filter { it.n >= minCount && key(it.latest.note, it.latest.amount) !in hidden }
            .sortedWith(compareByDescending<G> { it.n }.thenByDescending { it.last })
            .take(limit)
            .map { Favourite(it.latest.note.trim(), it.latest.categoryId, it.latest.amount, it.n) }
    }
}

/** Smart defaults: the choice made most often wins; a tie goes to the most recent one. */
object SmartDefaults {
    /** [history] is (choice, epochDay). */
    fun <T : Any> mostFrequent(history: List<Pair<T, Long>>): T? {
        if (history.isEmpty()) return null
        val count = HashMap<T, Int>()
        val last = HashMap<T, Long>()
        for ((v, day) in history) {
            count[v] = (count[v] ?: 0) + 1
            last[v] = maxOf(last[v] ?: Long.MIN_VALUE, day)
        }
        return count.keys.maxWithOrNull(compareBy<T> { count[it]!! }.thenBy { last[it]!! })
    }
}

/** A bank message waiting as a "ready to add" card on Today. Nothing is saved until the person taps Add. */
data class SmsCard(val id: Int, val amount: Double, val currency: String, val merchant: String, val epochDay: Long)

object SmsCards {
    const val MAX = 20

    fun encode(cards: List<SmsCard>): String = cards.joinToString("\n") {
        listOf(it.id, it.amount, it.currency, it.epochDay, it.merchant.replace(Regex("[\\t\\r\\n]+"), " ")).joinToString("\t")
    }

    fun decode(s: String): List<SmsCard> = s.lines().mapNotNull { line ->
        val p = line.split('\t')
        if (p.size < 5) return@mapNotNull null
        SmsCard(id = p[0].toIntOrNull() ?: return@mapNotNull null, amount = p[1].toDoubleOrNull() ?: return@mapNotNull null, currency = p[2], merchant = p.drop(4).joinToString(" "), epochDay = p[3].toLongOrNull() ?: return@mapNotNull null)
    }

    /** The same message never makes two cards; the oldest card goes when there are too many. */
    fun add(cards: List<SmsCard>, c: SmsCard): List<SmsCard> =
        if (cards.any { it.id == c.id }) cards else (cards + c).takeLast(MAX)

    fun remove(cards: List<SmsCard>, id: Int): List<SmsCard> = cards.filter { it.id != id }
}
