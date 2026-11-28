package app.pocketta.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.pocketta.ai.QuickAiHttpError
import app.pocketta.ai.QuickAiSink
import app.pocketta.ai.QuickAiStore
import app.pocketta.ai.QuickAiTransport
import app.pocketta.core.QuickAi
import app.pocketta.data.Prefs
import app.pocketta.data.Repo
import kotlinx.coroutines.launch
import java.time.LocalDate

/** What the user sees for one proposed transaction. [problem] is set when it cannot be saved (and then it is not offered). */
private class Row2(val p: QuickAi.Proposal, val problem: String?)

private fun typeLabel(t: String) = when (t) {
    "expense" -> "Expense"
    "income" -> "Income"
    "loan_received" -> "Borrowed"
    "loan_given" -> "Lent"
    "loan_repaid_given" -> "You paid back"
    "loan_repaid_received" -> "Paid back to you"
    else -> t
}

private fun amountText(v: Double) = if (v % 1.0 == 0.0) v.toLong().toString() else String.format("%.2f", v)

private fun usd(v: Double) = if (v < 0.01) String.format("%.5f", v) else String.format("%.3f", v)

/** Same text as the Settings card: this month's estimated cost from the logged token counts. */
fun quickAiCostLine(store: QuickAiStore): String =
    "This month: about $" + usd(store.monthCostUsd()) + " (" + store.monthRequests() + " requests). Estimate at $" +
        QuickAi.INPUT_USD_PER_MILLION + " per million input tokens and $" + QuickAi.OUTPUT_USD_PER_MILLION + " per million output tokens."

/** Quick AI entry: type a sentence in Egyptian Arabic, check what the assistant understood, tap Save. Nothing is saved before that tap. */
@Composable
fun QuickAiScreen(repo: Repo, nav: Nav, prefs: Prefs) {
    val ctx = LocalContext.current
    val store = remember { QuickAiStore(ctx) }
    val sink = remember { QuickAiSink(repo) }
    val scope = rememberCoroutineScope()
    var hasKey by remember { mutableStateOf(store.hasKey()) }
    var keyField by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<QuickAi.Pending?>(null) }
    var rows by remember { mutableStateOf<List<Row2>>(emptyList()) }
    val chosen = remember { mutableStateListOf<Int>() }
    var cost by remember { mutableStateOf(quickAiCostLine(store)) }
    var showKey by remember { mutableStateOf(false) }

    fun ask() {
        val input = text.trim()
        val key = store.key()
        if (input.isEmpty() || busy || key == null) return
        busy = true; message = null; pending = null; rows = emptyList(); chosen.clear()
        scope.launch {
            try {
                val session = QuickAi.Session(
                    QuickAiTransport(key), sink,
                    onUsage = { u, ok -> store.addUsage(u, ok) },
                    onLog = { store.addLog(it) },
                )
                when (val r = session.propose(input, LocalDate.now())) {
                    is QuickAi.Proposed.Ready -> {
                        val rs = r.pending.proposals.map { Row2(it, sink.problem(it)) }
                        pending = r.pending; rows = rs
                        rs.forEachIndexed { i, row -> if (row.problem == null) chosen.add(i) }
                    }
                    is QuickAi.Proposed.NeedsReview -> { message = "Needs review: " + r.reason; isError = false }
                    is QuickAi.Proposed.WrongModel -> { message = r.message; isError = true }
                }
            } catch (e: QuickAiHttpError) {
                message = e.friendly(); isError = true
            } catch (e: Exception) {
                message = "Could not reach the assistant. Check your internet and try again."; isError = true
            } finally {
                busy = false
                cost = quickAiCostLine(store)
            }
        }
    }

    Scaffold(topBar = {
        BackBar("Quick AI entry", { nav.back() }) {
            if (hasKey) TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Close" else "Key") }
        }
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (!hasKey || showKey) {
                SectionCard {
                    Label("Your Anthropic key")
                    Gap(6)
                    OutlinedTextField(
                        value = keyField, onValueChange = { keyField = it }, label = { Text("API key (sk-ant-...)") }, singleLine = true, shape = FieldShape,
                        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                    )
                    Gap(8)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { store.setKey(keyField); keyField = ""; hasKey = store.hasKey(); showKey = false }, enabled = keyField.isNotBlank()) { Text("Save key") }
                        if (hasKey) OutlinedButton(onClick = { store.removeKey(); hasKey = false; showKey = false }) { Text("Remove key") }
                    }
                    Gap(8)
                    Text(
                        "The key is encrypted with the phone's secure key store. It stays on this phone only: not in backups, exports or cloud sync. Your sentence is sent to Anthropic, and the cost is on your own account there. Get a key at console.anthropic.com.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (hasKey) {
                SectionCard {
                    Label("Write what happened")
                    Gap(6)
                    OutlinedTextField(
                        value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(), shape = FieldShape, minLines = 2,
                        placeholder = { Text("كشري ٦٠ وبنزين ٢٠٠ النهاردة") },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                    Gap(8)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { ask() }, enabled = text.isNotBlank() && !busy) { Text("Understand") }
                        if (busy) CircularProgressIndicator(Modifier.padding(4.dp))
                    }
                    Gap(6)
                    Text("Nothing is saved until you tap Save.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            message?.let {
                SectionCard(container = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer) { Text(it) }
            }
            pending?.let { pend ->
                SectionCard {
                    Label("Is this right?")
                    Gap(6)
                    rows.forEachIndexed { i, row ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = i in chosen, enabled = row.problem == null, onCheckedChange = { on -> if (on) chosen.add(i) else chosen.remove(i) })
                            Column(Modifier.weight(1f)) {
                                val p = row.p
                                Text(typeLabel(p.type) + " · " + amountText(p.amountEgp) + " " + prefs.baseCurrency + (p.person?.let { " · $it" } ?: ""), style = MaterialTheme.typography.titleSmall)
                                Text(
                                    listOf(p.category, p.note, p.date.toString()).filter { it.isNotBlank() }.joinToString(" · ") + (p.dueDate?.let { " · pay back by $it" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                row.problem?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                    Gap(8)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = chosen.isNotEmpty() && !busy,
                            onClick = {
                                busy = true
                                val picked = chosen.toSet()
                                scope.launch {
                                    try {
                                        val n = pend.confirm(picked)
                                        pending = null; rows = emptyList(); chosen.clear(); text = ""
                                        message = "Saved $n"; isError = false
                                    } catch (e: Exception) {
                                        message = "Could not save. Nothing more was added."; isError = true
                                    } finally { busy = false }
                                }
                            },
                        ) { Text("Save ${chosen.size}") }
                        OutlinedButton(onClick = { pending = null; rows = emptyList(); chosen.clear(); message = "Cancelled. Nothing was saved."; isError = false }, enabled = !busy) { Text("Cancel") }
                    }
                }
            }
            SectionCard {
                Label("Cost")
                Gap(6)
                Text(cost, style = MaterialTheme.typography.bodySmall)
                Gap(4)
                Text("Model: " + QuickAi.MODEL + " (fixed).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
