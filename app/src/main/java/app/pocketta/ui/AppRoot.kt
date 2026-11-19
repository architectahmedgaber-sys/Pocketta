package app.pocketta.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import app.pocketta.data.Prefs
import app.pocketta.data.Repo

@Composable
fun AppRoot(repo: Repo, nav: Nav, prefs: Prefs, authenticate: (String, () -> Unit) -> Unit) {
    val current = nav.current
    UpdateDialog(prefs)
    if (current in tabs) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    for (t in tabs) {
                        NavigationBarItem(
                            selected = current == t,
                            onClick = { nav.tab(t) },
                            icon = { Icon(iconOf(t), contentDescription = null) },
                            label = { Text(nameOf(t)) },
                        )
                    }
                }
            },
        ) { pad ->
            val m = Modifier.padding(pad)
            when (current) {
                Route.Home -> HomeScreen(repo, nav, prefs, m)
                Route.Spend -> SpendScreen(repo, nav, prefs, m)
                Route.More -> MoreScreen(repo, nav, prefs, m)
                else -> CommitmentsScreen(repo, prefs, m)
            }
        }
    } else {
        when (current) {
            is Route.Add -> AddScreen(repo, nav, prefs, current)
            Route.Income -> IncomeScreen(repo, nav, prefs)
            Route.Wallets -> WalletsScreen(repo, nav, prefs)
            Route.Categories -> CategoriesScreen(repo, nav)
            Route.Currencies -> CurrenciesScreen(repo, nav, prefs)
            Route.Settings -> SettingsScreen(repo, nav, prefs, authenticate)
            Route.Plan -> PlanScreen(repo, nav, prefs)
            Route.Wealth -> WealthScreen(repo, nav, prefs)
            Route.Notifications -> NotificationsScreen(repo, nav, prefs)
            Route.Piggy -> PiggyScreen(repo, nav, prefs)
            Route.Goals -> GoalsScreen(repo, nav, prefs)
            Route.MonthPlan -> MonthPlanScreen(repo, nav, prefs)
            Route.Assistant -> AssistantScreen(repo, nav, prefs)
            else -> {}
        }
    }
}

private fun nameOf(r: Route) = when (r) {
    Route.Home -> "Today"
    Route.Spend -> "Spending"
    Route.More -> "More"
    else -> "Commitments"
}

private fun iconOf(r: Route): ImageVector = when (r) {
    Route.Home -> Icons.Filled.Home
    Route.Spend -> Icons.AutoMirrored.Filled.List
    Route.More -> Icons.Filled.Menu
    else -> Icons.Filled.DateRange
}
