package app.pocketta.core

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

object Money {
    private val symbols = DecimalFormatSymbols(Locale.US)
    private val whole = DecimalFormat("#,##0", symbols)
    @Volatile private var decimals = 2
    /** The main currency last set with [setCurrency]. */
    @Volatile var currency: String = "EGP"
        private set
    @Volatile private var fraction = DecimalFormat("#,##0.##", symbols)

    /** Set when the main currency is known or changes, so every amount shows that currency's decimals (dinar: 3, EGP: 2, IQD: 0). */
    fun setCurrency(currency: String) {
        this.currency = currency
        val d = Countries.decimalsOf(currency)
        decimals = d
        fraction = DecimalFormat("#,##0" + if (d > 0) "." + "#".repeat(d) else "", symbols)
    }

    /** 1,234 for whole numbers, 12.5 otherwise (up to the currency's decimals). */
    fun fmt(v: Double): String {
        val f = 10.0.pow(decimals)
        val r = (v * f).roundToLong() / f
        return if (decimals == 0 || abs(r - r.roundToLong()) < 0.5 / f) whole.format(r.roundToLong()) else fraction.format(r)
    }

    fun fmt(v: Double, currency: String): String = "${fmt(v)} $currency"

    /** Rounded to whole units for big numbers on the home screen and widgets (3-decimal currencies keep their fils). */
    fun short(v: Double): String = if (decimals >= 3) fmt(v) else whole.format(v.roundToLong())

    fun signed(v: Double): String = (if (v > 0) "+" else if (v < 0) "−" else "") + fmt(abs(v))
}
