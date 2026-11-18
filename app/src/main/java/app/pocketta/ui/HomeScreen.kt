package app.pocketta.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pocketta.core.Budget
import app.pocketta.core.Money
import app.pocketta.BuildConfig
import app.pocketta.data.HomeState
import app.pocketta.data.Online
import app.pocketta.data.Prefs
import app.pocketta.data.Repo
import app.pocketta.data.Upcoming
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(repo: Repo, nav: Nav, prefs: Prefs, modifier: Modifier) {
    val version by repo.version.collectAsState()
    var state by remember { mutableStateOf<HomeState?>(null) }
    var details by remember { mutableStateOf(false) }
    LaunchedEffect(version, prefs) { state = repo.home() }
    val s = state ?: return
    val cur = prefs.baseCurrency
    val b = s.budget
    val over = b.leftToday < 0
    val heroColor = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val heroText = if (over) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
    Box(modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.cycle.end.format(monthFmt), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("${b.daysLeft} days left in this month", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { nav.open(Route.More) }) { Icon(Icons.Filled.Settings, contentDescription = "Settings and more") }
            }
            if (prefs.updateVersion.isNotEmpty() && prefs.updateUrl.isNotEmpty() && Online.isNewer(prefs.updateVersion, BuildConfig.VERSION_NAME)) {
                SectionCard(container = MaterialTheme.colorScheme.tertiaryContainer, onClick = { nav.open(Route.Settings) }) {
                    Text("Pocketta ${prefs.updateVersion} is ready", fontWeight = FontWeight.SemiBold)
                    Text("Tap to update. Your data stays as it is.", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (b.budget <= 0 && s.salaryExpected <= 0) {
                SectionCard(onClick = { nav.open(Route.Income) }) {
                    Text("Add your salary to see how much you can spend", fontWeight = FontWeight.SemiBold)
                    Text("Tap here to set it", style = MaterialTheme.typography.bodySmall)
                }
            }

            // The one question: how much can I spend today?
            SectionCard(container = heroColor) {
                Text(if (over) "Over today's budget" else "You can spend today", color = heroText, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(Money.short(kotlin.math.abs(b.leftToday)), fontSize = 52.sp, fontWeight = FontWeight.ExtraBold, color = heroText, maxLines = 1)
                    Text(" $cur", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = heroText, modifier = Modifier.padding(bottom = 10.dp))
                }
                Gap(8)
                val spentToday = b.dailyAllowance - b.leftToday
                val frac = if (b.dailyAllowance > 0) (spentToday / b.dailyAllowance).toFloat() else 0f
                Progress(frac, color = heroText, track = heroText.copy(alpha = 0.25f))
                Gap(6)
                Text("Spent ${Money.short(spentToday)} of ${Money.short(b.dailyAllowance)} today", color = heroText, style = MaterialTheme.typography.bodySmall)
            }

            // The converter lives on the first page so there is a reason to open the app every day.
            ConverterCard(prefs)

            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("This month", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("${Money.short(b.left)} left of ${Money.short(b.budget)} $cur", style = MaterialTheme.typography.bodySmall)
                }
                Gap(8)
                Progress(b.spentFraction.toFloat(), color = if (b.left < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                Gap(6)
                Text("Spent ${Money.short(b.spent)} · still to pay ${Money.short(b.committed)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (s.upcoming.isNotEmpty()) UpcomingCard(repo, s.upcoming.take(3), s, cur)

            TextButton(onClick = { details = !details }) { Text(if (details) "Hide details" else "Show details (forecast, days, how it is calculated)") }
            if (details) {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Label("Month-end forecast")
                            Text("${Money.short(b.forecastSpend)} $cur", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }
                        if (b.forecastDiff >= 0) Pill("${Money.short(b.forecastDiff)} under", Good)
                        else Pill("${Money.short(-b.forecastDiff)} over", MaterialTheme.colorScheme.error)
                    }
                }
                if (s.days.isNotEmpty()) {
                    SectionCard {
                        Label("This month, day by day")
                        Gap(8)
                        DayBars(s)
                    }
                }
                SectionCard {
                    Label("How the budget is made")
                    Gap(6)
                    Line("Income received", Money.short(s.income))
                    if (s.salaryExpected > 0) Line("Salary still coming", Money.short(s.salaryExpected))
                    if (s.setAside > 0) Line("Set aside (yearly bills, savings)", "−" + Money.short(s.setAside))
                    Line("Spent so far", "−" + Money.short(b.spent))
                    Line("Still to pay this month", "−" + Money.short(b.committed))
                    Line("Left", Money.short(b.left), bold = true)
                }
            }
            Gap(64)
        }
        ExtendedFloatingActionButton(
            onClick = { nav.open(Route.Add()) },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Add expense") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

@Composable
private fun Line(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun DayBars(s: HomeState) {
    val days = s.days.takeLast(31)
    val max = (days.maxOfOrNull { it.second } ?: 0.0).coerceAtLeast(s.dayReference).coerceAtLeast(1.0)
    val ok = MaterialTheme.colorScheme.primary
    val near = Warn
    val over = MaterialTheme.colorScheme.error
    val empty = MaterialTheme.colorScheme.surfaceVariant
    Canvas(Modifier.fillMaxWidth().height(64.dp)) {
        val n = days.size
        val gap = 3.dp.toPx()
        val w = ((size.width - gap * (n - 1)) / n).coerceAtLeast(1f)
        days.forEachIndexed { i, (_, v) ->
            val h = if (v <= 0) 3.dp.toPx() else (v / max * size.height).toFloat().coerceAtLeast(3.dp.toPx())
            val c = if (v <= 0) empty else when (Budget.level(v, s.dayReference)) {
                Budget.Level.OVER -> over
                Budget.Level.NEAR -> near
                else -> ok
            }
            drawRoundRect(c, Offset(i * (w + gap), size.height - h), Size(w, h), CornerRadius(4.dp.toPx()))
        }
    }
}

@Composable
private fun UpcomingCard(repo: Repo, items: List<Upcoming>, s: HomeState, cur: String) {
    val scope = rememberCoroutineScope()
    SectionCard {
        Label("Coming up")
        for (u in items) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(u.title, maxLines = 1)
                    val late = u.due.isBefore(s.today)
                    Text(
                        if (late) "Late · ${u.due.format(dayFmt)}" else u.due.format(dayFmt),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (late) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text((if (u.incoming) "+" else "") + Money.short(u.amount), fontWeight = FontWeight.Bold, color = if (u.incoming) Good else MaterialTheme.colorScheme.onSurface)
                if (!u.due.isAfter(s.today.plusDays(3))) {
                    TextButton(onClick = { scope.launch { payUpcoming(repo, u) } }) { Text(if (u.incoming) "Got it" else "Paid") }
                }
            }
        }
        Text("Amounts in $cur", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

suspend fun payUpcoming(repo: Repo, u: Upcoming) {
    val dao = repo.dao
    when (u.kind) {
        "BILL" -> dao.bill(u.id)?.let { repo.payBill(it) }
        "INSTALLMENT" -> dao.installment(u.id)?.let { repo.payInstallment(it) }
        "CIRCLE" -> dao.circle(u.id)?.let { repo.payCircle(it) }
        "LOAN" -> dao.loan(u.id)?.let { repo.loanPayment(it, it.remaining) }
    }
}
