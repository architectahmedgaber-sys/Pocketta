package app.pocketta.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pocketta.core.Circle
import app.pocketta.core.CycleMath
import app.pocketta.core.InstallmentPlan
import app.pocketta.core.Money
import app.pocketta.data.CircleEntity
import app.pocketta.data.Installment
import app.pocketta.data.Loan
import app.pocketta.data.Prefs
import app.pocketta.data.Repo
import kotlinx.coroutines.launch
import java.time.LocalDate

private fun plan(i: Installment) = InstallmentPlan(i.amount, i.total, i.paid, LocalDate.ofEpochDay(i.firstDue))
private fun circle(c: CircleEntity) = Circle(c.share, c.months, c.myTurn, c.paid, LocalDate.ofEpochDay(c.firstDue))

@Composable
fun CommitmentsScreen(repo: Repo, prefs: Prefs, modifier: Modifier) {
    var tab by remember { mutableIntStateOf(0) }
    Column(modifier.fillMaxSize()) {
        Text("Commitments", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 8.dp))
        TabRow(selectedTabIndex = tab, containerColor = androidx.compose.ui.graphics.Color.Transparent) {
            listOf("Bills", "Installments", "Gam'eya", "Loans").forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, maxLines = 1) })
            }
        }
        when (tab) {
            0 -> BillsScreen(repo, prefs, Modifier)
            1 -> InstallmentsTab(repo, prefs) { tab = 2 }
            2 -> CirclesTab(repo, prefs)
            else -> LoansTab(repo, prefs)
        }
    }
}

// ---------------- Installments ----------------

@Composable
private fun InstallmentsTab(repo: Repo, prefs: Prefs, openCircles: () -> Unit) {
    val scope = rememberCoroutineScope()
    val instFlow = remember { repo.dao.installmentsFlow() }
    val circFlow = remember { repo.dao.circlesFlow() }
    val items by instFlow.collectAsState(initial = emptyList())
    val circles by circFlow.collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<Installment?>(null) }
    val today = LocalDate.now()
    val cycle = CycleMath.cycleFor(today, prefs.cycleStart)
    val epoch = LocalDate.ofEpochDay(0)
    val thisMonth = items.sumOf { plan(it).unpaidDueIn(epoch, cycle.nextStart).size * it.amount } +
        circles.sumOf { circle(it).plan.unpaidDueIn(epoch, cycle.nextStart).size * it.share }
    val owed = items.sumOf { plan(it).remainingAmount } + circles.sumOf { circle(it).plan.remainingAmount }
    val active = items.filter { !plan(it).done }
    val finished = items.filter { plan(it).done }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                SectionCard(Modifier.padding(16.dp), container = MaterialTheme.colorScheme.primaryContainer) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Label("Due this month")
                            Big(Money.short(thisMonth), size = 24)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("still owed in total", style = MaterialTheme.typography.bodySmall)
                            Text(Money.short(owed), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            if (active.isEmpty() && circles.isEmpty()) item { Text("No installments yet. Add valU, Aman, Contact, a credit card plan, or your own type.", Modifier.padding(24.dp)) }
            items(active, key = { it.id }) { i ->
                val p = plan(i)
                InstallmentRow(
                    title = i.name + if (i.provider.isNotBlank()) " · ${i.provider}" else "",
                    sub = "${i.type} · ${i.paid} of ${i.total} · ends ${p.endDate.format(monthYearFmt)}",
                    progress = p.progress, amount = i.amount, next = p.nextDue, today = today,
                    onClick = { editing = i }, onPay = { scope.launch { repo.payInstallment(i) } },
                )
            }
            items(circles.filter { it.paid < it.months }, key = { "c${it.id}" }) { c ->
                val cc = circle(c)
                InstallmentRow(
                    title = c.name, sub = "Gam'eya share · ${c.paid} of ${c.months}",
                    progress = cc.plan.progress, amount = c.share, next = cc.plan.nextDue, today = today,
                    onClick = openCircles, onPay = { scope.launch { repo.payCircle(c) } },
                )
            }
            if (finished.isNotEmpty()) {
                item { Label("Finished", Modifier.padding(start = 16.dp, top = 16.dp)) }
                items(finished, key = { "f${it.id}" }) { i ->
                    ListRow(i.name, "${i.type} · paid off", icon = i.name, onClick = { editing = i }) {
                        Pill("done", Good)
                    }
                }
            }
            item { Gap(88) }
        }
        ExtendedFloatingActionButton(
            onClick = { editing = Installment(name = "", type = "Consumer finance", amount = 0.0, total = 12, firstDue = today.toEpochDay()) },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Installment") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
    editing?.let { InstallmentEditor(repo, it, items.map { x -> x.type }) { editing = null } }
}

