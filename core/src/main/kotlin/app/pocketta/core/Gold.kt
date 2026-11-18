package app.pocketta.core

/** One purchase of gold: [grams] of [karat] gold bought at [pricePerGram] (in the base currency). */
data class GoldBuy(val karat: String, val grams: Double, val pricePerGram: Double, val epochDay: Long = 0) {
    val cost: Double get() = grams * pricePerGram
}

/** What all the gold is worth today against what was paid. [value] only counts purchases whose karat has a price today. */
data class GoldSummary(val grams: Double, val cost: Double, val value: Double) {
    val profit: Double get() = value - cost
    val percent: Double get() = if (cost > 0) profit / cost * 100.0 else 0.0
}

object Gold {
    /** Stored in one text setting: "karat,grams,price,day" parts joined by ";". Bad parts are skipped. */
    fun parse(s: String): List<GoldBuy> = s.split(';').mapNotNull {
        val p = it.split(',')
        val g = p.getOrNull(1)?.toDoubleOrNull()
        val pr = p.getOrNull(2)?.toDoubleOrNull()
        if (p.size < 3 || p[0].isBlank() || g == null || pr == null || g <= 0 || pr <= 0) null
        else GoldBuy(p[0].trim(), g, pr, p.getOrNull(3)?.toLongOrNull() ?: 0L)
    }

    fun format(list: List<GoldBuy>): String = list.joinToString(";") { "${it.karat},${it.grams},${it.pricePerGram},${it.epochDay}" }

    /** Gain or loss of one purchase against today's price per gram of its karat, or null when today's price isn't known. */
    fun profit(b: GoldBuy, today: Map<String, Double>): Double? = today[b.karat]?.let { b.grams * it - b.cost }

    fun summary(list: List<GoldBuy>, today: Map<String, Double>): GoldSummary? {
        val priced = list.filter { today[it.karat] != null }
        if (priced.isEmpty()) return null
        return GoldSummary(priced.sumOf { it.grams }, priced.sumOf { it.cost }, priced.sumOf { it.grams * (today[it.karat] ?: 0.0) })
    }
}

object Savings {
    /** Leftover that is worth moving into a goal at the end of the month: whole pounds, never more than the goal still needs. 0 means don't suggest. */
    fun suggestMove(left: Double, goalAmount: Double, goalSaved: Double, minimum: Double = 50.0): Double {
        if (goalAmount <= 0 || goalSaved >= goalAmount) return 0.0
        val amount = kotlin.math.floor(minOf(left, goalAmount - goalSaved))
        return if (amount >= minimum || (amount > 0 && goalAmount - goalSaved <= amount)) amount else 0.0
    }
}
