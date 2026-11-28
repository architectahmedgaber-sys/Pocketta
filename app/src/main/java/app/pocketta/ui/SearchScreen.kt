package app.pocketta.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pocketta.core.Money
import app.pocketta.core.TxnFilter
import app.pocketta.data.Category
import app.pocketta.data.Prefs
import app.pocketta.data.Repo
import app.pocketta.data.TxType
import java.time.LocalDate

private const val CAP = 300

/** Which filter dialog is open. */
private enum class Dlg { NONE, CATEGORY, AMOUNT, DATE, NEED, TAG }

/** Search every transaction by text or amount, and narrow them down with filters. Tap a row to edit it. */
@Composable
fun SearchScreen(repo: Repo, nav: Nav, prefs: Prefs) {
    val version by repo.version.collectAsState()
    val all by remember { repo.dao.allTxnsFlow() }.collectAsState(initial = emptyList())
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var tags by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(version) {
        categories = repo.dao.categories()
        tags = repo.allTags()
    }

    var q by remember { mutableStateOf("") }
    var types by remember { mutableStateOf<Set<String>>(emptySet()) }
    var catIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var minText by remember { mutableStateOf("") }
    var maxText by remember { mutableStateOf("") }
    var from by remember { mutableStateOf<LocalDate?>(null) }
    var to by remember { mutableStateOf<LocalDate?>(null) }
    var need by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf("") }
    var dlg by remember { mutableStateOf(Dlg.NONE) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val minV = parseNumber(minText)
    val maxV = parseNumber(maxText)
    val filter = TxnFilter.F(
        q = q, types = types, catIds = catIds, min = minV, max = maxV,
        from = from?.toEpochDay(), to = to?.toEpochDay(), need = need, tag = tag,
    )
    val byId = remember(all) { all.associateBy { it.id } }
    val result = remember(all, filter, categories) {
        val ts = all.map { TxnFilter.T(it.id, it.type, it.base, it.note, it.categoryId, it.walletId, it.epochDay, it.need, it.tagList) }
        TxnFilter.apply(ts, filter) { id -> categoryLabel(categories, id) }
    }
    val shown = result.list.take(CAP)
    val anyFilter = types.isNotEmpty() || catIds.isNotEmpty() || minV != null || maxV != null || from != null || to != null || need.isNotEmpty() || tag.isNotEmpty()
    val cur = prefs.baseCurrency

    Scaffold(topBar = { BackBar("Search", { nav.back() }) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
            item {
                OutlinedTextField(
                    value = q, onValueChange = { q = it }, singleLine = true,
                    placeholder = { Text("Search notes, categories, tags or amounts") },
                    shape = FieldShape,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).focusRequester(focus),
                )
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TypeChip("Expense", TxType.EXPENSE in types) { types = if (TxType.EXPENSE in types) types - TxType.EXPENSE else types + TxType.EXPENSE }
                    TypeChip("Income", TxType.INCOME in types) { types = if (TxType.INCOME in types) types - TxType.INCOME else types + TxType.INCOME }
                    FilterTag("Category", if (catIds.isEmpty()) null else catIds.size.toString(), { dlg = Dlg.CATEGORY }, { catIds = emptySet() })
                    FilterTag(
                        "Amount",
                        if (minV == null && maxV == null) null else "${minV?.let { TxnFilter.amountText(it) } ?: "…"} – ${maxV?.let { TxnFilter.amountText(it) } ?: "…"}",
                        { dlg = Dlg.AMOUNT }, { minText = ""; maxText = "" },
                    )
                    FilterTag(
                        "Date",
                        if (from == null && to == null) null else "${from?.format(dayFmt) ?: "…"} – ${to?.format(dayFmt) ?: "…"}",
                        { dlg = Dlg.DATE }, { from = null; to = null },
                    )
                    FilterTag("Need/Want", if (need.isEmpty()) null else if (need == "NEED") "Need" else "Want", { dlg = Dlg.NEED }, { need = "" })
                    FilterTag("Tag", if (tag.isEmpty()) null else "#$tag", { dlg = Dlg.TAG }, { tag = "" })
                    if (anyFilter) TextButton(onClick = {
                        types = emptySet(); catIds = emptySet(); minText = ""; maxText = ""; from = null; to = null; need = ""; tag = ""
                    }) { Text("Clear all") }
                }
            }
            if (result.list.isNotEmpty()) item {
                SectionCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), container = MaterialTheme.colorScheme.tertiaryContainer) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${result.list.size} results", Modifier.weight(1f))
                        Text("Total spent ${Money.short(result.total)} $cur", fontWeight = FontWeight.Medium)
                    }
                }
            }
            if (result.list.isEmpty() && (q.isNotBlank() || anyFilter)) {
                item { Text("Nothing found", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(shown, key = { it.id }) { t ->
                byId[t.id]?.let { tx -> TxnRow(tx, categories) { nav.open(Route.Add(txnId = tx.id)) } }
            }
            if (result.list.size > CAP) item {
                Text("Showing the first 300", Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    when (dlg) {
        Dlg.NONE -> {}
        Dlg.CATEGORY -> {
            val list = categories.filter { !it.archived }.sortedWith(compareBy({ it.kind }, { it.parentId ?: it.id }, { if (it.parentId == null) 0 else 1 }, { it.sort }))
            AlertDialog(
                onDismissRequest = { dlg = Dlg.NONE },
                title = { Text("Category") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        for (c in list) {
                            val on = c.id in catIds
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    // A top-level category also picks the ones inside it.
                                    val group = setOf(c.id) + (if (c.parentId == null) categories.filter { it.parentId == c.id }.map { it.id } else emptyList())
                                    catIds = if (on) catIds - group else catIds + group
                                }.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = on, onCheckedChange = null)
                                Text(categoryLabel(categories, c.id), Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { dlg = Dlg.NONE }) { Text("OK") } },
                dismissButton = { TextButton(onClick = { catIds = emptySet(); dlg = Dlg.NONE }) { Text("Clear") } },
            )
        }
        Dlg.AMOUNT -> AlertDialog(
            onDismissRequest = { dlg = Dlg.NONE },
            title = { Text("Amount") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("From", minText, { minText = it }, suffix = cur)
                    NumberField("To", maxText, { maxText = it }, suffix = cur)
                }
            },
            confirmButton = { TextButton(onClick = { dlg = Dlg.NONE }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { minText = ""; maxText = ""; dlg = Dlg.NONE }) { Text("Clear") } },
        )
        Dlg.DATE -> AlertDialog(
            onDismissRequest = { dlg = Dlg.NONE },
            title = { Text("Date") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateField("From", from, { from = it }, allowClear = true)
                    DateField("To", to, { to = it }, allowClear = true)
                }
            },
            confirmButton = { TextButton(onClick = { dlg = Dlg.NONE }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { from = null; to = null; dlg = Dlg.NONE }) { Text("Clear") } },
        )
        Dlg.NEED -> AlertDialog(
            onDismissRequest = { dlg = Dlg.NONE },
            title = { Text("Need/Want") },
            text = {
                Choices(listOf("", "NEED", "WANT"), need, { when (it) { "NEED" -> "Need"; "WANT" -> "Want"; else -> "Either" } }, { need = it; dlg = Dlg.NONE })
            },
            confirmButton = { TextButton(onClick = { dlg = Dlg.NONE }) { Text("OK") } },
        )
        Dlg.TAG -> AlertDialog(
            onDismissRequest = { dlg = Dlg.NONE },
            title = { Text("Tag") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (tags.isEmpty()) Text("Nothing found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Choices(tags, tag.takeIf { it.isNotEmpty() }, { "#$it" }, { tag = if (tag == it) "" else it; dlg = Dlg.NONE })
                }
            },
            confirmButton = { TextButton(onClick = { dlg = Dlg.NONE }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { tag = ""; dlg = Dlg.NONE }) { Text("Clear") } },
        )
    }
}

@Composable
private fun TypeChip(label: String, on: Boolean, onClick: () -> Unit) {
    FilterChip(selected = on, onClick = onClick, label = { Text(label) })
}

/** A filter chip that opens its dialog; when a value is set it shows it and a small clear mark. */
@Composable
private fun FilterTag(label: String, value: String?, onOpen: () -> Unit, onClear: () -> Unit) {
    FilterChip(
        selected = value != null,
        onClick = onOpen,
        label = { Text(if (value == null) label else "$label: $value") },
        trailingIcon = if (value == null) null else ({
            Icon(Icons.Filled.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp).clickable { onClear() })
        }),
    )
}
