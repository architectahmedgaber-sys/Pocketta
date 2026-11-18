package app.pocketta.core

import java.time.LocalDate

/** A fixed plan paid monthly: consumer finance, credit card plan, etc. */
data class InstallmentPlan(val amount: Double, val total: Int, val paid: Int, val firstDue: LocalDate) {
    val remainingCount: Int get() = (total - paid).coerceAtLeast(0)
    val remainingAmount: Double get() = remainingCount * amount
    val done: Boolean get() = paid >= total
    val nextDue: LocalDate? get() = if (done) null else dueOf(paid)
    val endDate: LocalDate get() = dueOf((total - 1).coerceAtLeast(0))
    val progress: Float get() = if (total <= 0) 0f else paid.toFloat() / total

    /** Due date of payment number [index] (0-based). */
    fun dueOf(index: Int): LocalDate {
        val m = firstDue.plusMonths(index.toLong()).withDayOfMonth(1)
        return m.withDayOfMonth(firstDue.dayOfMonth.coerceAtMost(m.lengthOfMonth()))
    }

    /** Unpaid due dates in [from, toExclusive). */
    fun unpaidDueIn(from: LocalDate, toExclusive: LocalDate): List<LocalDate> =
        (paid until total).map { dueOf(it) }.filter { !it.isBefore(from) && it.isBefore(toExclusive) }
}

/** A gam'eya (savings circle): only my own share, my turn and my payout. */
data class Circle(val share: Double, val months: Int, val myTurn: Int, val paid: Int, val firstDue: LocalDate) {
    val plan: InstallmentPlan get() = InstallmentPlan(share, months, paid, firstDue)
    val payout: Double get() = share * months
    val payoutDate: LocalDate get() = plan.dueOf((myTurn - 1).coerceIn(0, (months - 1).coerceAtLeast(0)))
    val currentMonth: Int get() = (paid + 1).coerceAtMost(months)
}
