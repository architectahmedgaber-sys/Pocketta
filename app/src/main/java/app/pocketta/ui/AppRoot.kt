package app.pocketta.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    if (current == Route.Assistant) {
        AssistantScreen(repo, nav, prefs, bottomBar = { TabBar(nav, current) })
    } else if (current in tabs) {
        Scaffold(bottomBar = { TabBar(nav, current) }) { pad ->
            val m = Modifier.padding(pad)
            when (current) {
                Route.Home -> HomeScreen(repo, nav, prefs, m)
                Route.Spend -> SpendScreen(repo, nav, prefs, m)
                Route.More -> MoreScreen(repo, nav, prefs, m)
                else -> CommitmentsScreen(repo, nav, prefs, m)
            }
        }
    } else {
        when (current) {
            is Route.Add -> AddScreen(repo, nav, prefs, current)
            Route.Income -> IncomeScreen(repo, nav, prefs)
            Route.Tags -> TagsScreen(repo, nav, prefs)
            is Route.TagDetail -> TagDetailScreen(repo, nav, prefs, current.name)
            Route.Categories -> CategoriesScreen(repo, nav)
            Route.Currencies -> CurrenciesScreen(repo, nav, prefs)
            Route.Settings -> SettingsScreen(repo, nav, prefs, authenticate)
            Route.Plan -> PlanScreen(repo, nav, prefs)
            Route.Wealth -> WealthScreen(repo, nav, prefs)
            Route.Notifications -> NotificationsScreen(repo, nav, prefs)
            Route.Piggy -> PiggyScreen(repo, nav, prefs)
            Route.Goals -> GoalsScreen(repo, nav, prefs)
            Route.MonthPlan -> MonthPlanScreen(repo, nav, prefs)
            else -> {}
        }
    }
}

/** Bottom bar: two tabs on each side, and Miqrish (the assistant) highlighted in the middle. */
@Composable
private fun TabBar(nav: Nav, current: Route) {
    NavigationBar {
        for (t in tabs) {
            if (t == Route.Assistant) {
                NavigationBarItem(
                    selected = current == t,
                    onClick = { nav.tab(t) },
                    icon = {
                        val ring = if (current == t) Modifier.border(3.dp, MaterialTheme.colorScheme.primaryContainer, CircleShape) else Modifier
                        Box(
                            Modifier.size(48.dp).then(ring).shadow(4.dp, CircleShape).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center,
                        ) { Text("🪙", fontSize = 24.sp) }
                    },
                    label = { Text(nameOf(t), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                    colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent),
                )
            } else {
                NavigationBarItem(
                    selected = current == t,
                    onClick = { nav.tab(t) },
                    icon = { Icon(iconOf(t), contentDescription = null) },
                    label = { Text(nameOf(t), maxLines = 1) },
                )
            }
        }
    }
}

private fun nameOf(r: Route) = when (r) {
    Route.Home -> "Today"
    Route.Spend -> "Spending"
    Route.More -> "More"
    Route.Assistant -> "Miqrish"
    else -> "Commitments"
}

private fun iconOf(r: Route): ImageVector = when (r) {
    Route.Home -> Icons.Filled.Home
    Route.Spend -> Icons.AutoMirrored.Filled.List
    Route.More -> Icons.Filled.Menu
    else -> Icons.Filled.DateRange
}
