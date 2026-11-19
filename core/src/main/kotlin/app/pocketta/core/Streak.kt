package app.pocketta.core

/** The saving challenge: days in a row (ending today) where the last 7 days stayed under the weekly share of the flexible money. */
data class StreakResult(val days: Int, val best: Int, val weekSpent: Double, val target: Double) {
    val weekLeft: Double get() = target - weekSpent
}

object Streak {
    /** [perDay]: flexible spending by epoch day. [firstDay]: the first day with any spending (days before it do not count). */
    fun compute(perDay: Map<Long, Double>, today: Long, weeklyTarget: Double, firstDay: Long?): StreakResult? {
        if (weeklyTarget <= 0 || firstDay == null || firstDay > today) return null
        fun trailing(d: Long): Double { var t = 0.0; for (k in 0L until 7L) t += perDay[d - k] ?: 0.0; return t }
        var days = 0
        var d = today
        while (d >= firstDay) { if (trailing(d) <= weeklyTarget) { days++; d-- } else break }
        var best = 0
        var run = 0
        var x = maxOf(firstDay, today - 120)
        while (x <= today) { if (trailing(x) <= weeklyTarget) { run++; best = maxOf(best, run) } else run = 0; x++ }
        return StreakResult(days, best, trailing(today), weeklyTarget)
    }

    /** One plain tip for the Friday summary when there is no AI key. Keys are English; the app translates them. */
    fun weeklyTip(spent: Double, previous: Double, top: String?, flexLeft: Double, daysLeft: Int): String = when {
        spent <= 0 -> "No spending recorded this week. Add what you spend so the summary has something to say."
        flexLeft < 0 -> "You are over the flexible amount for this month: skip anything you can wait on until the next salary."
        previous > 0 && spent > previous * 1.2 && top != null -> "Spending is up on last week. $top is the place to look first."
        previous > 0 && spent < previous * 0.9 -> "Good week: you spent less than the week before. Keep the same pace."
        top != null && daysLeft > 0 -> "$top took the biggest share. A small limit on it would keep the rest of the month calm."
        else -> "A steady week. Keep logging expenses and the month stays under control."
    }
}