@Composable
private fun InstallmentRow(
    title: String, sub: String, progress: Float, amount: Double, next: LocalDate?, today: LocalDate,
    onClick: () -> Unit, onPay: () -> Unit,
) {
    Column {
    ListRow(title, sub, icon = title, onClick = onClick) {
        Column(horizontalAlignment = Alignment.End) {
            Text(Money.fmt(amount), fontWeight = FontWeight.SemiBold)
            if (next != null) {
                val late = next.isBefore(today)
                Text(
                    if (late) "late" else next.format(dayFmt), style = MaterialTheme.typography.bodySmall,
                    color = if (late) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TextButton(onClick = onPay) { Text("Pay") }
    }
    Progress(progress, modifier = Modifier.padding(start = 66.dp, end = 16.dp, bottom = 6.dp))
    }
}

private val defaultTypes = listOf("Consumer finance", "Credit card", "Phone / device", "Car", "Apartment")
private val providers = listOf("valU", "Aman", "Contact", "Souhoola", "Halan", "Sympl", "Bank")

@Composable
private fun InstallmentEditor(repo: Repo, inst: Installment, usedTypes: List<String>, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val walletsFlow = remember { repo.dao.walletsFlow() }
    val wallets by walletsFlow.collectAsState(initial = emptyList())
    var name by remember { mutableStateOf(inst.name) }
    var type by remember { mutableStateOf(inst.type) }
    var newType by remember { mutableStateOf("") }
    var provider by remember { mutableStateOf(inst.provider) }
    var amount by remember { mutableStateOf(numText(inst.amount)) }
    var total by remember { mutableStateOf(inst.total.toString()) }
    var paid by remember { mutableStateOf(inst.paid.toString()) }
    var first by remember { mutableStateOf(LocalDate.ofEpochDay(inst.firstDue)) }
    var walletId by remember { mutableStateOf(inst.walletId) }
    var remind by remember { mutableStateOf(inst.remindDays.toString()) }
    val a = parseNumber(amount)
    val t = total.toIntOrNull()
    val types = (defaultTypes + usedTypes).distinct()
    FormDialog(
        if (inst.id == 0L) "New installment" else inst.name,
        onDismiss = onClose,
        saveEnabled = name.isNotBlank() && a != null && a > 0 && t != null && t > 0,
        onDelete = if (inst.id != 0L) ({ scope.launch { repo.deleteInstallment(inst) }; onClose() }) else null,
        onSave = {
            scope.launch {
                repo.saveInstallment(
                    inst.copy(
                        name = name.trim(), type = newType.trim().ifBlank { type }, provider = provider.trim(), amount = a ?: 0.0,
                        total = t ?: 1, paid = (paid.toIntOrNull() ?: 0).coerceIn(0, t ?: 1), firstDue = first.toEpochDay(),
                        walletId = walletId, remindDays = remind.toIntOrNull()?.coerceIn(0, 30) ?: 1,
                    )
                )
            }
            onClose()
        },
    ) {
        TextInput("What is it (fridge, laptop...)", name, { name = it })
        Label("Type")
        Choices(types, if (newType.isBlank()) type else null, { it }, { type = it; newType = "" })
        TextInput("Or a new type", newType, { newType = it })
        TextInput("Company / bank (optional)", provider, { provider = it })
        Choices(providers, provider, { it }, { provider = it })
        NumberField("Monthly payment", amount, { amount = it })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Months in total", total, { total = it }, Modifier.weight(1f), decimal = false)
            NumberField("Already paid", paid, { paid = it }, Modifier.weight(1f), decimal = false)
        }
        DateField("First payment date", first, { if (it != null) first = it })
        if (a != null && t != null && t > 0) {
            val p = InstallmentPlan(a, t, paid.toIntOrNull() ?: 0, first)
            Text("Total ${Money.fmt(a * t)} · ends ${p.endDate.format(monthYearFmt)} · ${Money.fmt(p.remainingAmount)} left", style = MaterialTheme.typography.bodySmall)
        }
        NumberField("Remind me days before", remind, { remind = it }, decimal = false)
        Label("Pay from")
        Choices(wallets.map { it.id }, walletId, { id -> wallets.firstOrNull { it.id == id }?.name ?: "" }, { walletId = it })
    }
}

// ---------------- Circles (gam'eya) ----------------

@Composable
private fun CirclesTab(repo: Repo, prefs: Prefs) {
    val scope = rememberCoroutineScope()
    val flow = remember { repo.dao.circlesFlow() }
    val circles by flow.collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<CircleEntity?>(null) }
    val today = LocalDate.now()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (circles.isEmpty()) item { Text("No gam'eya yet. Add your share, how many months, and your turn.") }
            items(circles, key = { it.id }) { c ->
                val cc = circle(c)
                SectionCard(onClick = { editing = c }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(c.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        if (c.paid >= c.months) Pill("finished", Good) else Pill("Month ${cc.currentMonth} of ${c.months}")
                    }
                    Text(
                        "${Money.fmt(c.share)} a month · pot ${Money.fmt(cc.payout)} ${prefs.baseCurrency}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Gap(8)
                    Turns(c)
                    Gap(8)
                    val payoutPast = !cc.payoutDate.isAfter(today)
                    Text(
                        if (c.payoutReceived) "You collected ${Money.fmt(cc.payout)} (month ${c.myTurn})."
                        else "Your turn: month ${c.myTurn} · ${cc.payoutDate.format(monthYearFmt)} · +${Money.fmt(cc.payout)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (c.payoutReceived && c.paid < c.months) {
                        Text("Still to pay after collecting: ${Money.fmt((c.months - c.paid) * c.share)}", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        cc.plan.nextDue?.let { d ->
                            OutlinedButton(onClick = { scope.launch { repo.payCircle(c) } }) { Text("Pay share · ${d.format(dayFmt)}") }
                        }
                        if (!c.payoutReceived && payoutPast) {
                            OutlinedButton(onClick = { scope.launch { repo.receivePayout(c) } }) { Text("Got payout") }
                        }
                    }
                }
            }
            item { Gap(72) }
        }
        ExtendedFloatingActionButton(
            onClick = { editing = CircleEntity(name = "", share = 0.0, months = 10, myTurn = 1, firstDue = today.toEpochDay()) },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Gam'eya") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
    editing?.let { CircleEditor(repo, it) { editing = null } }
}

@Composable
private fun Turns(c: CircleEntity) {
    val primary = MaterialTheme.colorScheme.primary
    val done = MaterialTheme.colorScheme.outlineVariant
    val rest = MaterialTheme.colorScheme.surfaceVariant
    val now = Warn
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (m in 1..c.months.coerceAtMost(24)) {
            val mine = m == c.myTurn
            val isNow = m == c.paid + 1
            val bg = when {
                mine -> primary
                m <= c.paid -> done
                else -> rest
            }
            Box(
                Modifier.weight(1f).height(24.dp).clip(RoundedCornerShape(6.dp)).background(bg)
                    .then(if (isNow) Modifier.border(2.dp, now, RoundedCornerShape(6.dp)) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "$m", fontSize = 10.sp,
                    color = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (mine) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun CircleEditor(repo: Repo, c: CircleEntity, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val walletsFlow = remember { repo.dao.walletsFlow() }
    val wallets by walletsFlow.collectAsState(initial = emptyList())
    var name by remember { mutableStateOf(c.name) }
    var share by remember { mutableStateOf(numText(c.share)) }
    var months by remember { mutableStateOf(c.months.toString()) }
    var turn by remember { mutableStateOf(c.myTurn.toString()) }
    var paid by remember { mutableStateOf(c.paid.toString()) }
    var first by remember { mutableStateOf(LocalDate.ofEpochDay(c.firstDue)) }
    var received by remember { mutableStateOf(c.payoutReceived) }
    var walletId by remember { mutableStateOf(c.walletId) }
    var remind by remember { mutableStateOf(c.remindDays.toString()) }
    val s = parseNumber(share)
    val m = months.toIntOrNull()
    val tn = turn.toIntOrNull()
    FormDialog(
        if (c.id == 0L) "New gam'eya" else c.name,
        onDismiss = onClose,
        saveEnabled = name.isNotBlank() && s != null && s > 0 && m != null && m > 0 && tn != null && tn in 1..m,
        onDelete = if (c.id != 0L) ({ scope.launch { repo.deleteCircle(c) }; onClose() }) else null,
        onSave = {
            scope.launch {
                repo.saveCircle(
                    c.copy(
                        name = name.trim(), share = s ?: 0.0, months = m ?: 1, myTurn = tn ?: 1,
                        paid = (paid.toIntOrNull() ?: 0).coerceIn(0, m ?: 1), firstDue = first.toEpochDay(),
                        payoutReceived = received, walletId = walletId, remindDays = remind.toIntOrNull()?.coerceIn(0, 30) ?: 1,
                    )
                )
            }
            onClose()
        },
    ) {
        TextInput("Name (office, family...)", name, { name = it })
        NumberField("My share per month", share, { share = it })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Months / members", months, { months = it }, Modifier.weight(1f), decimal = false)
            NumberField("My turn", turn, { turn = it }, Modifier.weight(1f), decimal = false)
        }
        DateField("First payment date", first, { if (it != null) first = it })
        NumberField("Shares already paid", paid, { paid = it }, decimal = false)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = received, onCheckedChange = { received = it })
            Text("I already collected my payout")
        }
        if (s != null && m != null && tn != null && tn in 1..m) {
            val cc = Circle(s, m, tn, 0, first)
            Text("You collect ${Money.fmt(cc.payout)} in ${cc.payoutDate.format(monthYearFmt)}", style = MaterialTheme.typography.bodySmall)
        }
        NumberField("Remind me days before", remind, { remind = it }, decimal = false)
        Label("Pay from")
        Choices(wallets.map { it.id }, walletId, { id -> wallets.firstOrNull { it.id == id }?.name ?: "" }, { walletId = it })
    }
}

// ---------------- Loans ----------------

@Composable
private fun LoansTab(repo: Repo, prefs: Prefs) {
    val flow = remember { repo.dao.loansFlow() }
    val loans by flow.collectAsState(initial = emptyList())
    var adding by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<Loan?>(null) }
    val today = LocalDate.now()
    val owedToMe = loans.filter { it.lent && !it.closed }.sumOf { it.remaining }
    val iOwe = loans.filter { !it.lent && !it.closed }.sumOf { it.remaining }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionCard(Modifier.weight(1f)) {
                        Label("They owe you")
                        Big(Money.short(owedToMe), Good, 22)
                    }
                    SectionCard(Modifier.weight(1f)) {
                        Label("You owe")
                        Big(Money.short(iOwe), MaterialTheme.colorScheme.error, 22)
                    }
                }
            }
            if (loans.isEmpty()) item { Text("No loans. Money you lend or borrow shows up here.", Modifier.padding(24.dp)) }
            items(loans, key = { it.id }) { l ->
                val sub = buildString {
                    append(if (l.lent) "Lent " else "Borrowed ")
                    append(LocalDate.ofEpochDay(l.epochDay).format(dayFmt))
                    if (l.repaid > 0) append(" · paid ${Money.fmt(l.repaid)} of ${Money.fmt(l.amount)}")
                    l.dueDay?.let { if (!l.closed) append(" · by ${LocalDate.ofEpochDay(it).format(dayFmt)}") }
                    if (l.note.isNotBlank()) append(" · ${l.note}")
                }
                ListRow(l.person, sub, icon = l.person, onClick = { open = l }) {
                    if (l.closed) Pill("settled", Good)
                    else l.dueDay?.let { if (LocalDate.ofEpochDay(it).isBefore(today)) Pill("late", MaterialTheme.colorScheme.error) }
                    Text(
                        (if (l.lent) "+" else "−") + Money.fmt(if (l.closed) l.amount else l.remaining),
                        fontWeight = FontWeight.SemiBold,
                        color = if (l.closed) MaterialTheme.colorScheme.onSurfaceVariant else if (l.lent) Good else MaterialTheme.colorScheme.error,
                    )
                }
            }
            item { Gap(88) }
        }
        ExtendedFloatingActionButton(
            onClick = { adding = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Loan") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
    if (adding) NewLoanDialog(repo, prefs) { adding = false }
    open?.let { l -> LoanDialog(repo, prefs, loans.firstOrNull { it.id == l.id } ?: l) { open = null } }
}

@Composable
private fun NewLoanDialog(repo: Repo, prefs: Prefs, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val walletsFlow = remember { repo.dao.walletsFlow() }
    val wallets by walletsFlow.collectAsState(initial = emptyList())
    var lent by remember { mutableStateOf(true) }
    var person by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var due by remember { mutableStateOf<LocalDate?>(null) }
    var note by remember { mutableStateOf("") }
    var walletId by remember { mutableStateOf<Long?>(null) }
    var record by remember { mutableStateOf(true) }
    val a = parseNumber(amount)
    FormDialog(
        "New loan", onDismiss = onClose,
        saveEnabled = person.isNotBlank() && a != null && a > 0,
        onSave = {
            scope.launch { repo.addLoan(person.trim(), lent, a ?: 0.0, walletId, date, due, note.trim(), record) }
            onClose()
        },
    ) {
        Choices(listOf(true, false), lent, { if (it) "I lent money" else "I borrowed money" }, { lent = it })
        TextInput(if (lent) "To whom" else "From whom", person, { person = it })
        NumberField("Amount", amount, { amount = it }, suffix = prefs.baseCurrency)
        DateField("Date", date, { if (it != null) date = it })
        DateField("Pay back by (optional)", due, { due = it }, allowClear = true)
        TextInput("Note (optional)", note, { note = it })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = record, onCheckedChange = { record = it })
            Text(if (lent) "Take it out of my wallet" else "Add it to my wallet (counts as income)", style = MaterialTheme.typography.bodySmall)
        }
        if (record) {
            Label("Wallet")
            Choices(wallets.map { it.id }, walletId ?: wallets.firstOrNull()?.id, { id -> wallets.firstOrNull { it.id == id }?.name ?: "" }, { walletId = it })
        }
    }
}

