package app.pocketta.core

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

object Money {
    private val whole = DecimalFormat("#,##0", DecimalFormatSymbols(Locale.US))
    private val cents = DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.US))

    /** 1,234 for whole numbers, 12.5 otherwise. */
    fun fmt(v: Double): String {
        val r = (v * 100).roundToLong() / 100.0
        return if (abs(r - r.roundToLong()) < 0.005) whole.format(r.roundToLong()) else cents.format(r)
    }

    fun fmt(v: Double, currency: String): String = "${fmt(v)} $currency"

    /** Rounded down to whole pounds, for big numbers on the home screen and widgets. */
    fun short(v: Double): String = whole.format(v.roundToLong())

    fun signed(v: Double): String = (if (v > 0) "+" else if (v < 0) "−" else "") + fmt(abs(v))
}
