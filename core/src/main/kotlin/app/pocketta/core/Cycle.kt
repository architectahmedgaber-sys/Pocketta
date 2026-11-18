package app.pocketta.core

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** A budget cycle ("month"): [start] inclusive, [nextStart] exclusive. */
data class Cycle(val start: LocalDate, val nextStart: LocalDate) {
    val end: LocalDate get() = nextStart.minusDays(1)
    val lengthDays: Int get() = ChronoUnit.DAYS.between(start, nextStart).toInt()

    /** Days left including [today]. On the last day of the cycle this is 1. */
    fun daysLeftIncluding(today: LocalDate): Int = ChronoUnit.DAYS.between(today, nextStart).toInt().coerceAtLeast(1)

    /** Days until the plan renews (the day the next cycle starts). */
    fun daysToRenewal(today: LocalDate): Int = ChronoUnit.DAYS.between(today, nextStart).toInt().coerceAtLeast(0)

    /** 1-based index of [today] in the cycle. */
    fun dayIndex(today: LocalDate): Int = ChronoUnit.DAYS.between(start, today).toInt() + 1

    fun contains(date: LocalDate): Boolean = !date.isBefore(start) && date.isBefore(nextStart)

    fun days(): List<LocalDate> = (0 until lengthDays).map { start.plusDays(it.toLong()) }

    fun previous(startDay: Int): Cycle = CycleMath.cycleFor(start.minusDays(1), startDay)

    fun next(startDay: Int): Cycle = CycleMath.cycleFor(nextStart, startDay)
}

object CycleMath {
    /** The cycle start date in [month]: the chosen day, or the last day of the month if it is shorter. */
    fun startIn(month: YearMonth, startDay: Int): LocalDate {
        val day = startDay.coerceIn(1, 31).coerceAtMost(month.lengthOfMonth())
        return month.atDay(day)
    }

    /** The cycle that contains [today], for a plan that renews on [startDay] of each month. */
    fun cycleFor(today: LocalDate, startDay: Int): Cycle {
        val thisMonth = YearMonth.from(today)
        val startThisMonth = startIn(thisMonth, startDay)
        return if (!today.isBefore(startThisMonth)) {
            Cycle(startThisMonth, startIn(thisMonth.plusMonths(1), startDay))
        } else {
            Cycle(startIn(thisMonth.minusMonths(1), startDay), startThisMonth)
        }
    }
}
