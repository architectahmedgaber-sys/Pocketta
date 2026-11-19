package app.pocketta.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pocketta.core.Money
import app.pocketta.core.Planning
import app.pocketta.core.WealthLog
import app.pocketta.data.PlanData
import app.pocketta.data.Prefs
import app.pocketta.data.Repo
import app.pocketta.data.WealthView
import java.time.LocalDate

/** Categories that are heading higher than your usual (average of the last 3 months). */
@Composable
fun RisesCard(d: PlanData) {
    val rises = d.rises.take(3)
    if (rises.isEmpty()) return
    SectionCard(container = MaterialTheme.colorScheme.errorContainer) {
        Label("Prices are rising")
        Gap(4)
        for (r in rises) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text("▲ " + r.percent + "% vs your usual", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
        }
        Gap(2)
        Text("At this pace, compared with your average of the last 3 months", style = MaterialTheme.typography.bodySmall)
    }
}

/** A small card on Today: everything you own in one number. */
@Composable
fun WealthCard(repo: Repo, nav: Nav, prefs: Prefs) {
    val version by repo.version.collectAsState()
    var w by remember { mutableStateOf<WealthView?>(null) }
    LaunchedEffect(version) { w = repo.wealth(); repo.recordWealth() }
    val v = w ?: return
    SectionCard(onClick = { nav.open(Route.Wealth) }) {
        Label("Everything I own")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Big(Money.short(v.total) + " " + prefs.baseCurrency, size = 24)
            Row(Modifier.weight(1f)) {}
            Text("›", color = MaterialTheme.colorScheme.outline)
        }
        val usd = v.dollars
        if (usd != null) Text("≈ $" + Money.short(usd), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun WealthScreen(repo: Repo, nav: Nav, prefs: Prefs) {
    val version by repo.version.collectAsState()
    var w by remember { mutableStateOf<WealthView?>(null) }
    LaunchedEffect(version) { w = repo.wealth() }
    val cur = prefs.baseCurrency
    var editBalance by remember { mutableStateOf(false) }
    if (editBalance) BalanceDialog(repo, prefs, w?.cash ?: 0.0) { editBalance = false }
    Scaffold(topBar = { BackBar("Everything I own", { nav.back() }) }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val v = w ?: return@Column
            SectionCard {
                Label("Total")
                Big(Money.short(v.total) + " " + cur, size = 32)
                Gap(8)
                WLine("Money I have", Money.short(v.cash) + " " + cur)
                if (v.salaryComing > 0) WLine("Salary still coming (not counted above)", Money.short(v.salaryComing) + " " + cur)
                if (v.cash < 0) Text("Your money is below zero because Pocketta only counts what you typed. Tap Change my balance and type what you really have.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                androidx.compose.material3.OutlinedButton(onClick = { editBalance = true }) { Text("Change my balance") }
                WLine("Gold I bought, at today's price", Money.short(v.gold) + " " + cur)
                val usd = v.dollars
                if (usd != null) WLine("In dollars", "$" + Money.short(usd))
                val g = v.goldGrams
                if (g != null) WLine("In gold (21k)", Money.fmt(Math.round(g * 10) / 10.0) + " g")
                if (usd == null && g == null) {
                    Gap(4)
                    Text("Turn on today's rates in Currencies & gold to see dollars and gold", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SectionCard {
                Label("Over time")
                Gap(6)
                if (v.history.size < 2) {
                    Text("Come back in a few days. Pocketta saves your total once a day and draws the line here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Sparkline(v.history.map { it.total })
                    Gap(8)
                    val today = LocalDate.now().toEpochDay()
                    for ((label, days) in listOf("Last 7 days" to 7, "Last 30 days" to 30, "Last 90 days" to 90)) {
                        val ch = WealthLog.changeSince(v.history, today, days) ?: continue
                        WLine(label, Money.signed(ch) + " " + cur, color = if (ch >= 0) Good else MaterialTheme.colorScheme.error)
                    }
                }
            }
            Gap(24)
        }
    }
}

@Composable
private fun WLine(label: String, value: String, color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified) {
    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Sparkline(values: List<Double>) {
    val line = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val lo = values.min()
        val hi = values.max()
        val span = if (hi - lo < 1e-9) 1.0 else hi - lo
        val stepX = size.width / (values.size - 1).coerceAtLeast(1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = (size.height - 8.dp.toPx()) - ((v - lo) / span * (size.height - 16.dp.toPx())).toFloat() + 4.dp.toPx()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, line, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        val last = values.last()
        drawCircle(line, 5.dp.toPx(), Offset(size.width, (size.height - 8.dp.toPx()) - ((last - lo) / span * (size.height - 16.dp.toPx())).toFloat() + 4.dp.toPx()))
    }
}
