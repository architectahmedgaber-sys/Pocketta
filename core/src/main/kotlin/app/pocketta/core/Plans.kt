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

/** Where a gam'eya stands today: what's left to pay and when it's your turn to collect. */
data class CircleStatus(
    val installmentsLeft: Int,
    val nextDue: LocalDate?,
    val payout: Double,
    val payoutDate: LocalDate,
    val payoutReceived: Boolean,
    /** Days until your turn. 0 = today, negative = it has passed. */
    val daysToTurn: Int,
    /** Everything is paid and the payout is in your hands. */
    val finished: Boolean,
    /** It's your turn (or past it) and you haven't marked the payout received. */
    val collectNow: Boolean,
)

object Circles {
    fun status(c: Circle, payoutReceived: Boolean, today: LocalDate): CircleStatus {
        val days = java.time.temporal.ChronoUnit.DAYS.between(today, c.payoutDate).toInt()
        return CircleStatus(
            installmentsLeft = c.plan.remainingCount,
            nextDue = c.plan.nextDue,
            payout = c.payout,
            payoutDate = c.payoutDate,
            payoutReceived = payoutReceived,
            daysToTurn = days,
            finished = c.plan.done && payoutReceived,
            collectNow = !payoutReceived && days <= 0,
        )
    }
}
