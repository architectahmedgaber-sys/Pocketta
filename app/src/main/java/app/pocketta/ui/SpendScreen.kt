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
import androidx.compose.material3.ExtendedFloatingActionButton
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
import app.pocketta.data.Txn
import app.pocketta.data.TxType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SpendScreen(repo: Repo, nav: Nav, prefs: Prefs, modifier: Modifier) {
    var offset by remember { mutableIntStateOf(0) }
    var filterParent by remember { mutableStateOf<Long?>(null) }
    var search by remember { mutableStateOf("") }
    val today = LocalDate.now()
    val context = LocalContext.current
    var cycle = CycleMath.cycleFor(today, prefs.cycleStart)
    repeat(offset) { cycle = cycle.previous(prefs.cycleStart) }
    val txFlow = remember(cycle.start) { repo.dao.txnsFlow(cycle.start.toEpochDay(), cycle.nextStart.toEpochDay()) }
    val catFlow = remember { repo.dao.categoriesFlow() }
    val txns by txFlow.collectAsState(initial = emptyList())
    val categories by catFlow.collectAsState(initial = emptyList())
    val byId = categories.associateBy { it.id }
    fun topOf(id: Long?): Long? = id?.let { byId[it]?.parentId ?: it }

    val prevCycle = cycle.previous(prefs.cycleStart)
    val prevFlow = remember(cycle.start) { repo.dao.txnsFlow(prevCycle.start.toEpochDay(), prevCycle.nextStart.toEpochDay()) }
    val prevTxns by prevFlow.collectAsState(initial = emptyList())
    val prevPerParent = prevTxns.filter { it.type == TxType.EXPENSE }.groupBy { topOf(it.categoryId) }.mapValues { e -> e.value.sumOf { it.base } }
    var trend by remember { mutableStateOf<List<Pair<String, Double>>>(emptyList()) }
    LaunchedEffect(cycle.start, txns) {
        val cs = ArrayList<app.pocketta.core.Cycle>()
        var c = cycle
        cs += c
        repeat(5) { c = c.previous(prefs.cycleStart); cs += c }
        trend = cs.reversed().map { x ->
            x.end.format(shortMonthFmt) to repo.dao.txns(x.start.toEpochDay(), x.nextStart.toEpochDay()).filter { it.type == TxType.EXPENSE && !it.spread }.sumOf { it.base }
        }
    }
    val expenses = txns.filter { it.type == TxType.EXPENSE }
    val spent = expenses.sumOf { it.base }
    val income = txns.filter { it.type == TxType.INCOME }.sumOf { it.base }
    val perParent = expenses.groupBy { topOf(it.categoryId) }.mapValues { e -> e.value.sumOf { it.base } }
        .entries.sortedByDescending { it.value }

    val shown = txns.filter { t ->
        (filterParent == null || topOf(t.categoryId) == filterParent) &&
            (search.isBlank() || t.note.contains(search, true) || categoryLabel(categories, t.categoryId).contains(search, true))
    }
    val grouped = shown.groupBy { it.epochDay }.entries.sortedByDescending { it.key }

    Box(modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { offset++ }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month") }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(cycle.end.format(monthFmt) + " " + cycle.end.year, style = MaterialTheme.typography.titleLarge)
                        Text("${cycle.start.format(dayFmt)} → ${cycle.end.format(dayFmt)}", style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { if (offset > 0) offset-- }, enabled = offset > 0) {
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
                                    previousSpent = prevTxns.filter { it.type == TxType.EXPENSE }.sumOf { it.base },
                                    categories = perParent.map { SummaryImage.Slice(byId[it.key]?.name ?: "No category", it.value) },
                                ),
                                prefs.lang,
                            )
                        }) { Text("Share") }
                    }
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
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        if (limit > 0) "${Money.short(total)} / ${Money.short(limit)}" else "${Money.short(total)} · ${if (spent > 0) (total / spent * 100).toInt() else 0}%",
                                        fontWeight = FontWeight.SemiBold,
                                    )
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
            item { Gap(88) }
        }
        ExtendedFloatingActionButton(
            onClick = { nav.open(Route.Add()) },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Add") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

private val weekdayFmt = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)

fun dayTitle(d: LocalDate, today: LocalDate): String = when (d) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> d.format(weekdayFmt)
}

@Composable
fun TxnRow(t: Txn, categories: List<app.pocketta.data.Category>, onClick: () -> Unit) {
    val title = t.note.ifBlank { categories.firstOrNull { it.id == t.categoryId }?.name ?: if (t.type == TxType.TRANSFER) "Transfer" else "—" }
    val sub = when (t.type) {
        TxType.TRANSFER -> "Transfer"
        else -> categoryLabel(categories, t.categoryId) + t.tagList.joinToString("") { " · #$it" }
    }
    ListRow(title, sub, icon = categories.firstOrNull { it.id == t.categoryId }?.name ?: title, onClick = onClick) {
        val sign = when (t.type) { TxType.INCOME -> "+"; TxType.EXPENSE -> "−"; else -> "" }
        val text = sign + Money.fmt(t.amount) + if (t.currency != Money.currency) " ${t.currency}" else ""
        Text(
            text, fontWeight = FontWeight.SemiBold,
            color = when (t.type) { TxType.INCOME -> Good; TxType.TRANSFER -> MaterialTheme.colorScheme.onSurfaceVariant; else -> MaterialTheme.colorScheme.onSurface },
        )
    }
}
