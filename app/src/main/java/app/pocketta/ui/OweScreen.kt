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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import app.pocketta.core.LoanSchedule
import app.pocketta.core.Money
import app.pocketta.data.CircleEntity
import app.pocketta.data.Installment
import app.pocketta.data.Loan
import app.pocketta.data.LoanInstallment
import app.pocketta.data.Prefs
import app.pocketta.data.Repo
import kotlinx.coroutines.launch
import java.time.LocalDate

private fun plan(i: Installment) = InstallmentPlan(i.amount, i.total, i.paid, LocalDate.ofEpochDay(i.firstDue))
private fun circle(c: CircleEntity) = Circle(c.share, c.months, c.myTurn, c.paid, LocalDate.ofEpochDay(c.firstDue))

@Composable
fun CommitmentsScreen(repo: Repo, nav: Nav, prefs: Prefs, modifier: Modifier) {
    var tab by remember { mutableIntStateOf(0) }
    Column(modifier.fillMaxSize()) {
        Text("Commitments", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 8.dp))
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp, containerColor = androidx.compose.ui.graphics.Color.Transparent) {
            listOf("This month", "Bills", "Installments", "Gam'eya", "Loans").forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, maxLines = 1) })
            }
        }
        when (tab) {
            0 -> CommitOverview(repo, nav, prefs, Modifier)
            1 -> BillsScreen(repo, prefs, Modifier)
            2 -> InstallmentsTab(repo, prefs) { tab = 3 }
            3 -> CirclesTab(repo, prefs)
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
                            Text(Money.short(owed), fontWeight = FontWeight.Medium)
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
            Text(Money.fmt(amount), fontWeight = FontWeight.Medium)
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

internal val defaultTypes = listOf("Consumer finance", "Credit card", "Phone / device", "Car", "Apartment")
private val providers = listOf("valU", "Aman", "Contact", "Souhoola", "Halan", "Sympl", "Bank")

@Composable
internal fun InstallmentEditor(repo: Repo, inst: Installment, usedTypes: List<String>, onClose: () -> Unit) {
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
                        Text(c.name, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
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
                    fontWeight = if (mine) FontWeight.Medium else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
internal fun CircleEditor(repo: Repo, c: CircleEntity, onClose: () -> Unit) {
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
    }
}

// ---------------- Loans ----------------

@Composable
private fun LoansTab(repo: Repo, prefs: Prefs) {
    val flow = remember { repo.dao.loansFlow() }
    val loans by flow.collectAsState(initial = emptyList())
    val instFlow = remember { repo.dao.loanInstallmentsFlow() }
    val allInst by instFlow.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<Loan?>(null) }
    var dating by remember { mutableStateOf<Loan?>(null) }
    var iOweTab by remember { mutableStateOf(true) }
    val today = LocalDate.now()
    val cycle = remember(prefs.cycleStart) { CycleMath.cycleFor(today, prefs.cycleStart) }
    val owedToMe = loans.filter { it.lent && !it.closed }.sumOf { it.remaining }
    val iOwe = loans.filter { !it.lent && !it.closed }.sumOf { it.remaining }
    val shown = loans.filter { it.lent != iOweTab }
    // What falls due in this month's cycle on the shown side.
    val thisMonth = allInst.filter { i -> shown.any { it.id == i.loanId && !it.closed } && i.remaining > 0.004 && LocalDate.ofEpochDay(i.dueDay).isBefore(cycle.nextStart) }.sumOf { it.remaining }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Choices(listOf(true, false), iOweTab, { if (it) "I owe" else "Owed to me" }, { iOweTab = it })
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionCard(Modifier.weight(1f)) {
                            Label(if (iOweTab) "Total I owe" else "Total owed to me")
                            Big(Money.short(if (iOweTab) iOwe else owedToMe), if (iOweTab) MaterialTheme.colorScheme.error else Good, 22)
                        }
                        SectionCard(Modifier.weight(1f)) {
                            Label("This month")
                            Big(Money.short(thisMonth), MaterialTheme.colorScheme.onSurface, 22)
                        }
                    }
                }
            }
            if (shown.isEmpty()) item { Text("No loans. Money you lend or borrow shows up here.", Modifier.padding(24.dp)) }
            items(shown, key = { it.id }) { l ->
                val rows = allInst.filter { it.loanId == l.id }.sortedBy { it.dueDay }
                val next = rows.firstOrNull { it.remaining > 0.004 }
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    SectionCard(onClick = { open = l }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(l.person, fontWeight = FontWeight.Medium)
                                Text(
                                    (if (l.lent) "Lent " else "Borrowed ") + LocalDate.ofEpochDay(l.epochDay).format(dayFmt) + " · " + Money.fmt(l.amount) +
                                        (if (l.note.isNotBlank()) " · ${l.note}" else ""),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (l.closed) Pill("settled", Good)
                            else if (rows.size > 1) Pill("Instalment ${rows.count { it.remaining < 0.005 } + 1} of ${rows.size}", Good)
                        }
                        Gap(10)
                        Progress(if (l.amount <= 0) 0f else (l.repaid / l.amount).toFloat().coerceIn(0f, 1f), color = GoldAccent)
                        Gap(4)
                        Text("Repaid ${Money.fmt(l.repaid)} · ${Money.fmt(l.remaining)} left", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!l.closed && rows.isNotEmpty()) {
                            Gap(8)
                            for (r in rows) {
                                val d = LocalDate.ofEpochDay(r.dueDay)
                                val paid = r.remaining < 0.005
                                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(d.format(dayFmt), Modifier.weight(1f), color = if (paid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                                    if (paid) Pill("paid", Good)
                                    else if (d.isBefore(today)) Pill("late", MaterialTheme.colorScheme.error)
                                    else if (d.isBefore(cycle.nextStart)) Pill("This month", Good)
                                    Text(
                                        (if (l.lent) "+" else "−") + Money.fmt(if (paid) r.amount else r.remaining), Modifier.padding(start = 8.dp),
                                        color = if (paid) MaterialTheme.colorScheme.onSurfaceVariant else if (l.lent) Good else MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                        if (!l.closed && rows.isEmpty()) {
                            Gap(8)
                            Text("No repayment date yet. Set one so it counts in your budget.", style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { dating = l }) { Text("Set repayment date") }
                        }
                        if (!l.closed && next != null) {
                            Gap(8)
                            androidx.compose.material3.Button(
                                onClick = { scope.launch { withPayUndo(repo, if (l.lent) "Marked received" else "Marked paid") { repo.payNextInstalment(l) } } }, modifier = Modifier.fillMaxWidth(),
                            ) { Text(if (l.lent) "Got the instalment" else "I paid the instalment") }
                        }
                    }
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
    dating?.let { l -> SetRepaymentDialog(repo, loans.firstOrNull { it.id == l.id } ?: l) { dating = null } }
}

/** A loan with no repayment date gets one (or monthly instalments) here; only then does it count as a commitment. */
@Composable
internal fun SetRepaymentDialog(repo: Repo, loan: Loan, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf("LUMP") }
    var first by remember { mutableStateOf<LocalDate?>(LocalDate.now().plusMonths(1)) }
    var count by remember { mutableStateOf("3") }
    val n = (parseNumber(count)?.toInt() ?: 1).coerceIn(1, 60)
    val plan = first?.let { LoanSchedule.generate(loan.remaining, if (mode == "LUMP") 1 else n, it) } ?: emptyList()
    FormDialog(
        "Repayment date", onDismiss = onClose, saveEnabled = first != null,
        onSave = { scope.launch { first?.let { repo.setRepayment(loan, it, if (mode == "LUMP") 1 else n) } }; onClose() },
    ) {
        Choices(listOf("LUMP", "INST"), mode, { if (it == "LUMP") "One payment" else "Instalments" }, { mode = it })
        if (mode == "INST") NumberField("Number of instalments", count, { count = it }, decimal = false)
        DateField(if (mode == "LUMP") "Pay back on" else "First instalment", first, { first = it })
        if (plan.isNotEmpty()) LoanPreview(loan.lent, loan.remaining, LocalDate.now(), plan, false, "")
    }
}

@Composable
internal fun NewLoanDialog(
    repo: Repo, prefs: Prefs, initLent: Boolean? = null, initPerson: String = "", initAmount: String = "", onSaved: () -> Unit = {}, onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val walletsFlow = remember { repo.dao.walletsFlow() }
    val wallets by walletsFlow.collectAsState(initial = emptyList())
    var lentSel by remember { mutableStateOf(initLent) }
    val lent = lentSel == true
    var person by remember { mutableStateOf(initPerson) }
    var amount by remember { mutableStateOf(initAmount) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var mode by remember { mutableStateOf("LUMP") }
    var due by remember { mutableStateOf<LocalDate?>(null) }
    var count by remember { mutableStateOf("3") }
    var first by remember { mutableStateOf(LocalDate.now().plusMonths(1)) }
    var note by remember { mutableStateOf("") }
    var walletId by remember { mutableStateOf<Long?>(null) }
    var record by remember { mutableStateOf(true) }
    var countInBudget by remember { mutableStateOf(true) }
    val a = parseNumber(amount)
    val n = (parseNumber(count)?.toInt() ?: 1).coerceIn(1, 60)
    // What will be repaid and when: the same list is previewed and saved.
    val schedule: List<Pair<LocalDate, Double>> =
        if (a == null || a <= 0) emptyList()
        else if (mode == "LUMP") due?.let { listOf(it to a) } ?: emptyList()
        else LoanSchedule.generate(a, n, first)
    FormDialog(
        "New loan", onDismiss = onClose,
        saveEnabled = lentSel != null && person.isNotBlank() && a != null && a > 0,
        onSave = {
            scope.launch { repo.addLoan(person.trim(), lent, a ?: 0.0, walletId, date, null, note.trim(), record, schedule, countInBudget) }
            onSaved()
            onClose()
        },
    ) {
        Choices(listOf(false, true), lentSel, { if (it) "I lent" else "I borrowed" }, { lentSel = it })
        TextInput(if (lent) "To whom" else "From whom", person, { person = it })
        NumberField("Amount", amount, { amount = it }, suffix = prefs.baseCurrency)
        Label("Repay")
        Choices(listOf("LUMP", "INST"), mode, { if (it == "LUMP") "One payment" else "Instalments" }, { mode = it })
        if (mode == "LUMP") {
            DateField("Pay back on (optional)", due, { due = it }, allowClear = true)
        } else {
            NumberField("Number of instalments", count, { count = it }, decimal = false)
            DateField("First instalment", first, { if (it != null) first = it })
        }
        DateField(if (lent) "Date you lent" else "Date you borrowed", date, { if (it != null) date = it })
        if (lentSel != null && a != null && a > 0) LoanPreview(lent, a, date, schedule, record, prefs.baseCurrency)
        TextInput("Note (optional)", note, { note = it })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = record, onCheckedChange = { record = it })
            Text(if (lent) "The money left my wallet" else "The money came to my wallet (counts as income)", style = MaterialTheme.typography.bodySmall)
        }
        if (!record && !lent) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = countInBudget, onCheckedChange = { countInBudget = it })
                Text("Count the repayments in my budget", style = MaterialTheme.typography.bodySmall)
            }
        }
        Text("You can turn off recording as income if the money did not come into your wallet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "What will happen": what the loan does to each month, shown before anything is saved. */
@Composable
internal fun LoanPreview(lent: Boolean, total: Double, day: LocalDate, schedule: List<Pair<LocalDate, Double>>, recorded: Boolean, currency: String) {
    val lines = LoanSchedule.preview(lent, total, day, schedule, recorded)
    SectionCard {
        Label("What will happen")
        Gap(6)
        val start = java.time.YearMonth.from(day)
        if (lent && recorded) Text(start.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.US) + ": −" + Money.fmt(total) + " " + currency, color = MaterialTheme.colorScheme.error)
        for (l in lines) {
            val name = l.month.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.US)
            if (l.income > 0) Text("$name: +${Money.fmt(l.income)} " + (if (lent) "coming back" else "income"), color = Good)
            if (l.commitment > 0) Text("$name: −${Money.fmt(l.commitment)} commitment", color = MaterialTheme.colorScheme.error)
        }
        if (schedule.isEmpty()) {
            Gap(4)
            Text("No repayment date yet. You will be asked to set one.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun LoanDialog(repo: Repo, prefs: Prefs, loan: Loan, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val instFlow = remember { repo.dao.loanInstallmentsFlow() }
    val schedule = instFlow.collectAsState(initial = emptyList()).value.filter { it.loanId == loan.id }.sortedBy { it.dueDay }
    val next = schedule.firstOrNull { it.remaining > 0.004 }
    var pay by remember(next?.id) { mutableStateOf(numText(next?.remaining ?: loan.remaining)) }
    var due by remember { mutableStateOf(loan.dueDay?.let { LocalDate.ofEpochDay(it) }) }
    var person by remember { mutableStateOf(loan.person) }
    var total by remember { mutableStateOf(numText(loan.amount)) }
    val a = parseNumber(pay)
    FormDialog(
        loan.person, onDismiss = onClose,
        saveLabel = if (loan.closed) "Save" else if (loan.lent) "Got it back" else "I paid",
        saveEnabled = person.isNotBlank() && (loan.closed || (a != null && a > 0)),
        onDelete = { scope.launch { repo.deleteLoan(loan) }; onClose() },
        onSave = {
            scope.launch {
                // Only a loan with no schedule gets its date here; scheduled loans change through their instalments.
                if (schedule.isEmpty() && due != null && due?.toEpochDay() != loan.dueDay) repo.setRepayment(loan, due!!, 1)
                val newTotal = parseNumber(total) ?: loan.amount
                if (person.trim() != loan.person || (newTotal > 0 && newTotal != loan.amount)) {
                    repo.editLoan(repo.dao.loan(loan.id) ?: loan, person.trim(), newTotal)
                }
                if (!loan.closed && a != null && a > 0) {
                    val fresh = repo.dao.loan(loan.id) ?: loan
                    withPayUndo(repo, if (loan.lent) "Marked received" else "Marked paid") { repo.loanPayment(fresh, a) }
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
        if (schedule.isEmpty()) DateField("Pay back by", due, { due = it }, allowClear = true)
        else for (r in schedule) Text(
            LocalDate.ofEpochDay(r.dueDay).format(dayFmt) + " · " + Money.fmt(r.amount) + if (r.remaining < 0.005) " · paid" else "",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(person, { person = it }, label = { Text("To / from whom") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        NumberField("Loan amount", total, { total = it })
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
