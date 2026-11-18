package app.pocketta.core

import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Planning helpers: "can I afford this?", "what if I save", emergency mode and price-rise alerts. All pure math. */
object Planning {
    // ---------- Savings goal date ----------

    /** Whole months until the goal is reached saving [monthly] a month. 0 = already there, null = can't tell (nothing saved per month). */
    fun monthsToGoal(goalAmount: Double, goalSaved: Double, monthly: Double): Int? {
        if (goalAmount <= 0) return null
        val need = goalAmount - goalSaved
        if (need <= 0) return 0
        if (monthly <= 0) return null
        return ceil(need / monthly - 1e-9).toInt()
    }

    fun goalDate(today: LocalDate, months: Int): LocalDate = today.plusMonths(months.toLong())

    // ---------- Can I afford this? ----------

    enum class Verdict { YES, TIGHT, NO }

    data class Afford(
        val price: Double,
        val dailyBefore: Double,
        val dailyAfter: Double,
        val leftBefore: Double,
        val leftAfter: Double,
        val verdict: Verdict,
        /** Money missing this month (0 when it fits). It would have to come out of savings. */
        val shortfall: Double,
        /** Months the savings goal moves back if the shortfall is taken from savings, or null when there is no goal or nothing saved monthly. */
        val goalDelayMonths: Int?,
    )

    /**
     * What buying something for [price] does to this month. [dailyAllowance] and [left] come from [BudgetResult].
     * YES: it fits and the daily number stays at least half. TIGHT: it fits but squeezes the days. NO: it doesn't fit this month.
     */
    fun canAfford(
        price: Double, dailyAllowance: Double, left: Double, daysLeft: Int,
        goalAmount: Double = 0.0, goalSaved: Double = 0.0, savingsPerMonth: Double = 0.0,
    ): Afford {
        val days = max(daysLeft, 1)
        val dailyAfter = dailyAllowance - price / days
        val leftAfter = left - price
        val verdict = when {
            leftAfter < 0 -> Verdict.NO
            dailyAllowance > 0 && dailyAfter >= dailyAllowance * 0.5 -> Verdict.YES
            else -> Verdict.TIGHT
        }
        val shortfall = max(0.0, -leftAfter)
        val delay = if (shortfall > 0 && goalAmount > goalSaved && savingsPerMonth > 0) ceil(shortfall / savingsPerMonth - 1e-9).toInt() else if (shortfall > 0) null else 0
        return Afford(price, dailyAllowance, dailyAfter, left, leftAfter, verdict, shortfall, delay)
    }

    // ---------- What if I save? ----------

    data class Simulation(
        /** What cutting frees up every month. */
        val extraPerMonth: Double,
        val monthsBefore: Int?,
        val monthsAfter: Int?,
        val monthsSooner: Int,
    )

    /** Cutting [percent] of a category that costs [categoryMonthly] a month: how much sooner the goal comes. */
    fun simulateCut(
        categoryMonthly: Double, percent: Int, goalAmount: Double, goalSaved: Double, savingsPerMonth: Double,
    ): Simulation {
        val extra = max(0.0, categoryMonthly) * percent.coerceIn(0, 100) / 100.0
        val before = monthsToGoal(goalAmount, goalSaved, savingsPerMonth)
        val after = monthsToGoal(goalAmount, goalSaved, savingsPerMonth + extra)
        val sooner = if (before != null && after != null) max(0, before - after) else 0
        return Simulation(extra, before, after, sooner)
    }

    // ---------- Emergency mode ----------

    data class Postponable(val name: String, val amount: Double, val due: LocalDate)
    data class CategorySpend(val name: String, val spent: Double)
    data class Cut(val name: String, val amount: Double)

    data class Emergency(
        /** The day the money is gone if you keep spending like this. */
        val runOutDate: LocalDate,
        /** Days between running out and the end of the month. */
        val daysShort: Int,
        val shortfall: Double,
        val postpone: List<Postponable>,
        val cuts: List<Cut>,
        /** What is still missing after every suggestion (0 = the plan covers it). */
        val uncovered: Double,
    )

    /** Categories you can't really cut. Everything else is fair game. */
    val fixedCategories: Set<String> = setOf("Bills", "Installments", "Gam'eya", "Loans", "Home", "Subscriptions", "Education", "Health")

