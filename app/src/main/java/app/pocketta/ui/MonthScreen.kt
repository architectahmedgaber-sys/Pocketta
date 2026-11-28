package app.pocketta.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import app.pocketta.share.SummaryImage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pocketta.core.Budget
import app.pocketta.core.CycleMath
import app.pocketta.core.Money
import app.pocketta.data.Prefs
import app.pocketta.data.Repo
import app.pocketta.data.Source
import app.pocketta.data.Txn
import app.pocketta.data.TxType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The Month tab: switch month (and peek ahead), income against commitments, a timeline of the cycle, then spending. */
@Composable
fun MonthScreen(repo: Repo, nav: Nav, prefs: Prefs, modifier: Modifier) {
    // 0 = this month, 1 = last month, -1 = next month (up to 3 ahead).
    var offset by remember { mutableIntStateOf(0) }
    var filterParent by remember { mutableStateOf<Long?>(null) }
    var search by remember { mutableStateOf("") }
    val today = LocalDate.now()
    val context = LocalContext.current
    var cycle = CycleMath.cycleFor(today, prefs.cycleStart)
    if (offset >= 0) repeat(offset) { cycle = cycle.previous(prefs.cycleStart) }
    else repeat(-offset) { cycle = CycleMath.cycleFor(cycle.nextStart, prefs.cycleStart) }
    val bills by remember { repo.dao.billsFlow() }.collectAsState(initial = emptyList())
    val insts by remember { repo.dao.installmentsFlow() }.collectAsState(initial = emptyList())
    val circles by remember { repo.dao.circlesFlow() }.collectAsState(initial = emptyList())
    val loans by remember { repo.dao.loansFlow() }.collectAsState(initial = emptyList())
    val loanInst by remember { repo.dao.loanInstallmentsFlow() }.collectAsState(initial = emptyList())
    val txFlow = remember(cycle.start) { repo.dao.txnsFlow(cycle.start.toEpochDay(), cycle.nextStart.toEpochDay()) }
    val catFlow = remember { repo.dao.categoriesFlow() }
    val txns by txFlow.collectAsState(initial = emptyList())
    val categories by catFlow.collectAsState(initial = emptyList())
    val byId = categories.associateBy { it.id }
    fun topOf(id: Long?): Long? = id?.let { byId[it]?.parentId ?: it }

    val prevCycle = cycle.previous(prefs.cycleStart)
    val prevFlow = remember(cycle.start) { repo.dao.txnsFlow(prevCycle.start.toEpochDay(), prevCycle.nextStart.toEpochDay()) }
    val prevTxns by prevFlow.collectAsState(initial = emptyList())
    val prevPerParent = prevTxns.filter { it.type == TxType.EXPENSE && it.source != Source.PIGGY }.groupBy { topOf(it.categoryId) }.mapValues { e -> e.value.sumOf { it.base } }
    var trend by remember { mutableStateOf<List<Pair<String, Double>>>(emptyList()) }
    LaunchedEffect(cycle.start, txns) {
        val cs = ArrayList<app.pocketta.core.Cycle>()
        var c = cycle
        cs += c
        repeat(5) { c = c.previous(prefs.cycleStart); cs += c }
        trend = cs.reversed().map { x ->
            x.end.format(shortMonthFmt) to repo.dao.txns(x.start.toEpochDay(), x.nextStart.toEpochDay())  .filter { it.type == TxType.EXPENSE && !it.spread && it.source != Source.PIGGY }.sumOf { it.base }
        }
    }
    val saved = txns.filter { it.source == Source.PIGGY }.sumOf { it.base }
    val expenses = txns.filter { it.type == TxType.EXPENSE && it.source != Source.PIGGY }
    val spent = expenses.sumOf { it.base }
    val income = txns.filter { it.type == TxType.INCOME }.sumOf { it.base }
    val perParent = expenses.groupBy { topOf(it.categoryId) }.mapValues { e -> e.value.sumOf { it.base } }
        .entries.sortedByDescending { it.value }

    val shown = txns.filter { t ->
        (filterParent == null || topOf(t.categoryId) == filterParent) &&
            (search.isBlank() || t.note.contains(search, true) || categoryLabel(categories, t.categoryId).contains(search, true))
    }
    val grouped = shown.groupBy { it.epochDay }.entries.sortedByDescending { it.key }
    // Everything of this cycle by date: salary, bills, instalments, gam'eya, loan instalments and loan income.
    val timeline = remember(bills, insts, circles, loans, loanInst, txns, prefs, cycle) {
        val items = buildItems(bills, insts, circles, loans, loanInst, txns, prefs, repo, today, cycle, -offset).toMutableList()
        for (t in txns) if (t.type == TxType.INCOME && t.source == Source.LOAN) {
            items += app.pocketta.core.CItem(app.pocketta.core.CKind.LOAN, t.sourceId, t.note.ifBlank { "Loan" }, "Borrowed", t.base, LocalDate.ofEpochDay(t.epochDay), paid = true, incoming = true)
        }
        items.sortedWith(compareBy({ it.date }, { it.paid }))
    }
    val expectedIncome = timeline.filter { it.incoming }.sumOf { it.amount }
    val commitTotal = timeline.filter { !it.incoming }.sumOf { it.amount }
    val commitLeft = timeline.filter { !it.incoming && !it.paid }.sumOf { it.amount }

    Box(modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { offset++ }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month") }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(cycle.end.format(monthFmt) + " " + cycle.end.year, style = MaterialTheme.typography.titleLarge)
                        Text("${cycle.start.format(dayFmt)} → ${cycle.end.format(dayFmt)}", style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { if (offset > -3) offset-- }, enabled = offset > -3) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
                    }
                    if (spent > 0) {
                        TextButton(onClick = {
                            SummaryImage.share(
                                context,
                                SummaryImage.Data(
                                    month = cycle.end.format(monthFmt) + " " + cycle.end.year,
                                    currency = prefs.baseCurrency,
                                    spent = spent,
                                    income = income,
                                    previousSpent = prevTxns.filter { it.type == TxType.EXPENSE && it.source != Source.PIGGY }.sumOf { it.base },
                                    categories = perParent.map { SummaryImage.Slice(byId[it.key]?.name ?: "No category", it.value) },
                                ),
                                prefs.lang,
                            )
                        }) { Text("Share") }
                    }
                }
            }
            item {
                SectionCard(Modifier.padding(horizontal = 16.dp)) {
                    Label(if (offset < 0) "Planned for this month" else "Income and commitments")
                    Gap(6)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Income", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Big(Money.short(maxOf(income, 0.0) + timeline.filter { it.incoming && !it.paid }.sumOf { it.amount }), Good, size = 20)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Commitments", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Big(Money.short(commitTotal), size = 20)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Still to pay", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Big(Money.short(commitLeft), Warn, size = 20)
                        }
                    }
                }
                Gap()
            }
            if (timeline.isNotEmpty()) item {
                SectionCard(Modifier.padding(horizontal = 16.dp)) {
                    Label("Timeline")
                    Gap(4)
                    for (x in timeline) {
                        val late = !x.paid && !x.incoming && x.date.isBefore(today)
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(x.date.format(dayFmt), Modifier.width(52.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Column(Modifier.weight(1f)) {
                                Text(x.name, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Text(
                                    (if (late) "Late · " else "") + x.sub, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                                    color = if (late) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                (if (x.incoming) "+" else "") + Money.fmt(x.amount), fontWeight = FontWeight.Medium,
                                color = if (x.incoming) Good else if (x.paid) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                            )
                            Text(if (x.paid) "✓" else "○", color = if (x.paid) Good else MaterialTheme.colorScheme.outline)
                        }
                    }
                }
                Gap()
            }
            item {
                Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { nav.open(Route.MonthPlan) }) { Text("Month plan ›") }
                    TextButton(onClick = { nav.open(Route.Goals) }) { Text("Goals ›") }
                    TextButton(onClick = { nav.open(Route.Piggy) }) { Text("Piggy bank ›") }
                }
            }
            item { Column {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionCard(Modifier.weight(1f)) {
                        Label("Spent")
                        Big(Money.short(spent), size = 22)
                    }
                    SectionCard(Modifier.weight(1f)) {
                        Label("Income")
                        Big(Money.short(income), Good, size = 22)
                    }
                }
                if (saved > 0) {
                    Gap()
                    SectionCard(Modifier.padding(horizontal = 16.dp)) {
                        Label("Put in piggy bank")
                        Big("🐷 " + Money.short(saved), PiggyColor, size = 22)
                        Text("Counted against what is left, but not as everyday spending.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Gap()
            } }
            if (perParent.isNotEmpty()) {
                item { Column {
                    DonutCard(perParent.map { DonutItem(byId[it.key]?.name ?: "No category", it.value, byId[it.key]?.monthlyLimit ?: 0.0) }, spent, Modifier.padding(horizontal = 16.dp))
                    Gap()
                    val sums = expenses.groupBy { it.epochDay.toLong() }.mapValues { e -> e.value.sumOf { it.base } }
                    DaysCard((cycle.start.toEpochDay() until cycle.nextStart.toEpochDay()).map { d -> sums[d] ?: 0.0 }, Modifier.padding(horizontal = 16.dp))
                    Gap()
                    CompareCard(perParent.take(5).map { Triple(byId[it.key]?.name ?: "No category", it.value, prevPerParent[it.key] ?: 0.0) }, Modifier.padding(horizontal = 16.dp))
                    Gap()
                    TrendCard(trend, Modifier.padding(horizontal = 16.dp))
                    Gap()
                } }
            }
            if (perParent.isNotEmpty()) {
                item { Column {
                    SectionCard(Modifier.padding(horizontal = 16.dp)) {
                        Label("By category · tap to filter")
                        Gap(8)
                        for ((pid, total) in perParent) {
                            val c = pid?.let { byId[it] }
                            val limit = c?.monthlyLimit ?: 0.0
                            val selected = filterParent == pid
                            Column(
                                Modifier.fillMaxWidth().clickable { filterParent = if (selected) null else pid }.padding(vertical = 6.dp)
                            ) {
                                Row {
                                    Text(
                                        c?.name ?: "No category", Modifier.weight(1f),
                                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        if (limit > 0) "${Money.short(total)} / ${Money.short(limit)}" else "${Money.short(total)} · ${if (spent > 0) (total / spent * 100).toInt() else 0}%",
                                        fontWeight = FontWeight.Medium,
                                    )
                                    if (pid != null) TextButton(onClick = { nav.open(Route.CategoryMonth(categoryId = pid)) }) { Text("Details ›") }
                                }
                                val frac = if (limit > 0) (total / limit).toFloat() else if (spent > 0) (total / spent).toFloat() else 0f
                                val color = when (Budget.level(total, limit)) {
                                    Budget.Level.OVER -> MaterialTheme.colorScheme.error
                                    Budget.Level.NEAR -> Warn
                                    else -> MaterialTheme.colorScheme.primary
                                }
                                Gap(4)
                                Progress(frac, color)
                            }
                        }
                    }
                    Gap()
                } }
            }
            item { Column {
                OutlinedTextField(
                    value = search, onValueChange = { search = it }, singleLine = true,
                    label = { Text("Search notes and categories") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                Gap(4)
            } }
            if (grouped.isEmpty()) {
                item { Text("Nothing here yet.", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            for ((day, list) in grouped) {
                item(key = "h$day") {
                    Text(
                        dayTitle(LocalDate.ofEpochDay(day), today) + " · " + Money.short(list.filter { it.type == TxType.EXPENSE }.sumOf { it.base }),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 2.dp),
                    )
                }
                items(list, key = { it.id }) { t -> TxnRow(t, categories) { nav.open(Route.Add(txnId = t.id)) } }
            }
            item { Gap(16) }
        }
    }
}

private val weekdayFmt = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)

/** Purple for money put in the piggy bank. */
val PiggyColor = androidx.compose.ui.graphics.Color(0xFF6B4FBB)

fun dayTitle(d: LocalDate, today: LocalDate): String = when (d) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> d.format(weekdayFmt)
}

@Composable
fun TxnRow(t: Txn, categories: List<app.pocketta.data.Category>, onClick: () -> Unit) {
    val title = (if (t.source == Source.PIGGY) "🐷 " else "") + t.note.ifBlank { categories.firstOrNull { it.id == t.categoryId }?.name ?: if (t.type == TxType.TRANSFER) "Transfer" else "—" }
    val sub = when (t.type) {
        TxType.TRANSFER -> "Transfer"
        else -> categoryLabel(categories, t.categoryId) + t.tagList.joinToString("") { " · #$it" } +
            listOf(
                when (t.need) { "NEED" -> "Need"; "WANT" -> "Want"; else -> "" },
                when (t.how) { "CASH" -> "Cash"; "CARD" -> "Card"; "WALLET" -> "Wallet"; else -> "" },
                t.place,
            ).filter { it.isNotBlank() }.joinToString("") { " · $it" }
    }
    ListRow(title, sub, icon = categories.firstOrNull { it.id == t.categoryId }?.name ?: title, onClick = onClick) {
        val sign = when (t.type) { TxType.INCOME -> "+"; TxType.EXPENSE -> "−"; else -> "" }
        val text = sign + Money.fmt(t.amount) + if (t.currency != Money.currency) " ${t.currency}" else ""
        Text(
            text, fontWeight = FontWeight.Medium,
            color = if (t.source == Source.PIGGY) PiggyColor else when (t.type) { TxType.INCOME -> Good; TxType.TRANSFER -> MaterialTheme.colorScheme.onSurfaceVariant; else -> MaterialTheme.colorScheme.onSurface },
        )
    }
}
