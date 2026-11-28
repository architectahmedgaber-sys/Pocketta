package app.pocketta.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.pocketta.ai.QUICK_AI_OFFLINE
import app.pocketta.ai.QuickAiHttpError
import app.pocketta.ai.QuickAiSink
import app.pocketta.ai.QuickAiStore
import app.pocketta.ai.QuickAiTransport
import app.pocketta.core.QuickAi
import app.pocketta.data.Category
import app.pocketta.data.Prefs
import app.pocketta.data.Repo
import app.pocketta.data.TxType
import kotlinx.coroutines.launch
import java.time.LocalDate

/** One card on the confirmation screen. Its fields change as the user edits; [id] keeps the card's field state when others are deleted. */
private class Draft(val id: Int, p: QuickAi.Proposal) {
    var p by mutableStateOf(p)
}

private val typeLabels = listOf(
    "expense" to "مصروف", "income" to "دخل", "loan_received" to "استلفت", "loan_given" to "سلّفت",
    "loan_repaid_given" to "سدّدت لـ", "loan_repaid_received" to "سدّد لي",
)

private fun amountText(v: Double) = if (v == 0.0) "" else if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

/** Accepts Arabic-Indic digits and a comma or Arabic decimal mark. Anything unreadable becomes 0, which the card then flags. */
private fun parseAmount(s: String): Double {
    val t = buildString { for (c in s.trim()) append(if (c in '٠'..'٩') ('0' + (c - '٠')) else if (c == '٫' || c == ',') '.' else c) }
    return t.toDoubleOrNull() ?: 0.0
}

private fun usd(v: Double) = if (v < 0.01) String.format("%.5f", v) else String.format("%.3f", v)

/** Same text as the Settings card: this month's estimated cost from the logged token counts. */
fun quickAiCostLine(store: QuickAiStore): String =
    "هذا الشهر: حوالي $" + usd(store.monthCostUsd()) + " (" + store.monthRequests() + " طلب). تقدير على سعر $" +
        QuickAi.INPUT_USD_PER_MILLION + " لكل مليون توكن دخل و $" + QuickAi.OUTPUT_USD_PER_MILLION + " لكل مليون توكن خرج."

/**
 * Quick AI entry. Type a sentence, the assistant proposes cards, the user edits / deletes / adds cards, then one "Confirm all"
 * saves the cards exactly as they are on screen. Nothing is saved before that tap.
 */
