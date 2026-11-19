package app.pocketta.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pocketta.core.Money
import app.pocketta.core.MonthValues
import app.pocketta.core.PlanMonth
import app.pocketta.core.Ym
import app.pocketta.data.Prefs
import app.pocketta.data.Repo
import kotlinx.coroutines.launch
import java.time.LocalDate

private const val PAST = 3
private const val FUTURE = 24

/** The month-by-month plan, like a spreadsheet: income, commitments, living cost and what is left, for past and coming months. */
@Composable
fun MonthPlanScreen(repo: Repo, nav: Nav, prefs: Prefs) {
    val scope = rememberCoroutineScope()
    val version by repo.version.collectAsState()
    var months by remember { mutableStateOf<List<PlanMonth>>(emptyList()) }
    LaunchedEffect(version, prefs) { months = repo.monthPlan(PAST, FUTURE) }
    val nowYm = Ym.of(LocalDate.now())
    val cur = prefs.baseCurrency
    var open by remember { mutableStateOf<Int?>(null) }
    var livingFor by remember { mutableStateOf<Int?>(null) }
    var raiseFor by remember { mutableStateOf<Int?>(null) }
    var livingBaseText by remember(prefs.livingBase) { mutableStateOf(numText(prefs.livingBase)) }
    val listState = rememberLazyListState()
    var scrolled by remember { mutableStateOf(false) }
    LaunchedEffect(months.size) {
        if (months.isNotEmpty() && !scrolled) { scrolled = true; listState.scrollToItem(1 + PAST) }
    }

    Scaffold(topBar = { BackBar("Month plan", { nav.back() }) }) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(), state = listState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SectionCard {
                    Label("Living cost in a normal month")
                    Text("Food, transport, everyday things. Used for every coming month.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Gap(8)
                    NumberField("Living cost", livingBaseText, { livingBaseText = it }, suffix = cur)
                    Gap(6)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { scope.launch { repo.set("livingBase", parseNumber(livingBaseText) ?: 0.0) } }) { Text("Save") }
                        Gap(8)
                        OutlinedButton(onClick = { raiseFor = nowYm }, modifier = Modifier.padding(start = 8.dp)) { Text("Salary raise") }
                    }
                    if (prefs.salarySteps.isNotEmpty()) {
                        Gap(8)
                        for ((ym, v) in prefs.salarySteps.toSortedMap()) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("From " + Ym.toMonth(ym).atDay(1).format(monthYearFmt) + ": " + Money.short(v) + " " + cur, Modifier.weight(1f))
                                TextButton(onClick = { raiseFor = ym }) { Text("Edit") }
                            }
                        }
                    }
                }
            }
            itemsIndexed(months, key = { _, m -> m.ym }) { _, m ->
                MonthCard(m, cur, isNow = m.ym == nowYm, expanded = open == m.ym, onToggle = { open = if (open == m.ym) null else m.ym },
                    onLiving = { livingFor = m.ym }, onRaise = { raiseFor = m.ym })
            }
        }
    }

    livingFor?.let { ym ->
        var t by remember(ym) { mutableStateOf(numText(prefs.livingOverrides[ym] ?: prefs.livingBase)) }
        val v = parseNumber(t)
        FormDialog(
            "Living cost · " + Ym.toMonth(ym).atDay(1).format(monthYearFmt), onDismiss = { livingFor = null },
            saveEnabled = v != null && v >= 0,
            onDelete = if (prefs.livingOverrides.containsKey(ym)) ({
                scope.launch { repo.set("livingOverrides", MonthValues.format(prefs.livingOverrides - ym)) }; livingFor = null
            }) else null,
            onSave = {
                scope.launch { repo.set("livingOverrides", MonthValues.format(prefs.livingOverrides + (ym to (v ?: 0.0)))) }
                livingFor = null
            },
        ) {
            Text("Only for this month. Other months keep the normal living cost.", style = MaterialTheme.typography.bodySmall)
            NumberField("Living cost", t, { t = it }, suffix = cur)
        }
    }
    raiseFor?.let { ym0 ->
        var ym by remember(ym0) { mutableStateOf(ym0) }
        var t by remember(ym0) { mutableStateOf(numText(prefs.salarySteps[ym0] ?: MonthValues.stepAt(prefs.salarySteps, ym0) ?: prefs.salary)) }
        val v = parseNumber(t)
        FormDialog(
            "Salary from a month", onDismiss = { raiseFor = null },
            saveEnabled = v != null && v > 0,
            onDelete = if (prefs.salarySteps.containsKey(ym0)) ({
                scope.launch { repo.set("salarySteps", MonthValues.format(prefs.salarySteps - ym0)) }; raiseFor = null
            }) else null,
            onSave = {
                scope.launch {
                    val base = if (ym != ym0) prefs.salarySteps - ym0 else prefs.salarySteps
                    repo.set("salarySteps", MonthValues.format(base + (ym to (v ?: 0.0))))
                }
                raiseFor = null
            },
        ) {
            Text("From this month on, the salary is this amount, until the next raise.", style = MaterialTheme.typography.bodySmall)
            DateField("Month", Ym.start(ym), { if (it != null) ym = Ym.of(it) })
            NumberField("Salary", t, { t = it }, suffix = cur)
        }
    }
}

@Composable
private fun MonthCard(m: PlanMonth, cur: String, isNow: Boolean, expanded: Boolean, onToggle: () -> Unit, onLiving: () -> Unit, onRaise: () -> Unit) {
    val title = Ym.toMonth(m.ym).atDay(1).format(monthYearFmt)
    val bad = m.remaining < 0
    SectionCard(
        onClick = onToggle,
        container = if (isNow) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(
                    (if (m.actual) "Actual" else if (isNow) "This month" else "Expected") +
                        " · In " + Money.short(m.income) + " · Out " + Money.short(m.committed + m.living),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Money.short(m.remaining), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = if (bad) MaterialTheme.colorScheme.error else Good)
                Text("Total " + Money.short(m.cumulative), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (expanded) {
            Gap(8)
            Divider()
            DetailRow("Salary / income", m.salary, cur, plus = true)
            DetailRow("Gam'eya payout", m.payout, cur, plus = true)
            DetailRow("Loans coming back", m.loansIn, cur, plus = true)
            DetailRow("Bills and subscriptions", m.bills, cur)
            DetailRow("Installments", m.installments, cur)
            DetailRow("Credit cards", m.cards, cur)
            DetailRow("Gam'eya shares", m.circleShares, cur)
            DetailRow("Loans to pay back", m.loansOut, cur)
            DetailRow("Living", m.living, cur)
            if (m.lines.isNotEmpty()) {
                Gap(6)
                Text(m.lines.joinToString(" · ") { it.name + " " + Money.short(it.amount) }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!m.actual) {
                Gap(6)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onLiving) { Text("Change living cost") }
                    OutlinedButton(onClick = onRaise) { Text("Salary from here") }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, v: Double, cur: String, plus: Boolean = false) {
    if (v == 0.0) return
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, Modifier.weight(1f))
        Text((if (plus) "+ " else "− ") + Money.short(v) + " " + cur, color = if (plus) Good else Color.Unspecified)
    }
}
