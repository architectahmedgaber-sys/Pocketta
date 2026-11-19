package app.pocketta.core

import java.time.LocalDate
import java.time.YearMonth

/** A month as one whole number: year * 12 + (month - 1). Easy to compare, store and count with. */
object Ym {
    fun of(m: YearMonth): Int = m.year * 12 + m.monthValue - 1
    fun of(d: LocalDate): Int = d.year * 12 + d.monthValue - 1
    fun toMonth(ym: Int): YearMonth = YearMonth.of(Math.floorDiv(ym, 12), Math.floorMod(ym, 12) + 1)
    fun start(ym: Int): LocalDate = toMonth(ym).atDay(1)
    fun endExclusive(ym: Int): LocalDate = toMonth(ym).plusMonths(1).atDay(1)
}

/** Values that change from a month onward (a raise: "from January the salary is 63,000") or only in one month. Stored as one text setting. */
object MonthValues {
    /** "ym=amount" parts joined by ";". Bad parts are skipped. */
    fun parse(s: String): Map<Int, Double> = s.split(';').mapNotNull {
        val p = it.split('=')
        val k = p.getOrNull(0)?.trim()?.toIntOrNull()
        val v = p.getOrNull(1)?.trim()?.toDoubleOrNull()
        if (p.size == 2 && k != null && v != null) k to v else null
    }.toMap()

    fun format(m: Map<Int, Double>): String = m.toSortedMap().entries.joinToString(";") { "${it.key}=${Math.round(it.value * 100) / 100.0}" }

    /** The value of the latest step at or before [ym], or null when there is none. */
    fun stepAt(steps: Map<Int, Double>, ym: Int): Double? = steps.filterKeys { it <= ym }.maxByOrNull { it.key }?.value
}

data class PlanBill(val name: String, val amount: Double, val freq: Frequency, val everyN: Int, val nextDue: LocalDate, val anchorDay: Int)
data class PlanInstallment(val name: String, val amount: Double, val total: Int, val paid: Int, val firstDue: LocalDate, val card: Boolean)
data class PlanCircle(val name: String, val share: Double, val months: Int, val myTurn: Int, val paid: Int, val firstDue: LocalDate, val payoutReceived: Boolean)
data class PlanLoan(val person: String, val remaining: Double, val due: LocalDate?, val lent: Boolean)

data class PlanInput(
    val salaryBase: Double,
    /** Raises: from this month on, the salary is this much. */
    val salarySteps: Map<Int, Double>,
    val livingBase: Double,
    /** A different living cost for one month only. */
    val livingOverrides: Map<Int, Double>,
    val bills: List<PlanBill>,
    val installments: List<PlanInstallment>,
    val circles: List<PlanCircle>,
    val loans: List<PlanLoan>,
)

/** One line of one item in a month, for the details under the numbers. */
data class PlanLine(val kind: String, val name: String, val amount: Double)

/** One month of the plan. Amounts are positive; [remaining] is income minus everything that goes out. */
data class PlanMonth(
    val ym: Int,
    val actual: Boolean,
    val salary: Double,
    val payout: Double,
    /** Money coming back from people you lent to. */
    val loansIn: Double,
    val bills: Double,
    val installments: Double,
    val cards: Double,
    val circleShares: Double,
    /** Money you pay back to people who lent to you. */
    val loansOut: Double,
    val living: Double,
    val lines: List<PlanLine> = emptyList(),
    val cumulative: Double = 0.0,
) {
    val income: Double get() = salary + payout + loansIn
    val committed: Double get() = bills + installments + cards + circleShares + loansOut
    val remaining: Double get() = income - committed - living
}

object MonthPlanner {
    fun salaryIn(i: PlanInput, ym: Int): Double = MonthValues.stepAt(i.salarySteps, ym) ?: i.salaryBase
    fun livingIn(i: PlanInput, ym: Int): Double = i.livingOverrides[ym] ?: i.livingBase

    /** What is expected in each month from [fromYm] for [count] months. Only unpaid things count: paid installments and a payout already collected are history. */
    fun project(i: PlanInput, fromYm: Int, count: Int): List<PlanMonth> {
        val out = ArrayList<PlanMonth>()
        for (ym in fromYm until fromYm + count) {
            val from = Ym.start(ym)
            val to = Ym.endExclusive(ym)
            val lines = ArrayList<PlanLine>()
            var bills = 0.0
            for (b in i.bills) {
                val n = Recurrence.occurrences(b.nextDue, b.freq, b.everyN, from, to, b.anchorDay).size
                if (n > 0) { bills += b.amount * n; lines += PlanLine("BILL", b.name, b.amount * n) }
            }
            var inst = 0.0
            var cards = 0.0
            for (p in i.installments) {
                val plan = InstallmentPlan(p.amount, p.total, p.paid, p.firstDue)
                val n = plan.unpaidDueIn(from, to).size
                if (n > 0) {
                    lines += PlanLine(if (p.card) "CARD" else "INSTALLMENT", p.name, p.amount * n)
                    if (p.card) cards += p.amount * n else inst += p.amount * n
                }
            }
            var shares = 0.0
            var payout = 0.0
            for (c in i.circles) {
                val circle = Circle(c.share, c.months, c.myTurn, c.paid, c.firstDue)
                val n = circle.plan.unpaidDueIn(from, to).size
                if (n > 0) { shares += c.share * n; lines += PlanLine("CIRCLE", c.name, c.share * n) }
                val pd = circle.payoutDate
                if (!c.payoutReceived && !pd.isBefore(from) && pd.isBefore(to)) {
                    payout += circle.payout; lines += PlanLine("PAYOUT", c.name, circle.payout)
                }
            }
            var loansIn = 0.0
            var loansOut = 0.0
            for (l in i.loans) {
                val d = l.due ?: continue
                if (d.isBefore(from) || !d.isBefore(to)) continue
                if (l.lent) { loansIn += l.remaining; lines += PlanLine("LOAN_IN", l.person, l.remaining) }
                else { loansOut += l.remaining; lines += PlanLine("LOAN_OUT", l.person, l.remaining) }
            }
            out += PlanMonth(
                ym = ym, actual = false, salary = salaryIn(i, ym), payout = payout, loansIn = loansIn, bills = bills,
                installments = inst, cards = cards, circleShares = shares, loansOut = loansOut, living = livingIn(i, ym), lines = lines,
            )
        }
        return withCumulative(out)
    }

    /** Adds the running total of [PlanMonth.remaining], starting from the first month in the list. */
    fun withCumulative(months: List<PlanMonth>): List<PlanMonth> {
        var run = 0.0
        return months.map { run += it.remaining; it.copy(cumulative = run) }
    }
}
