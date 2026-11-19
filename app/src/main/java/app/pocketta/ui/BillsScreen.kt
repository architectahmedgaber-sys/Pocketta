package app.pocketta.ui

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pocketta.core.CycleMath
import app.pocketta.core.Frequency
import app.pocketta.core.Money
import app.pocketta.core.Recurrence
import app.pocketta.data.Bill
import app.pocketta.data.Prefs
import app.pocketta.data.Repo
import app.pocketta.data.TxType
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun BillsScreen(repo: Repo, prefs: Prefs, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val billsFlow = remember { repo.dao.billsFlow() }
    val bills by billsFlow.collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<Bill?>(null) }
    var paying by remember { mutableStateOf<Bill?>(null) }
    val today = LocalDate.now()
    val cycle = CycleMath.cycleFor(today, prefs.cycleStart)
    val monthly = bills.sumOf { Recurrence.monthlyShare(it.amount * prefs.rateOf(it.currency), Frequency.parse(it.frequency), it.everyN) }
    val leftThisMonth = bills.sumOf { b ->
        val f = Frequency.parse(b.frequency)
        val first = LocalDate.ofEpochDay(b.nextDue)
        Recurrence.occurrences(first, f, b.everyN, first, cycle.nextStart, b.anchorDay).size * b.amount * prefs.rateOf(b.currency)
    }
    val cur = prefs.baseCurrency

    Box(modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize()) {
            item { Column {
                Text("Bills & subscriptions", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp))
                SectionCard(Modifier.padding(horizontal = 16.dp), container = MaterialTheme.colorScheme.primaryContainer) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Label("Per month on average")
                            Big("${Money.short(monthly)} $cur", size = 24)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("left to pay this month", style = MaterialTheme.typography.bodySmall)
                            Text(Money.short(leftThisMonth), fontWeight = FontWeight.Bold)
                        }
                    }
                    Text("Yearly and quarterly bills are spread over the months, so they never surprise you.", style = MaterialTheme.typography.bodySmall)
                }
                Gap(8)
            } }
            if (bills.isEmpty()) item { Text("No bills yet. Add internet, mobile, electricity, Netflix, the gym...", Modifier.padding(24.dp)) }
            items(bills, key = { it.id }) { b ->
                val due = LocalDate.ofEpochDay(b.nextDue)
                val days = ChronoUnit.DAYS.between(today, due)
                val f = Frequency.parse(b.frequency)
                ListRow(
                    b.name,
                    Recurrence.describe(f, b.everyN) + " · due " + due.format(dayFmt) + if (b.kind == "SUB") " · subscription" else "",
                    icon = b.name,
                    onClick = { editing = b },
                ) {
                    when {
                        days < 0 -> Pill("late", MaterialTheme.colorScheme.error)
                        days == 0L -> Pill("today", Warn)
                        days <= 3 -> Pill("in $days d", Warn)
                    }
                    Text((if (b.variable) "~" else "") + Money.fmt(b.amount), fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { if (b.variable) paying = b else scope.launch { repo.payBill(b) } }) { Text("Paid") }
                }
            }
            item { Gap(88) }
        }
        ExtendedFloatingActionButton(
            onClick = { editing = Bill(name = "", amount = 0.0, nextDue = today.toEpochDay(), anchorDay = today.dayOfMonth) },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Bill") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
    editing?.let { BillEditor(repo, prefs, it) { editing = null } }
    paying?.let { b -> PayBillDialog(repo, b) { paying = null } }
}

/** Asks for this time's amount of a bill whose amount changes (electricity), then marks it paid. */
@Composable
internal fun PayBillDialog(repo: Repo, b: Bill, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var amount by remember(b.id) { mutableStateOf("") }
    LaunchedEffect(b.id) { amount = numText(repo.estimate(b)) }
    FormDialog("Pay ${b.name}", onDismiss = onClose, saveLabel = "Paid", saveEnabled = (parseNumber(amount) ?: 0.0) > 0, onSave = {
        parseNumber(amount)?.let { v -> scope.launch { repo.payBill(b, v) } }
        onClose()
    }) {
        NumberField("Amount this time", amount, { amount = it }, suffix = b.currency)
    }
}

@Composable
internal fun BillEditor(repo: Repo, prefs: Prefs, bill: Bill, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val walletsFlow = remember { repo.dao.walletsFlow() }
    val wallets by walletsFlow.collectAsState(initial = emptyList())
    var name by remember { mutableStateOf(bill.name) }
    var amount by remember { mutableStateOf(numText(bill.amount)) }
    var kind by remember { mutableStateOf(bill.kind) }
    var freq by remember { mutableStateOf(Frequency.parse(bill.frequency)) }
    var everyN by remember { mutableStateOf(bill.everyN.toString()) }
    var due by remember { mutableStateOf(LocalDate.ofEpochDay(bill.nextDue)) }
    var walletId by remember { mutableStateOf(bill.walletId) }
    var remind by remember { mutableStateOf(bill.remindDays.toString()) }
    var variable by remember { mutableStateOf(bill.variable) }
    var currency by remember { mutableStateOf(bill.currency) }
    val a = parseNumber(amount)
    val n = everyN.toIntOrNull()?.coerceAtLeast(1) ?: 30
    FormDialog(
        if (bill.id == 0L) "New bill" else bill.name,
        onDismiss = onClose,
        saveEnabled = name.isNotBlank() && a != null && a > 0,
        onDelete = if (bill.id != 0L) ({ scope.launch { repo.deleteBill(bill) }; onClose() }) else null,
        onSave = {
            scope.launch {
                val cat = bill.categoryId ?: repo.guessCategory(name, TxType.EXPENSE)
                repo.saveBill(
                    bill.copy(
                        name = name.trim(), amount = a ?: 0.0, kind = kind, frequency = freq.name, everyN = n,
                        nextDue = due.toEpochDay(), anchorDay = due.dayOfMonth, walletId = walletId,
                        remindDays = remind.toIntOrNull()?.coerceIn(0, 30) ?: 1, variable = variable, currency = currency, categoryId = cat,
                    )
                )
            }
            onClose()
        },
    ) {
        Choices(listOf("BILL", "SUB"), kind, { if (it == "BILL") "Bill" else "Subscription" }, { kind = it })
        TextInput("Name", name, { name = it })
        NumberField(if (variable) "Usual amount" else "Amount", amount, { amount = it }, suffix = currency)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = variable, onCheckedChange = { variable = it })
            Text("Amount changes (use the average of the last 3)", style = MaterialTheme.typography.bodySmall)
        }
        if (prefs.currencies.size > 1) Choices(prefs.currencies, currency, { it }, { currency = it })
        Label("Repeats")
        Choices(Frequency.entries.toList(), freq, { it.label }, { freq = it })
        if (freq == Frequency.EVERY_N_DAYS) NumberField("Every how many days", everyN, { everyN = it }, decimal = false)
        if (a != null && a > 0) {
            Text(
                "≈ ${Money.fmt(Recurrence.monthlyShare(a, freq, n))} a month · ${Money.fmt(Recurrence.yearlyCost(a, freq, n))} a year",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        DateField("Next due date", due, { if (it != null) due = it })
        NumberField("Remind me days before", remind, { remind = it }, decimal = false)
        Label("Pay from")
        Choices(wallets.map { it.id }, walletId, { id -> wallets.firstOrNull { it.id == id }?.name ?: "" }, { walletId = it })
        if (bill.id != 0L) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { scope.launch { repo.skipBill(bill) }; onClose() }) { Text("Skip this time") }
            }
        }
    }
}
