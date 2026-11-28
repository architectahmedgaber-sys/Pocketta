package app.pocketta.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Star
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pocketta.data.Prefs
import app.pocketta.data.Repo

@Composable
fun AppRoot(repo: Repo, nav: Nav, prefs: Prefs, authenticate: (String, () -> Unit) -> Unit) {
    val current = if (nav.current == Route.Spend) Route.Month else nav.current
    UpdateDialog(prefs)
    Box(Modifier.fillMaxSize()) {
        if (current in tabs) {
            // The bar is hidden while the keyboard is open (chat, typing), so it never sits on top of the keyboard.
            val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
            Scaffold(bottomBar = { if (!keyboardOpen) FloatingBar(nav, current) }) { pad ->
                val m = Modifier.padding(pad)
                when (current) {
                    Route.Home -> Box(m) { HomeScreen(repo, nav, prefs, Modifier); AddFab(nav, Modifier.align(Alignment.BottomEnd)) }
                    Route.Month -> Box(m) { MonthScreen(repo, nav, prefs, Modifier); AddFab(nav, Modifier.align(Alignment.BottomEnd)) }
                    Route.Assistant -> AssistantScreen(repo, nav, prefs, m)
                    Route.More -> MoreScreen(repo, nav, prefs, m)
                    else -> CommitmentsScreen(repo, nav, prefs, m)
                }
            }
        } else {
            when (current) {
                Route.QuickAi -> QuickAiScreen(repo, nav, prefs)
                is Route.Add -> AddScreen(repo, nav, prefs, current)
                Route.Income -> IncomeScreen(repo, nav, prefs)
                Route.Tags -> TagsScreen(repo, nav, prefs)
                is Route.TagDetail -> TagDetailScreen(repo, nav, prefs, current.name)
                is Route.CategoryMonth -> CategoryMonthScreen(repo, nav, prefs, current)
                Route.Categories -> CategoriesScreen(repo, nav)
                Route.Currencies -> CurrenciesScreen(repo, nav, prefs)
                Route.Settings -> SettingsScreen(repo, nav, prefs, authenticate)
                Route.Plan -> PlanScreen(repo, nav, prefs)
                Route.Wealth -> WealthScreen(repo, nav, prefs)
                Route.Notifications -> NotificationsScreen(repo, nav, prefs)
                Route.Piggy -> PiggyScreen(repo, nav, prefs)
                Route.Goals -> GoalsScreen(repo, nav, prefs)
                Route.MonthPlan -> MonthPlanScreen(repo, nav, prefs)
                is Route.Report -> ReportScreen(repo, nav, prefs, current.offset)
                Route.Search -> SearchScreen(repo, nav, prefs)
                else -> {}
            }
        }
        UndoHost(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = if (current in tabs) 92.dp else 20.dp))
    }
}

/**
 * Floating pill: five equal tabs, Miqrish in the middle. The selected tab is a filled green pill.
 * The bar sits in the Scaffold's bottom slot, so screens end above it instead of scrolling underneath.
 */
@Composable
private fun FloatingBar(nav: Nav, current: Route) {
    val hairline = MaterialTheme.colorScheme.outlineVariant
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 18.dp, top = 6.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(64.dp).shadow(10.dp, RoundedCornerShape(32.dp), ambientColor = hairline, spotColor = hairline),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.97f),
            border = BorderStroke(1.dp, hairline),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                for (r in tabs) BarTab(r, current, nav, Modifier.weight(1f))
            }
        }
    }
}

/** The clear "+ Add" button on Today and Month: opens the same add sheet the gold + used to open. */
@Composable
private fun AddFab(nav: Nav, modifier: Modifier) {
    androidx.compose.material3.ExtendedFloatingActionButton(
        onClick = { nav.open(Route.Add()) },
        modifier = modifier.padding(16.dp),
        containerColor = GoldAccent,
        contentColor = androidx.compose.ui.graphics.Color.White,
        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
        text = { Text("Add") },
    )
}

@Composable
private fun BarTab(r: Route, current: Route, nav: Nav, modifier: Modifier) {
    val selected = current == r
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier.padding(horizontal = 2.dp).height(52.dp).clip(RoundedCornerShape(26.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
            .clickable { nav.tab(r) },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        if (r == Route.Assistant) Text("🪙", fontSize = 20.sp, modifier = Modifier.height(22.dp))
        else Icon(iconOf(r), contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
        Text(nameOf(r), color = fg, fontSize = 10.sp, maxLines = 1, softWrap = false)
    }
}

private fun nameOf(r: Route) = when (r) {
    Route.Home -> "Today"
    Route.Month -> "Month"
    Route.More -> "More"
    Route.Assistant -> "Miqrish"
    else -> "Commitments"
}

private fun iconOf(r: Route): ImageVector = when (r) {
    Route.Home -> Icons.Filled.Home
    Route.Month -> Icons.Filled.DateRange
    Route.More -> Icons.Filled.Menu
    Route.Assistant -> Icons.Filled.Star // not drawn: the centre tab shows the coin emoji
    else -> Icons.AutoMirrored.Filled.List
}
