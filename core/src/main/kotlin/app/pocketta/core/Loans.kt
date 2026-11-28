package app.pocketta.core

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.round

/** One planned repayment of a loan. [paid] is how much of [amount] was already repaid. */
data class Instalment(val id: Long, val due: LocalDate, val amount: Double, val paid: Double = 0.0) {
    val remaining: Double get() = (amount - paid).coerceAtLeast(0.0)
    val isPaid: Boolean get() = remaining < 0.005
}

/** A line of the "what will happen" preview: what the loan does to one month. */
data class LoanMonthLine(val month: YearMonth, val income: Double, val commitment: Double)

object LoanSchedule {
    private fun cents(x: Double): Double = round(x * 100.0) / 100.0

    /**
     * [count] monthly instalments adding up to exactly [total]: the same day of the month as [first]
     * (clamped to the month end, 31 Jan -> 28 Feb -> 31 Mar), the rounding remainder on the last one.
     */
    fun generate(total: Double, count: Int, first: LocalDate): List<Pair<LocalDate, Double>> {
        val n = count.coerceAtLeast(1)
        val each = cents(total / n)
        return (0 until n).map { k ->
            val ym = YearMonth.from(first).plusMonths(k.toLong())
            val day = first.dayOfMonth.coerceAtMost(ym.lengthOfMonth())
            val amount = if (k == n - 1) cents(total - each * (n - 1)) else each
            ym.atDay(day) to amount
        }
    }

    /** What a loan does to each month: the borrowed money is income in the month it came, each instalment a commitment in its month. */
    fun preview(lent: Boolean, total: Double, day: LocalDate, schedule: List<Pair<LocalDate, Double>>, recordedIncome: Boolean = true): List<LoanMonthLine> {
        val income = HashMap<YearMonth, Double>()
        val commit = HashMap<YearMonth, Double>()
        // Borrowing brings money in now; lending sends money out now (an expense, not a commitment).
        if (!lent && recordedIncome) income.merge(YearMonth.from(day), total, Double::plus)
        for ((d, a) in schedule) {
            val m = YearMonth.from(d)
            // Lent money coming back is income later; borrowed money going back is a commitment.
            if (lent) income.merge(m, a, Double::plus) else commit.merge(m, a, Double::plus)
        }
        return (income.keys + commit.keys).distinct().sorted().map { LoanMonthLine(it, income[it] ?: 0.0, commit[it] ?: 0.0) }
    }

    /** Splits [payment] over the oldest unpaid instalments first. Returns (instalment id, amount applied) and any leftover. */
    fun allocate(instalments: List<Instalment>, payment: Double): Pair<List<Pair<Long, Double>>, Double> {
        var left = payment
        val out = ArrayList<Pair<Long, Double>>()
        for (i in instalments.filter { !it.isPaid }.sortedBy { it.due }) {
            if (left < 0.005) break
            val use = minOf(left, i.remaining)
            out += i.id to use
            left -= use
        }
        return out to (if (left < 0.005) 0.0 else left)
    }

    /** Takes [amount] back off the instalments, the most recently paid first (undo / deleting a repayment). Returns (id, amount removed). */
    fun unallocate(instalments: List<Instalment>, amount: Double): List<Pair<Long, Double>> {
        var left = amount
        val out = ArrayList<Pair<Long, Double>>()
        for (i in instalments.filter { it.paid > 0.004 }.sortedByDescending { it.due }) {
            if (left < 0.005) break
            val take = minOf(left, i.paid)
            out += i.id to take
            left -= take
        }
        return out
    }

    /**
     * What is still to be paid, as items for the commitments list. Unpaid instalments only; the ones due before
     * [today] are late and are kept in the current cycle (they are not repeated in their old month).
     */
    fun openItems(instalments: List<Instalment>): List<Instalment> = instalments.filter { !it.isPaid }.sortedBy { it.due }

    /** The first unpaid instalment: the one the "I paid" button settles. */
    fun next(instalments: List<Instalment>): Instalment? = openItems(instalments).firstOrNull()

    /** Instalments rebuilt after an edit: paid ones stay, the unpaid rest is regenerated for what is left. */
    fun regenerate(paid: List<Instalment>, newTotal: Double, remainingCount: Int, firstUnpaid: LocalDate): List<Pair<LocalDate, Double>> {
        val left = cents(newTotal - paid.sumOf { it.paid })
        if (left < 0.005) return emptyList()
        return generate(left, remainingCount, firstUnpaid)
    }

    /** One lump instalment for a loan made before instalments existed (migration and old backups). */
    fun legacy(remaining: Double, dueDay: Long?): Pair<LocalDate, Double>? =
        if (dueDay == null || remaining < 0.005) null else LocalDate.ofEpochDay(dueDay) to remaining

    fun same(a: Double, b: Double) = abs(a - b) < 0.005
}
