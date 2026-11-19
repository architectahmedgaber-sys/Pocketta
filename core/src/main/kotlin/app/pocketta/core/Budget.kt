package app.pocketta.core

import java.time.LocalDate

/**
 * Everything the home screen needs for one cycle, in the base currency.
 *
 * Budget for the cycle = income received + salary still expected − money set aside
 * (monthly share of quarterly/yearly bills and the savings amount).
 * Today's allowance = (budget − spent before today − unpaid commitments still due this cycle) ÷ days left, today included.
 */
data class BudgetInput(
    val cycle: Cycle,
    val today: LocalDate,
    val incomeReceived: Double,
    val salaryExpected: Double,
    val setAside: Double,
    val spentBeforeToday: Double,
    val spentToday: Double,
    /** Unpaid bills, installments, circle shares and debts due between the cycle start and its end. */
    val commitments: Double,
    /** Spending that was not a bill/installment payment, used for the forecast. */
    val everydaySpent: Double,
    /** Monthly amount kept apart for household shopping (groceries use it first). */
    val household: Double = 0.0,
    /** Grocery spending this cycle (counted only when [household] is set). */
    val householdSpent: Double = 0.0,
)

data class BudgetResult(
    val budget: Double,
    val spent: Double,
    val committed: Double,
    val left: Double,
    val daysLeft: Int,
    val dailyAllowance: Double,
    val leftToday: Double,
    val forecastSpend: Double,
    val forecastDiff: Double,
    /** Money for everyday spending after every commitment (paid or not) and the household reserve. */
    val flexTotal: Double = 0.0,
    val flexSpent: Double = 0.0,
    val flexLeft: Double = 0.0,
    val flexSpentFraction: Float = 0f,
    /** Share of the cycle that has passed, to compare with [flexSpentFraction]. */
    val elapsedFraction: Float = 0f,
    val household: Double = 0.0,
) {
    /** Share of the budget already spent, for the ring. */
    val spentFraction: Float get() = if (budget <= 0) 0f else (spent / budget).toFloat().coerceIn(0f, 1f)
    val committedFraction: Float get() = if (budget <= 0) 0f else (committed / budget).toFloat().coerceIn(0f, 1f - spentFraction)
}

object Budget {
    fun compute(i: BudgetInput): BudgetResult {
        val budget = i.incomeReceived + i.salaryExpected - i.setAside
        val spent = i.spentBeforeToday + i.spentToday
        val daysLeft = i.cycle.daysLeftIncluding(i.today)
        val available = budget - i.spentBeforeToday - i.commitments
        val daily = available / daysLeft
        val dayIndex = i.cycle.dayIndex(i.today).coerceAtLeast(1)
        val avg = i.everydaySpent / dayIndex
        val forecast = spent + i.commitments + avg * (daysLeft - 1)
        val flexTotal = budget - (spent - i.everydaySpent) - i.commitments - i.household
        val flexSpent = (i.everydaySpent - i.householdSpent + maxOf(i.householdSpent - i.household, 0.0)).coerceAtLeast(0.0)
        val flexFrac = if (flexTotal > 0) (flexSpent / flexTotal).toFloat().coerceIn(0f, 1f) else if (flexSpent > 0) 1f else 0f
        return BudgetResult(
            flexTotal = flexTotal, flexSpent = flexSpent, flexLeft = flexTotal - flexSpent, flexSpentFraction = flexFrac,
            elapsedFraction = (dayIndex.toFloat() / maxOf(i.cycle.lengthDays, 1)).coerceIn(0f, 1f), household = i.household,
            budget = budget,
            spent = spent,
            committed = i.commitments,
            left = budget - spent - i.commitments,
            daysLeft = daysLeft,
            dailyAllowance = daily,
            leftToday = daily - i.spentToday,
            forecastSpend = forecast,
            forecastDiff = budget - forecast,
        )
    }

    enum class Level { OK, NEAR, OVER }

    /** Color for a day's bar or a category limit. */
    fun level(value: Double, limit: Double): Level = when {
        limit <= 0 -> Level.OK
        value > limit -> Level.OVER
        value >= limit * 0.8 -> Level.NEAR
        else -> Level.OK
    }
}
