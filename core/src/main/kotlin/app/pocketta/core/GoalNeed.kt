package app.pocketta.core

import java.time.LocalDate
import kotlin.math.ceil

object GoalNeed {
    enum class Status { REACHED, NODATE, ONTRACK, BEHIND }
    data class Need(val status: Status, val needed: Long?, val months: Int?)

    /** Whole calendar months from today to the deadline (year/month difference only), at least 1. */
    fun monthsUntil(today: LocalDate, deadline: LocalDate): Int =
        maxOf(1, ((deadline.year * 12 + deadline.monthValue) - (today.year * 12 + today.monthValue)))

    fun need(target: Double, saved: Double, monthly: Double, deadline: LocalDate?, today: LocalDate): Need {
        if (target > 0 && saved >= target) return Need(Status.REACHED, 0, 0)
        if (deadline == null) return Need(Status.NODATE, null, null)
        val months = monthsUntil(today, deadline)
        val needed = ceil((target - saved) / months).toLong()
        return Need(if (monthly >= needed) Status.ONTRACK else Status.BEHIND, needed, months)
    }
}
