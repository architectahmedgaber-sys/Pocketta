package app.pocketta.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pocketta.core.Money
import app.pocketta.data.Prefs
import app.pocketta.data.Repo
import app.pocketta.data.TagStat
import app.pocketta.data.Txn
import kotlinx.coroutines.launch

/** Tags (projects): every tag with what was spent under it, across all categories. */
@Composable
fun TagsScreen(repo: Repo, nav: Nav, prefs: Prefs) {
    val version by repo.version.collectAsState()
    var stats by remember { mutableStateOf<List<TagStat>>(emptyList()) }
    LaunchedEffect(version) { stats = repo.tagStats() }
    val max = stats.maxOfOrNull { it.spent }?.coerceAtLeast(1.0) ?: 1.0
    Scaffold(topBar = { BackBar("Tags", { nav.back() }) }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Add a tag to any spending or income (like a wedding or a trip) and see its total here, across all categories.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (stats.isEmpty()) Text("No tags yet. When you add an expense, write a tag in the Tags box.")
            for (t in stats) {
                SectionCard(onClick = { nav.open(Route.TagDetail(t.name)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("#" + t.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(Money.short(t.spent) + " " + prefs.baseCurrency, fontWeight = FontWeight.Bold)
                    }
                    Gap(6)
                    Progress((t.spent / max).toFloat())
                    Gap(4)
                    Text(
                        t.count.toString() + (if (t.count == 1) " record" else " records") + (if (t.earned > 0) " · received " + Money.short(t.earned) else ""),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun TagDetailScreen(repo: Repo, nav: Nav, prefs: Prefs, name: String) {
    val scope = rememberCoroutineScope()
    val version by repo.version.collectAsState()
    var stat by remember { mutableStateOf<TagStat?>(null) }
    var txns by remember { mutableStateOf<List<Txn>>(emptyList()) }
    var categories by remember { mutableStateOf<List<app.pocketta.data.Category>>(emptyList()) }
    var menu by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf(name) }
    LaunchedEffect(version, name) {
        stat = repo.tagStats().firstOrNull { it.name == name }
        txns = repo.txnsWithTag(name)
        categories = repo.dao.categories()
    }
    val t = stat
    Scaffold(
        topBar = {
            BackBar("#$name", { nav.back() }) {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.Settings, contentDescription = "Rename or remove") }
            }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (t == null) {
                Text("Nothing with this tag.")
            } else {
                SectionCard(container = MaterialTheme.colorScheme.primaryContainer) {
                    Label("Spent on this tag")
                    Big(Money.short(t.spent) + " " + prefs.baseCurrency, size = 30)
                    if (t.earned > 0) Text("Received " + Money.short(t.earned) + " " + prefs.baseCurrency, style = MaterialTheme.typography.bodySmall)
                }
                if (t.cats.isNotEmpty()) {
                    SectionCard {
                        Label("Where it went")
                        Gap(8)
                        Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp))) {
                            t.cats.forEachIndexed { i, (_, v) ->
                                Box(Modifier.weight(v.toFloat().coerceAtLeast(0.01f)).fillMaxHeight().background(chartPalette[i % chartPalette.size]))
                            }
                        }
                        Gap(8)
                        t.cats.forEachIndexed { i, (k, v) ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 3.dp)) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(chartPalette[i % chartPalette.size]))
                                Text(k, Modifier.weight(1f), maxLines = 1)
                                Text(Money.short(v), fontWeight = FontWeight.SemiBold)
                                Text((if (t.spent > 0) (v / t.spent * 100).toInt() else 0).toString() + "%", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                Label("Records")
                for (x in txns) TxnRow(x, categories, emptyMap()) { nav.open(Route.Add(txnId = x.id)) }
            }
        }
    }
    if (menu) {
        AlertDialog(
            onDismissRequest = { menu = false },
            title = { Text("#$name") },
            text = { TextInput("Rename tag", newName, { newName = it }) },
            confirmButton = {
                TextButton(onClick = {
                    val n = newName.trim()
                    menu = false
                    if (n.isNotEmpty() && n != name) scope.launch { repo.renameTag(name, n); nav.back() }
                }) { Text("Rename") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { menu = false; scope.launch { repo.removeTag(name); nav.back() } }) { Text("Remove tag from everything") }
                    TextButton(onClick = { menu = false }) { Text("Cancel") }
                }
            },
        )
    }
}
