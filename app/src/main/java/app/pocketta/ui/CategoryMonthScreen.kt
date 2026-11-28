package app.pocketta.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import app.pocketta.core.Money
import app.pocketta.data.Category
import app.pocketta.data.Prefs
import app.pocketta.data.Repo
import app.pocketta.data.Txn
import java.time.LocalDate

/** One month of one category (or tag): the total against last month, every day, what is inside it, and all its spending. */
@Composable
fun CategoryMonthScreen(repo: Repo, nav: Nav, prefs: Prefs, route: Route.CategoryMonth) {
    val version by repo.version.collectAsState()
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var tags by remember { mutableStateOf<List<String>>(emptyList()) }
    var categoryId by remember { mutableStateOf(route.categoryId) }
    var tag by remember { mutableStateOf(route.tag) }
    var offset by remember { mutableIntStateOf(0) }
    var data by remember { mutableStateOf<Repo.CategoryMonth?>(null) }
    LaunchedEffect(version) {
        categories = repo.dao.categories()
        tags = repo.allTags()
    }
    val tops = categories.filter { it.kind == app.pocketta.data.TxType.EXPENSE && it.parentId == null && !it.archived && it.name != "Piggy bank" }.sortedBy { it.sort }
    val selectedId = if (categoryId == 0L && tag.isEmpty()) tops.firstOrNull()?.id ?: 0L else categoryId
    LaunchedEffect(version, selectedId, tag, offset) {
        data = if (tag.isNotEmpty()) repo.categoryMonth(null, tag, offset) else if (selectedId != 0L) repo.categoryMonth(selectedId, null, offset) else null
    }
    val byId = categories.associateBy { it.id }
    val d = data
    val name = if (tag.isNotEmpty()) "#$tag" else byId[selectedId]?.name ?: ""

    Scaffold(topBar = { BackBar("Category by month", { nav.back() }) }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (d != null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { offset++ }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month") }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(d.cycle.end.format(monthFmt) + " " + d.cycle.end.year, style = MaterialTheme.typography.titleLarge)
                        Text("${d.cycle.start.format(dayFmt)} → ${d.cycle.end.format(dayFmt)}", style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { if (offset > 0) offset-- }, enabled = offset > 0) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
                    }
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (c in tops) FilterChip(selected = tag.isEmpty() && selectedId == c.id, onClick = { categoryId = c.id; tag = "" }, label = { Text(c.name) })
                for (g in tags) FilterChip(selected = tag == g, onClick = { tag = g; categoryId = 0L }, label = { Text("#$g") })
            }
            if (d == null) {
                Text("No categories yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                SectionCard(container = MaterialTheme.colorScheme.primaryContainer) {
                    Label("Spent")
                    Text(name, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.titleMedium)
                    Big(Money.short(d.total) + " " + prefs.baseCurrency, size = 30)
                    Text(
                        if (d.previousTotal > 0) "Last month " + Money.short(d.previousTotal) else "Nothing last month",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (d.previousTotal > 0) {
                        val pct = Math.round((d.total - d.previousTotal) / d.previousTotal * 100).toInt()
                        Text(
                            if (pct > 0) "▲ $pct% vs last month" else if (pct < 0) "▼ ${-pct}% vs last month" else "＝ same as last month",
                            fontWeight = FontWeight.Medium,
                            color = if (pct > 0) MaterialTheme.colorScheme.error else Good,
                        )
                    }
                    if (d.limit > 0) {
                        Gap(6)
                        Text("Monthly limit " + Money.short(d.limit), style = MaterialTheme.typography.bodySmall)
                        Text(if (d.total > d.limit) "Over by " + Money.short(d.total - d.limit) else "Left " + Money.short(d.limit - d.total), style = MaterialTheme.typography.bodySmall)
                        val color = when (Budget.level(d.total, d.limit)) {
                            Budget.Level.OVER -> MaterialTheme.colorScheme.error
                            Budget.Level.NEAR -> Warn
                            else -> MaterialTheme.colorScheme.primary
                        }
                        Gap(4)
                        Progress((d.total / d.limit).toFloat().coerceIn(0f, 1f), color)
                    }
                }
                if (d.txns.isEmpty()) {
                    Text("Nothing spent here this month.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    val perDay = d.txns.groupBy { it.epochDay }.mapValues { e -> e.value.sumOf { it.base } }
                    DaysCard((d.cycle.start.toEpochDay() until d.cycle.nextStart.toEpochDay()).map { day -> perDay[day] ?: 0.0 })
                    perDay.entries.maxByOrNull { it.value }?.let { top ->
                        Text("Most expensive day", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(LocalDate.ofEpochDay(top.key).format(dayFmt) + " · " + Money.short(top.value) + " " + prefs.baseCurrency, fontWeight = FontWeight.Medium)
                    }
                    if (tag.isEmpty()) {
                        val subs = d.txns.groupBy { t -> byId[t.categoryId]?.let { c -> if (c.parentId != null) c.name else "Other" } ?: "Other" }
                            .mapValues { e -> e.value.sumOf { it.base } }.entries.sortedByDescending { it.value }
                        if (subs.size > 1) {
                            SectionCard {
                                Label("Inside it")
                                Gap(6)
                                for ((k, v) in subs) {
                                    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(k, Modifier.weight(1f), maxLines = 1)
                                        Text(Money.short(v) + " · " + (if (d.total > 0) (v / d.total * 100).toInt() else 0) + "%", fontWeight = FontWeight.Medium)
                                    }
                                    Progress((if (d.total > 0) v / d.total else 0.0).toFloat())
                                }
                            }
                        }
                    }
                    Label("Biggest three")
                    for (x in d.txns.sortedByDescending { it.base }.take(3)) TxnRow(x, categories) { nav.open(Route.Add(txnId = x.id)) }
                    Label("All of it, newest first")
                    for (x in d.txns) TxnRow(x, categories) { nav.open(Route.Add(txnId = x.id)) }
                }
            }
        }
    }
}
