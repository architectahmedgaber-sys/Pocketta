package app.pocketta.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.pocketta.data.TxType

sealed interface Route {
    data object Home : Route
    /** Old name of the Month tab; kept so any old link or shortcut still lands on the right screen. */
    data object Spend : Route
    data object Month : Route
    data object Commit : Route
    data object More : Route
    data class Add(
        val txnId: Long = 0, val type: String = TxType.EXPENSE, val category: Long = 0,
        /** Filled in from a receipt photo or a bank message. */
        val amount: Double = 0.0, val note: String = "", val currency: String = "",
        /** Epoch day to record it on (0 = today). Used when a calendar day is tapped. */
        val day: Long = 0L,
        /** The bank-message card this came from (0 = none): it is cleared once the entry is saved. */
        val smsId: Int = 0,
    ) : Route
    data object Income : Route
    data object Tags : Route
    data class TagDetail(val name: String) : Route
    data object Categories : Route
    data object Currencies : Route
    data object Settings : Route
    data object Plan : Route
    data object Wealth : Route
    data object Notifications : Route
    data object Piggy : Route
    data object Goals : Route
    data object MonthPlan : Route
    data object Assistant : Route
    data object QuickAi : Route
    /** One month of one category (categoryId) or one tag: its total, every day and all its spending. */
    data class CategoryMonth(val categoryId: Long = 0, val tag: String = "") : Route
    /** The monthly report of the month shown on the Month screen ([offset] cycles back). */
    data class Report(val offset: Int = 0) : Route
    data object Search : Route
}

/** Four tabs; the add button sits in the middle of the bar. Miqrish (Assistant) is a screen opened from Today. */
val tabs = listOf(Route.Home, Route.Month, Route.Commit, Route.More)

/** A tiny back stack: one tab at the bottom, screens opened on top of it. */
class Nav {
    var stack by mutableStateOf(listOf<Route>(Route.Home))
        private set

    val current: Route get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1 || stack.first() != Route.Home

    fun tab(r: Route) { stack = listOf(if (r == Route.Spend) Route.Month else r) }
    fun open(r: Route) { stack = if (stack.last() is Route.Add && r is Route.Add) stack.dropLast(1) + r else stack + r }
    fun back() { stack = if (stack.size > 1) stack.dropLast(1) else listOf(Route.Home) }
}
