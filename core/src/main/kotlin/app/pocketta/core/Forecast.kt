package app.pocketta.core

object Forecast {
    /** Days from today until the money runs out, or null when it lasts to the end of the cycle. [left] = budget - spent - unpaid commitments. [daysLeft] counts today. */
    fun runOutIn(left: Double, avg: Double, daysLeft: Int): Int? {
        if (avg <= 0) return null
        if (left <= 0) return 0
        val d = Math.floor(left / avg).toInt()
        return if (d >= daysLeft) null else d
    }

    enum class Status { OK, TIGHT, OVER }

    fun status(forecastDiff: Double, budget: Double): Status = when {
        forecastDiff < 0 -> Status.OVER
        budget > 0 && forecastDiff < budget * 0.1 -> Status.TIGHT
        else -> Status.OK
    }
}
