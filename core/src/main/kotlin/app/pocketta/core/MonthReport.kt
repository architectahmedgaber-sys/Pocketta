package app.pocketta.core

import kotlin.math.roundToInt
import kotlin.math.roundToLong

object MonthReport {
    data class Tx(val type: String, val base: Double, val cat: String, val note: String, val epochDay: Long, val source: String)
    data class Cat(val name: String, val amount: Double, val share: Int)
    data class Big(val note: String, val amount: Double, val day: Long)
    data class Day(val day: Long, val amount: Double)
    data class Report(
        val spent: Double, val income: Double, val net: Double, val prevSpent: Double, val changePct: Int?, val perDay: Double,
        val topCategories: List<Cat>, val topExpenses: List<Big>, val biggestDay: Day?,
    )

    private fun r2(v: Double) = (v * 100).roundToLong() / 100.0

    /** [cat] is the top-level category name ("" when none). [cycleDays] is the length of the cycle. */
    fun build(txns: List<Tx>, prev: List<Tx>, cycleDays: Int): Report {
        fun spend(l: List<Tx>) = l.filter { it.type == "EXPENSE" && it.source != "PIGGY" && it.source != "LOAN" }
        val exp = spend(txns)
        val spent = r2(exp.sumOf { it.base })
        val income = r2(txns.filter { it.type == "INCOME" && it.source != "PIGGYOUT" && it.source != "LOAN" }.sumOf { it.base })
        val prevSpent = r2(spend(prev).sumOf { it.base })
        val cats = exp.groupBy { it.cat }.map { (n, l) -> n to l.sumOf { it.base } }
            .sortedWith(compareByDescending<Pair<String, Double>> { it.second }.thenBy { it.first })
            .take(3).map { Cat(it.first, r2(it.second), if (spent > 0) (it.second / spent * 100).roundToInt() else 0) }
        val top = exp.sortedWith(compareByDescending<Tx> { it.base }.thenBy { it.epochDay }).take(3).map { Big(it.note, r2(it.base), it.epochDay) }
        val days = exp.groupBy { it.epochDay }.map { (d, l) -> Day(d, r2(l.sumOf { it.base })) }
        val best = days.sortedWith(compareByDescending<Day> { it.amount }.thenBy { it.day }).firstOrNull()
        return Report(
            spent, income, r2(income - spent), prevSpent,
            if (prevSpent > 0) ((spent - prevSpent) / prevSpent * 100).roundToInt() else null,
            if (cycleDays > 0) r2(spent / cycleDays) else 0.0, cats, top, best,
        )
    }
}