    /**
     * Null while the month looks fine. Otherwise when the money runs out and what to postpone or cut to reach the end of the month.
     * [left] is what's left after what's spent and still to pay, [everydaySpent] the spending so far that wasn't a bill,
     * [dayIndex] how many days have passed. Needs at least [minDays] days of data so day one doesn't scare anyone.
     */
    fun emergency(
        today: LocalDate, cycle: Cycle, budget: BudgetResult, everydaySpent: Double, dayIndex: Int,
        postponable: List<Postponable>, categories: List<CategorySpend>, minDays: Int = 4,
    ): Emergency? {
        if (budget.budget <= 0 || dayIndex < minDays || everydaySpent <= 0) return null
        if (budget.forecastDiff >= 0) return null
        val shortfall = -budget.forecastDiff
        val perDay = everydaySpent / dayIndex
        val daysAhead = if (budget.left <= 0) 0 else floor(budget.left / perDay).toInt()
        val runOut = today.plusDays(daysAhead.toLong()).let { if (it.isAfter(cycle.end)) cycle.end else it }
        val daysShort = java.time.temporal.ChronoUnit.DAYS.between(runOut, cycle.end).toInt().coerceAtLeast(0)
        var remaining = shortfall
        val postpone = ArrayList<Postponable>()
        for (p in postponable.filter { it.amount > 0 && it.due.isAfter(today) }.sortedByDescending { it.amount }) {
            if (remaining <= 0) break
            postpone += p
            remaining -= p.amount
        }
        val cuts = ArrayList<Cut>()
        val daysToGo = max(budget.daysLeft - 1, 1)
        val flexible = categories.filter { it.name !in fixedCategories && it.spent > 0 }.sortedByDescending { it.spent }.take(4)
        for (c in flexible) {
            if (remaining <= 0) break
            val projected = c.spent / dayIndex * daysToGo
            val cut = min(remaining, roundUp10(projected * 0.4))
            if (cut >= 10) {
                cuts += Cut(c.name, min(cut, projected))
                remaining -= min(cut, projected)
            }
        }
        return Emergency(runOut, daysShort, shortfall, postpone, cuts, max(0.0, remaining))
    }

    private fun roundUp10(v: Double): Double = ceil(v / 10.0) * 10.0

    // ---------- Price-rise alert ----------

    data class Rise(val name: String, val percent: Int, val thisMonth: Double, val usual: Double)

    /**
     * Categories where this month is heading higher than the usual (average of the last 3 months). [spentSoFar] is projected to the full month
     * by days passed. Only categories with a real usual amount and at least [minPercent] more are listed, biggest rise first.
     */
    fun priceRises(
        categories: List<CategoryTrend>, dayIndex: Int, cycleLength: Int, minPercent: Int = 20, minUsual: Double = 100.0,
    ): List<Rise> {
        if (dayIndex < 5 || cycleLength <= 0) return emptyList()
        return categories.mapNotNull { c ->
            if (c.usual < minUsual) return@mapNotNull null
            val projected = c.spentSoFar / dayIndex * cycleLength
            val pct = ((projected - c.usual) / c.usual * 100).toInt()
            if (pct >= minPercent) Rise(c.name, pct, projected, c.usual) else null
        }.sortedByDescending { it.percent }
    }

    data class CategoryTrend(val name: String, val spentSoFar: Double, val usual: Double)

    // ---------- Real wealth ----------

    /** Everything you have, in the base currency: money in wallets, gold at today's price, and dollars if you keep any. */
    data class Wealth(val cash: Double, val gold: Double, val foreign: Double) {
        val total: Double get() = cash + gold + foreign
    }

    fun wealth(walletsBase: Double, goldValue: Double, foreignBase: Double = 0.0) = Wealth(walletsBase, goldValue, foreignBase)

    /** [total] in dollars at [usdRate] (base currency per 1 USD), or null when the rate is unknown. */
    fun inDollars(total: Double, usdRate: Double?): Double? = usdRate?.takeIf { it > 0 }?.let { total / it }

    /** Gold-equivalent grams of [total] at today's price per gram of [karat], or null when unknown. */
    fun inGoldGrams(total: Double, pricePerGram: Double?): Double? = pricePerGram?.takeIf { it > 0 }?.let { total / it }

    // ---------- Weekly summary (two sentences) ----------

    /** Two short sentences for Friday. [pct] is the change against the week before (null when there was no week before). */
    fun weekSentences(spent: Double, pct: Int?, topCategory: String?, daysUnder: Int, currency: String): Pair<String, String> {
        val first = when {
            pct == null -> "You spent ${Money.short(spent)} $currency this week."
            pct > 0 -> "You spent ${Money.short(spent)} $currency this week, $pct% more than last week."
            pct < 0 -> "You spent ${Money.short(spent)} $currency this week, ${-pct}% less than last week."
            else -> "You spent ${Money.short(spent)} $currency this week, the same as last week."
        }
        val second = when {
            topCategory != null && daysUnder >= 5 -> "Most of it went to $topCategory, and you stayed under your daily budget on $daysUnder days."
            topCategory != null && daysUnder > 0 -> "Most of it went to $topCategory, and $daysUnder days were under your daily budget."
            topCategory != null -> "Most of it went to $topCategory."
            else -> "Keep going, next week is a fresh start."
        }
        return first to second
    }
}
