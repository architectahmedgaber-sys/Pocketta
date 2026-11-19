package app.pocketta.core

import java.time.LocalDate
import java.time.YearMonth

/** One move in the piggy bank: money put in (positive) or taken out (negative) on a day. */
data class PiggyEntry(val epochDay: Long, val amount: Double)

/** One month of the piggy bank: what went in net that month and the total after it. */
data class PiggyMonth(val month: YearMonth, val saved: Double, val total: Double)

/** The piggy bank ("حصالة"): everything saved, month by month, with a running total. Pure logic, stored as one text setting. */
object Piggy {
    /** "day=amount" parts joined by ";". Bad parts are skipped. */
    fun parse(s: String): List<PiggyEntry> = s.split(';').mapNotNull {
        val p = it.split('=')
        val d = p.getOrNull(0)?.toLongOrNull()
        val v = p.getOrNull(1)?.toDoubleOrNull()
        if (p.size == 2 && d != null && v != null && v != 0.0) PiggyEntry(d, v) else null
    }.sortedBy { it.epochDay }

    fun format(list: List<PiggyEntry>): String = list.joinToString(";") { "${it.epochDay}=${Math.round(it.amount * 100) / 100.0}" }

    fun add(list: List<PiggyEntry>, day: Long, amount: Double): List<PiggyEntry> =
        if (amount == 0.0) list else (list + PiggyEntry(day, amount)).sortedBy { it.epochDay }

    fun total(list: List<PiggyEntry>): Double = list.sumOf { it.amount }

    /** Every month from the first entry to [today]'s month (empty months show 0), oldest first, with the running total. */
    fun months(list: List<PiggyEntry>, today: LocalDate): List<PiggyMonth> {
        if (list.isEmpty()) return emptyList()
        val first = YearMonth.from(LocalDate.ofEpochDay(list.first().epochDay))
        val last = maxOf(YearMonth.from(today), YearMonth.from(LocalDate.ofEpochDay(list.last().epochDay)))
        val byMonth = list.groupBy { YearMonth.from(LocalDate.ofEpochDay(it.epochDay)) }.mapValues { e -> e.value.sumOf { it.amount } }
        val out = ArrayList<PiggyMonth>()
        var m = first
        var running = 0.0
        while (!m.isAfter(last)) {
            val s = byMonth[m] ?: 0.0
            running += s
            out += PiggyMonth(m, s, running)
            m = m.plusMonths(1)
        }
        return out
    }

    /** Average saved per month over the months listed, or null with fewer than two months. */
    fun averagePerMonth(months: List<PiggyMonth>): Double? =
        if (months.size < 2) null else months.sumOf { it.saved } / months.size
}
