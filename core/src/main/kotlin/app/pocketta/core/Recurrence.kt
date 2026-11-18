package app.pocketta.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class Frequency(val label: String) {
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    QUARTERLY("Every 3 months"),
    SEMIANNUAL("Every 6 months"),
    ANNUAL("Yearly"),
    EVERY_N_DAYS("Every N days");

    companion object {
        fun parse(name: String?): Frequency = entries.firstOrNull { it.name == name } ?: MONTHLY
    }
}

object Recurrence {
    private fun months(freq: Frequency): Long = when (freq) {
        Frequency.MONTHLY -> 1
        Frequency.QUARTERLY -> 3
        Frequency.SEMIANNUAL -> 6
        Frequency.ANNUAL -> 12
        else -> 0
    }

    /** The due date one period after [date]. Month-based periods keep [anchorDay] (clamped to short months). */
    fun next(date: LocalDate, freq: Frequency, everyN: Int, anchorDay: Int = date.dayOfMonth): LocalDate = when (freq) {
        Frequency.WEEKLY -> date.plusWeeks(1)
        Frequency.EVERY_N_DAYS -> date.plusDays(everyN.coerceAtLeast(1).toLong())
        else -> {
            val m = date.plusMonths(months(freq)).withDayOfMonth(1)
            m.withDayOfMonth(anchorDay.coerceIn(1, m.lengthOfMonth()))
        }
    }

    /** All due dates starting at [firstDue] that fall in [from, toExclusive). */
    fun occurrences(firstDue: LocalDate, freq: Frequency, everyN: Int, from: LocalDate, toExclusive: LocalDate, anchorDay: Int = firstDue.dayOfMonth): List<LocalDate> {
        val out = ArrayList<LocalDate>()
        var d = firstDue
        var guard = 0
        while (d.isBefore(toExclusive) && guard < 2000) {
            if (!d.isBefore(from)) out += d
            d = next(d, freq, everyN, anchorDay)
            guard++
        }
        return out
    }

    /** What this costs per month on average (a yearly bill is spread over 12 months). */
    fun monthlyShare(amount: Double, freq: Frequency, everyN: Int): Double = when (freq) {
        Frequency.WEEKLY -> amount * 52.0 / 12.0
        Frequency.MONTHLY -> amount
        Frequency.QUARTERLY -> amount / 3.0
        Frequency.SEMIANNUAL -> amount / 6.0
        Frequency.ANNUAL -> amount / 12.0
        Frequency.EVERY_N_DAYS -> amount * (365.25 / 12.0) / everyN.coerceAtLeast(1)
    }

    fun yearlyCost(amount: Double, freq: Frequency, everyN: Int): Double = monthlyShare(amount, freq, everyN) * 12.0

    /** Bills paid less often than monthly are set aside a little every month instead of hitting one month. */
    fun isSpread(freq: Frequency, everyN: Int): Boolean = when (freq) {
        Frequency.QUARTERLY, Frequency.SEMIANNUAL, Frequency.ANNUAL -> true
        Frequency.EVERY_N_DAYS -> everyN > 31
        else -> false
    }

    fun describe(freq: Frequency, everyN: Int): String =
        if (freq == Frequency.EVERY_N_DAYS) "Every $everyN days" else freq.label

    fun daysUntil(today: LocalDate, due: LocalDate): Long = ChronoUnit.DAYS.between(today, due)
}