@Composable
private fun LoanDialog(repo: Repo, prefs: Prefs, loan: Loan, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var pay by remember { mutableStateOf(numText(loan.remaining)) }
    var due by remember { mutableStateOf(loan.dueDay?.let { LocalDate.ofEpochDay(it) }) }
    val a = parseNumber(pay)
    FormDialog(
        loan.person, onDismiss = onClose,
        saveLabel = if (loan.closed) "Save" else if (loan.lent) "Got it back" else "I paid",
        saveEnabled = loan.closed || (a != null && a > 0),
        onDelete = { scope.launch { repo.deleteLoan(loan) }; onClose() },
        onSave = {
            scope.launch {
                if (due?.toEpochDay() != loan.dueDay) repo.updateLoan(loan.copy(dueDay = due?.toEpochDay()))
                if (!loan.closed && a != null && a > 0) {
                    val fresh = repo.dao.loan(loan.id) ?: loan
                    repo.loanPayment(fresh, a)
                }
            }
            onClose()
        },
    ) {
        Text(
            (if (loan.lent) "${loan.person} owes you " else "You owe ${loan.person} ") +
                "${Money.fmt(loan.remaining)} ${prefs.baseCurrency}" +
                if (loan.repaid > 0) " (paid ${Money.fmt(loan.repaid)} of ${Money.fmt(loan.amount)})" else "",
        )
        if (!loan.closed) {
            NumberField(if (loan.lent) "Amount they paid back" else "Amount you paid back", pay, { pay = it })
        }
        DateField("Pay back by", due, { due = it }, allowClear = true)
        if (loan.lent && !loan.closed) {
            OutlinedButton(onClick = {
                val msg = "Hi ${loan.person}, just a friendly reminder about the ${Money.fmt(loan.remaining)} ${prefs.baseCurrency}" +
                    (due?.let { " (due ${it.format(dayFmt)})" } ?: "") + ". Thanks!"
                val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/?text=" + Uri.encode(msg)))
                runCatching { context.startActivity(i) }
            }) { Text("Send a WhatsApp reminder") }
        }
    }
}