@Composable
fun QuickAiScreen(repo: Repo, nav: Nav, prefs: Prefs) {
    val ctx = LocalContext.current
    val store = remember { QuickAiStore(ctx) }
    val sink = remember { QuickAiSink(repo) }
    val scope = rememberCoroutineScope()
    var hasKey by remember { mutableStateOf(store.hasKey()) }
    var showSetup by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var reviewReason by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<QuickAi.Pending?>(null) }
    val cards = remember { mutableStateListOf<Draft>() }
    var nextId by remember { mutableStateOf(1) }
    var problems by remember { mutableStateOf<Map<Int, String?>>(emptyMap()) }
    var cats by remember { mutableStateOf<List<Category>>(emptyList()) }
    var cost by remember { mutableStateOf(quickAiCostLine(store)) }

    LaunchedEffect(Unit) { cats = repo.dao.categories().filter { !it.archived } }
    val snapshot = cards.map { it.id to it.p }
    LaunchedEffect(snapshot) { problems = snapshot.associate { (id, p) -> id to (QuickAi.validate(p) ?: sink.problem(p)) } }

    fun clearResult() { pending = null; cards.clear(); reviewReason = null }

    fun ask() {
        val input = text.trim()
        val key = store.key()
        if (input.isEmpty() || busy || key == null) return
        busy = true; message = null; clearResult()
        scope.launch {
            try {
                val session = QuickAi.Session(
                    QuickAiTransport(key), sink,
                    onUsage = { u, ok -> store.addUsage(u, ok) },
                    onLog = { store.addLog(it) },
                )
                when (val r = session.propose(input, LocalDate.now())) {
                    is QuickAi.Proposed.Ready -> {
                        pending = r.pending
                        r.pending.proposals.forEach { cards.add(Draft(nextId++, it)) }
                    }
                    is QuickAi.Proposed.NeedsReview -> reviewReason = r.reason
                    is QuickAi.Proposed.WrongModel -> { message = "الرد جه من موديل غير المتوقع فاتجاهلته. مفيش حاجة اتسجلت."; isError = true }
                }
            } catch (e: QuickAiHttpError) {
                message = e.friendly(); isError = true
            } catch (e: Exception) {
                message = QUICK_AI_OFFLINE; isError = true
            } finally {
                busy = false
                cost = quickAiCostLine(store)
            }
        }
    }

    Scaffold(topBar = {
        BackBar("إدخال بالذكاء الاصطناعي", { nav.back() }) {
            if (hasKey) TextButton(onClick = { showSetup = !showSetup }) { Text(if (showSetup) "رجوع" else "المفتاح") }
        }
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (!hasKey || showSetup) {
                QuickAiSetup(store) { hasKey = store.hasKey(); if (!hasKey) clearResult() }
                return@Column
            }
            SectionCard {
                Label("اكتب اللي حصل")
                Gap(6)
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(), shape = FieldShape, minLines = 2,
                    placeholder = { Text("كشري ٦٠ وبنزين ٢٠٠ النهاردة") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                Gap(8)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { ask() }, enabled = text.isNotBlank() && !busy) { Text("افهم") }
                    if (busy) CircularProgressIndicator(Modifier.padding(4.dp))
                }
                Gap(6)
                Text("مفيش حاجة بتتسجل غير لما تدوس «تأكيد الكل».", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            message?.let {
                SectionCard(container = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer) { Text(it) }
            }
            reviewReason?.let { reason ->
                SectionCard(container = MaterialTheme.colorScheme.tertiaryContainer) {
                    Label("محتاج مراجعة")
                    Gap(4)
                    Text(reason)
                    Gap(8)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { reviewReason = null }) { Text("اكتب تاني") }
                        OutlinedButton(onClick = { nav.open(Route.Add()) }) { Text("إدخال يدوي") }
                    }
                    Text("مفيش حاجة اتسجلت.", style = MaterialTheme.typography.bodySmall)
                }
            }
            pending?.let { pend ->
                Label("راجع وعدّل")
                cards.forEach { d ->
                    key(d.id) {
                        ProposalCard(
                            d.p, problems[d.id], cats,
                            onChange = { d.p = it },
                            onDelete = { cards.remove(d) },
                        )
                    }
                }
                OutlinedButton(onClick = { cards.add(Draft(nextId++, QuickAi.blankProposal(LocalDate.now()))) }) { Text("＋ ضيف معاملة") }
                val ready = cards.isNotEmpty() && cards.all { problems[it.id] == null } && !busy
                Button(
                    enabled = ready, modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        busy = true
                        val finalList = cards.map { it.p }
                        scope.launch {
                            try {
                                val n = pend.confirmAll(finalList)
                                clearResult(); text = ""
                                message = "اتسجل $n"; isError = false
                            } catch (e: Exception) {
                                message = "ماقدرتش أسجل. راجع الكروت وجرّب تاني."; isError = true
                            } finally { busy = false }
                        }
                    },
                ) { Text("تأكيد الكل (" + cards.size + ")") }
                TextButton(onClick = { clearResult(); message = "اتلغى. مفيش حاجة اتسجلت."; isError = false }, enabled = !busy) { Text("إلغاء") }
            }
            SectionCard {
                Label("التكلفة")
                Gap(6)
                Text(cost, style = MaterialTheme.typography.bodySmall)
                Gap(4)
                Text("الموديل: " + QuickAi.MODEL + " (ثابت).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** One editable proposal: type, amount, category (for expense / income), person and due date (for loans), date. */
@Composable
private fun ProposalCard(p: QuickAi.Proposal, problem: String?, cats: List<Category>, onChange: (QuickAi.Proposal) -> Unit, onDelete: () -> Unit) {
    var amountField by remember { mutableStateOf(amountText(p.amountEgp)) }
    var pickDate by remember { mutableStateOf(false) }
    var pickDue by remember { mutableStateOf(false) }
    val money = p.type == "expense" || p.type == "income"
    val loan = p.type == "loan_received" || p.type == "loan_given"
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dropdown(typeLabels.firstOrNull { it.first == p.type }?.second ?: p.type, typeLabels.map { it.second }) { i ->
                val t = typeLabels[i].first
                val isLoan = t == "loan_received" || t == "loan_given"
                onChange(p.copy(type = t, dueDate = if (isLoan) p.dueDate else null))
            }
            Box(Modifier.weight(1f))
            TextButton(onClick = onDelete) { Text("حذف") }
        }
        Gap(6)
        OutlinedTextField(
            value = amountField, onValueChange = { amountField = it; onChange(p.copy(amountEgp = parseAmount(it))) },
            label = { Text("المبلغ") }, singleLine = true, shape = FieldShape, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        if (money) {
            Gap(6)
            val names = cats.filter { it.kind == (if (p.type == "income") TxType.INCOME else TxType.EXPENSE) }.map { it.name }
            Dropdown(p.category.ifBlank { "اختار تصنيف" }, names) { i -> onChange(p.copy(category = names[i])) }
        }
        if (!money) {
            Gap(6)
            OutlinedTextField(
                value = p.person.orEmpty(), onValueChange = { onChange(p.copy(person = it.ifBlank { null })) },
                label = { Text("الشخص") }, singleLine = true, shape = FieldShape, modifier = Modifier.fillMaxWidth(),
            )
        }
        Gap(6)
        OutlinedTextField(
            value = p.note, onValueChange = { onChange(p.copy(note = it)) },
            label = { Text("ملاحظة") }, singleLine = true, shape = FieldShape, modifier = Modifier.fillMaxWidth(),
        )
        Gap(6)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { pickDate = true }) { Text("التاريخ: " + p.date) }
            if (loan) {
                OutlinedButton(onClick = { pickDue = true }) { Text(if (p.dueDate == null) "تاريخ السداد: —" else "السداد: " + p.dueDate) }
                if (p.dueDate != null) TextButton(onClick = { onChange(p.copy(dueDate = null)) }) { Text("مسح") }
            }
        }
        problem?.let { Gap(4); Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
    if (pickDate) DayPicker(p.date, { onChange(p.copy(date = it)); pickDate = false }, { pickDate = false })
    if (pickDue) DayPicker(p.dueDate ?: p.date, { onChange(p.copy(dueDate = it)); pickDue = false }, { pickDue = false })
}

/** A button that opens a menu of [items]; [onPick] gets the index tapped. */
@Composable
private fun Dropdown(current: String, items: List<String>, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text(current + " ▾") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEachIndexed { i, label -> DropdownMenuItem(text = { Text(label) }, onClick = { open = false; onPick(i) }) }
        }
    }
}
