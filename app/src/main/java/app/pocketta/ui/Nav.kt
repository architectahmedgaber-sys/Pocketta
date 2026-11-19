package app.pocketta.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.pocketta.data.TxType

sealed interface Route {
    data object Home : Route
    data object Spend : Route
    data object Commit : Route
    data object More : Route
    data class Add(
        val txnId: Long = 0, val type: String = TxType.EXPENSE, val category: Long = 0,
        /** Filled in from a receipt photo or a bank message. */
        val amount: Double = 0.0, val note: String = "", val currency: String = "",
    ) : Route
    data object Income : Route
    data object Wallets : Route
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
}

val tabs = listOf(Route.Home, Route.Spend, Route.Commit, Route.More)

/** A tiny back stack: one tab at the bottom, screens opened on top of it. */
class Nav {
    var stack by mutableStateOf(listOf<Route>(Route.Home))
        private set

    val current: Route get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1 || stack.first() != Route.Home

    fun tab(r: Route) { stack = listOf(r) }
    fun open(r: Route) { stack = if (stack.last() is Route.Add && r is Route.Add) stack.dropLast(1) + r else stack + r }
    fun back() { stack = if (stack.size > 1) stack.dropLast(1) else listOf(Route.Home) }
}
