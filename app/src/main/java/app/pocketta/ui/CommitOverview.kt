package app.pocketta.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.state.ToggleableState
import androidx.compose.foundation.layout.width
import app.pocketta.data.PayItem
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import app.pocketta.core.Cycle
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pocketta.core.Badges
import app.pocketta.data.PayGroup
import app.pocketta.data.PayUndo
import app.pocketta.core.CFilter
import app.pocketta.core.CItem
import app.pocketta.core.CKind
import app.pocketta.core.Circle
import app.pocketta.core.Commitments
import app.pocketta.core.CycleMath
import app.pocketta.core.Frequency
import app.pocketta.core.InstallmentPlan
import app.pocketta.core.Money
import app.pocketta.core.Recurrence
import app.pocketta.data.Bill
import app.pocketta.data.CircleEntity
import app.pocketta.data.Installment
import app.pocketta.data.Loan
import app.pocketta.data.Prefs
import app.pocketta.data.Repo
import app.pocketta.data.Source
import app.pocketta.data.TxType
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Pictures the user picked from the gallery for a payment, kept as small files inside the app. */
internal object CommitIcons {
    fun key(item: CItem): String = when (item.kind) {
        CKind.BILL, CKind.SUB -> "b${item.refId}"
        CKind.INSTALLMENT, CKind.CARD -> "i${item.refId}"
        CKind.CIRCLE -> "c${item.refId}"
        CKind.LOAN -> "l${item.refId}"
        CKind.INCOME -> "salary"
    }

    private fun file(ctx: android.content.Context, key: String): File {
        val dir = File(ctx.filesDir, "commit_icons")
        dir.mkdirs()
        return File(dir, "$key.png")
    }

    fun load(ctx: android.content.Context, key: String): Bitmap? {
        val f = file(ctx, key)
        return if (f.exists()) BitmapFactory.decodeFile(f.path) else null
    }

    fun clear(ctx: android.content.Context, key: String) {
        file(ctx, key).delete()
    }

    fun save(ctx: android.content.Context, key: String, uri: Uri): Boolean = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 256 && bounds.outHeight / (sample * 2) >= 256) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return false
        val side = minOf(bmp.width, bmp.height)
        val square = Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
        val small = Bitmap.createScaledBitmap(square, 192, 192, true)
        file(ctx, key).outputStream().use { small.compress(Bitmap.CompressFormat.PNG, 100, it) }
        true
    }.getOrDefault(false)
}

/** The little picture for a payment: the user's own image, a coloured brand mark, or an icon. */
@Composable
internal fun CommitBadge(item: CItem, size: Int, version: Int, onClick: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val key = CommitIcons.key(item)
    val custom = remember(key, version) { CommitIcons.load(ctx, key) }
    val b = remember(item.name, item.kind) { Badges.forName(item.name, item.kind) }
    val letter: String? = b.letter
    val shape = RoundedCornerShape((size * 0.3f).dp)
    val base = Modifier.size(size.dp).clip(shape).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    when {
        custom != null -> Image(custom.asImageBitmap(), contentDescription = null, modifier = base, contentScale = ContentScale.Crop)
        letter != null -> Box(base.background(Color(b.color)), contentAlignment = Alignment.Center) {
            Text(
                letter, color = Color.White, fontWeight = FontWeight.Bold,
                fontSize = (size * (if (letter.length > 1) 0.34f else 0.46f)).sp, maxLines = 1,
            )
        }
        else -> Box(base.background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Text(b.emoji, fontSize = (size * 0.5f).sp)
        }
    }
}

private fun planOf(i: Installment) = InstallmentPlan(i.amount, i.total, i.paid, LocalDate.ofEpochDay(i.firstDue))
private fun circleOf(c: CircleEntity) = Circle(c.share, c.months, c.myTurn, c.paid, LocalDate.ofEpochDay(c.firstDue))

private val filterNames = listOf(
    CFilter.ALL to "All", CFilter.BILLS to "Bills", CFilter.INSTALLMENTS to "Installments",
    CFilter.CIRCLE to "Gam'eya", CFilter.CARDS to "Cards", CFilter.INCOME to "Income",
)

private val dowFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)

/** Which collapsible group a payment belongs to: key and title (a company, credit cards, bills...). */
private fun groupOf(i: CItem, insts: List<Installment>): Pair<String, String> = when (i.kind) {
    CKind.SUB -> "subs" to "Subscriptions"
    CKind.BILL -> "bills" to "Bills"
    CKind.CIRCLE -> "circles" to "Gam'eya"
    CKind.LOAN -> "loans" to "Loans"
    CKind.CARD -> "cards" to "Credit cards"
    CKind.INCOME -> "income" to "Income"
    CKind.INSTALLMENT -> {
        val x = insts.firstOrNull { it.id == i.refId }
        val label = (x?.provider?.trim() ?: "").ifEmpty { x?.type ?: i.name }
        ("p:" + label.lowercase()) to label
    }
}

private fun payKind(k: CKind): String? = when (k) {
    CKind.BILL, CKind.SUB -> "BILL"
    CKind.INSTALLMENT, CKind.CARD -> "INSTALLMENT"
    CKind.CIRCLE -> "CIRCLE"
    else -> null
}

/** A payment can be ticked when it is unpaid, in the current month, and is a bill, installment or gam'eya. */
private fun selectable(i: CItem, off: Int) = off == 0 && !i.paid && !i.incoming && payKind(i.kind) != null
private fun selKey(i: CItem) = i.kind.name + ":" + i.refId + ":" + i.date.toEpochDay()

/** Ticked payments as one line per item with a count, the shape [Repo.payGroup] takes. */
private fun selectedLines(items: List<CItem>): List<PayItem> =
    items.groupBy { (payKind(it.kind) ?: "") + it.refId }.values.map { g ->
        PayItem(payKind(g.first().kind) ?: "", g.first().refId, g.first().name, g.size, g.sumOf { it.amount })
    }

/** When a group of installments is finished and how much is left in it, or null for bills. */
private fun groupPayoff(key: String, insts: List<Installment>, circles: List<CircleEntity>): Pair<LocalDate, Double>? {
    val plans = ArrayList<InstallmentPlan>()
    if (key == "circles") circles.forEach { plans += circleOf(it).plan }
    else if (key == "cards" || key.startsWith("p:")) insts.forEach { i ->
        val card = i.type.equals("Credit card", ignoreCase = true)
        val label = i.provider.trim().ifEmpty { i.type }
        if ((if (card) "cards" else "p:" + label.lowercase()) == key) plans += planOf(i)
    } else return null
    val live = plans.filter { !it.done }
    if (live.isEmpty()) return null
    return live.maxOf { it.endDate } to live.sumOf { it.remainingAmount }
}

/** Everything due or paid inside one budget cycle, as one list. */
private fun buildItems(
    bills: List<Bill>, insts: List<Installment>, circles: List<CircleEntity>, loans: List<Loan>,
    txns: List<app.pocketta.data.Txn>, prefs: Prefs, repo: Repo, today: LocalDate, cycle: Cycle, off: Int,
): List<CItem> {
    val out = ArrayList<CItem>()
    // The current month also lists what is late; other months only list their own payments.
    val epoch = if (off == 0) LocalDate.ofEpochDay(0) else cycle.start

    for (b in if (off < 0) emptyList() else bills) {
        val f = Frequency.parse(b.frequency)
        val first = LocalDate.ofEpochDay(b.nextDue)
        val dates = Recurrence.occurrences(first, f, b.everyN, first, cycle.nextStart, b.anchorDay)
        dates.forEachIndexed { idx, d ->
            if (d.isBefore(epoch)) return@forEachIndexed
            out += CItem(
                if (b.kind == "SUB") CKind.SUB else CKind.BILL, b.id, b.name,
                Recurrence.describe(f, b.everyN), b.amount * prefs.rateOf(b.currency), d, paid = false, payable = idx == 0 && off == 0,
            )
        }
    }
    for (i in if (off < 0) emptyList() else insts) {
        val p = planOf(i)
        val kind = if (i.type.equals("Credit card", ignoreCase = true)) CKind.CARD else CKind.INSTALLMENT
        p.unpaidDueIn(epoch, cycle.nextStart).forEachIndexed { idx, d ->
            out += CItem(kind, i.id, i.name, "${i.paid + idx + 1} of ${i.total}", i.amount, d, paid = false, payable = idx == 0 && off == 0)
        }
    }
    for (c in if (off < 0) emptyList() else circles) {
        circleOf(c).plan.unpaidDueIn(epoch, cycle.nextStart).forEachIndexed { idx, d ->
            out += CItem(CKind.CIRCLE, c.id, c.name, "Gam'eya · ${c.paid + idx + 1} of ${c.months}", c.share, d, paid = false, payable = idx == 0 && off == 0)
        }
    }
    for (l in if (off < 0) emptyList() else loans) {
        val due = l.dueDay ?: continue
        if (l.closed) continue
        val d = LocalDate.ofEpochDay(due)
        if (!d.isBefore(cycle.nextStart) || d.isBefore(epoch)) continue
        out += CItem(
            CKind.LOAN, l.id, l.person, if (l.lent) "They owe you" else "You owe", l.remaining, d,
            paid = false, incoming = l.lent,
        )
    }

    // Money already paid this cycle.
    for (t in txns) {
        if (t.type != TxType.EXPENSE) continue
        val kind = when (t.source) {
            Source.BILL -> bills.firstOrNull { it.id == t.sourceId }?.let { if (it.kind == "SUB") CKind.SUB else CKind.BILL } ?: CKind.BILL
            Source.INSTALLMENT -> insts.firstOrNull { it.id == t.sourceId }?.let { if (it.type.equals("Credit card", ignoreCase = true)) CKind.CARD else CKind.INSTALLMENT } ?: CKind.INSTALLMENT
            Source.CIRCLE -> CKind.CIRCLE
            else -> continue
        }
        out += CItem(kind, t.sourceId, t.note, "Paid", t.base, LocalDate.ofEpochDay(t.epochDay), paid = true)
    }

    // Salary: received (paid) or still expected.
    val received = txns.firstOrNull { it.source == Source.SALARY }
    if (received != null) {
        out += CItem(CKind.INCOME, 0, "Salary", "Received", received.base, LocalDate.ofEpochDay(received.epochDay), paid = true, incoming = true)
    } else if (prefs.salary > 0 && off >= 0) {
        repo.salaryDateIn(cycle, prefs.salaryDay)?.let { d ->
            out += CItem(CKind.INCOME, 0, "Salary", "Expected", prefs.salary, d, paid = false, incoming = true)
        }
    }
    return out
}

@Composable
fun CommitOverview(repo: Repo, nav: Nav, prefs: Prefs, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val today = LocalDate.now()
    var off by remember { mutableIntStateOf(0) }
    val cycle = remember(off, prefs.cycleStart) {
        var c = CycleMath.cycleFor(today, prefs.cycleStart)
        repeat(kotlin.math.abs(off)) { c = if (off > 0) CycleMath.cycleFor(c.nextStart, prefs.cycleStart) else CycleMath.cycleFor(c.start.minusDays(1), prefs.cycleStart) }
        c
    }

    val billsFlow = remember { repo.dao.billsFlow() }
    val instFlow = remember { repo.dao.installmentsFlow() }
    val circFlow = remember { repo.dao.circlesFlow() }
    val loanFlow = remember { repo.dao.loansFlow() }
    val txnFlow = remember(cycle.start) { repo.dao.txnsFlow(cycle.start.toEpochDay(), cycle.nextStart.toEpochDay()) }
    val bills by billsFlow.collectAsState(initial = emptyList())
    val insts by instFlow.collectAsState(initial = emptyList())
    val circles by circFlow.collectAsState(initial = emptyList())
    val loans by loanFlow.collectAsState(initial = emptyList())
    val txns by txnFlow.collectAsState(initial = emptyList())
    val categories by remember { repo.dao.categoriesFlow() }.collectAsState(initial = emptyList())

    var filter by remember { mutableStateOf(CFilter.ALL) }
    var selected by remember { mutableStateOf<LocalDate?>(null) }
    var version by remember { mutableIntStateOf(0) }
    var iconFor by remember { mutableStateOf<CItem?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    var editBill by remember { mutableStateOf<Bill?>(null) }
    var editInst by remember { mutableStateOf<Installment?>(null) }
    var editCircle by remember { mutableStateOf<CircleEntity?>(null) }
    var newLoan by remember { mutableStateOf(false) }
    var openLoan by remember { mutableStateOf<Loan?>(null) }
    var payVariable by remember { mutableStateOf<Bill?>(null) }
    var sel by remember { mutableStateOf(setOf<String>()) }
    var openGroups by remember { mutableStateOf(mapOf<String, Boolean>()) }
    var confirmItems by remember { mutableStateOf<List<CItem>?>(null) }
    var balance by remember { mutableStateOf(0.0) }
    LaunchedEffect(off) { sel = emptySet() }

    // Mark a whole group (a provider, credit cards, gam'eya...) as paid in one step, with undo.
    var bulkOpen by remember { mutableStateOf(false) }
    var bulkMonth by remember { mutableIntStateOf(0) }
    var groups by remember { mutableStateOf<List<PayGroup>>(emptyList()) }
    var confirmGroup by remember { mutableStateOf<PayGroup?>(null) }
    var lastBulk by remember { mutableStateOf<Pair<PayUndo, String>?>(null) }
    val monthStart = if (bulkMonth == 0) today else cycle.nextStart
    LaunchedEffect(bills, insts, circles, bulkMonth) { groups = repo.groupPayments(monthStart) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val target = iconFor
        if (uri != null && target != null && CommitIcons.save(ctx, CommitIcons.key(target), uri)) version++
        iconFor = null
    }

    val all = remember(bills, insts, circles, loans, txns, prefs, today, cycle) { buildItems(bills, insts, circles, loans, txns, prefs, repo, today, cycle, off) }
    val filtered = all.filter { Commitments.matches(filter, it) }
    val left = Commitments.leftToPay(all)
    val leftCount = Commitments.leftCount(all)
    val shown = selected?.let { s -> filtered.filter { it.date == s } } ?: filtered
    val late = Commitments.late(shown, today)
    val later = Commitments.later(shown, today)
    val done = Commitments.done(shown)
    val byDay = Commitments.byDay(filtered)
    val plainByDay = txns.filter { it.source == Source.NONE && it.type != TxType.TRANSFER }.groupBy { LocalDate.ofEpochDay(it.epochDay) }
    val monthName = if (cycle.start.dayOfMonth == 1) cycle.start.format(monthFmt) + " " + cycle.start.year
    else "${cycle.start.format(dayFmt)} – ${cycle.end.format(dayFmt)}"

    fun open(it: CItem) {
        when (it.kind) {
            CKind.BILL, CKind.SUB -> editBill = bills.firstOrNull { b -> b.id == it.refId }
            CKind.INSTALLMENT, CKind.CARD -> editInst = insts.firstOrNull { i -> i.id == it.refId }
            CKind.CIRCLE -> editCircle = circles.firstOrNull { c -> c.id == it.refId }
            CKind.LOAN -> openLoan = loans.firstOrNull { l -> l.id == it.refId }
            CKind.INCOME -> {}
        }
    }

    fun pay(it: CItem) {
        scope.launch {
            when (it.kind) {
                CKind.BILL, CKind.SUB -> bills.firstOrNull { b -> b.id == it.refId }?.let { b -> if (b.variable) payVariable = b else repo.payBill(b) }
                CKind.INSTALLMENT, CKind.CARD -> insts.firstOrNull { i -> i.id == it.refId }?.let { i -> repo.payInstallment(i) }
                CKind.CIRCLE -> circles.firstOrNull { c -> c.id == it.refId }?.let { c -> repo.payCircle(c) }
                else -> {}
            }
        }
    }

    fun toggle(item: CItem) {
        val same = all.filter { x -> selectable(x, off) && payKind(x.kind) == payKind(item.kind) && x.refId == item.refId }
        val on = selKey(item) !in sel
        val keys = same.filter { x -> if (on) !x.date.isAfter(item.date) else !x.date.isBefore(item.date) }.map { x -> selKey(x) }
        sel = if (on) sel + keys else sel - keys.toSet()
    }

    fun toggleGroup(items: List<CItem>) {
        val keys = items.filter { x -> selectable(x, off) }.map { x -> selKey(x) }
        sel = if (keys.all { k -> k in sel }) sel - keys.toSet() else sel + keys
    }

    /** Swipe: pay this payment (and any earlier ones of the same item, in date order). */
    fun paySingle(item: CItem) {
        val earlier = all.filter { x -> selectable(x, off) && payKind(x.kind) == payKind(item.kind) && x.refId == item.refId && !x.date.isAfter(item.date) }
        scope.launch {
            val undo = repo.payGroup(PayGroup("one", item.name, selectedLines(earlier)))
            lastBulk = undo to item.name + " paid · " + Money.short(item.amount) + " " + prefs.baseCurrency
        }
    }

    @Composable
    fun Line(item: CItem) {
        val can = selectable(item, off)
        val isLate = !item.paid && !item.incoming && item.date.isBefore(today)
        var drag by remember { mutableFloatStateOf(0f) }
        Row(
            Modifier.fillMaxWidth()
                .pointerInput(item.kind, item.refId, item.date) {
                    detectTapGestures(onTap = { open(item) }, onLongPress = { if (can) confirmItems = listOf(item) })
                }
                .pointerInput(item.kind, item.refId, item.date, can) {
                    if (can) detectHorizontalDragGestures(
                        onDragEnd = { if (kotlin.math.abs(drag) > 160f) paySingle(item); drag = 0f },
                        onDragCancel = { drag = 0f },
                    ) { _, dx -> drag += dx }
                }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (can) Checkbox(checked = selKey(item) in sel, onCheckedChange = { toggle(item) })
            else if (item.paid && !item.incoming) Checkbox(checked = true, onCheckedChange = null, enabled = false)
            else Spacer(Modifier.width(8.dp))
            CommitBadge(item, 40, version, onClick = { iconFor = item })
            Column(Modifier.weight(1f)) {
                Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(
                    item.date.format(dowFmt) + " · " + (if (isLate) "Late · " else "") + item.sub, style = MaterialTheme.typography.bodySmall,
                    color = if (isLate) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                (if (item.incoming) "+" else "") + Money.fmt(item.amount), fontWeight = FontWeight.SemiBold,
                color = if (item.incoming) Good else if (item.paid) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
            )
        }
    }

    // Groups by company or type, biggest first; paid ones and income go below.
    val groupList = shown.filter { !it.paid && it.kind != CKind.INCOME }.groupBy { groupOf(it, insts) }
        .entries.sortedByDescending { e -> e.value.sumOf { x -> x.amount } }
    val paidList = Commitments.done(shown).filter { it.kind != CKind.INCOME }
    val incomeList = shown.filter { it.kind == CKind.INCOME }
    val dueNow = if (off == 0) all.filter { selectable(it, 0) && !it.date.isAfter(today) } else emptyList()
    val pickedItems = all.filter { selectable(it, off) && selKey(it) in sel }
    val monthTotal = all.filter { !it.incoming }.sumOf { it.amount }
    val outlook = remember(bills, insts, circles, loans, prefs, today, off) {
        (0..3).map { o ->
            var c = CycleMath.cycleFor(today, prefs.cycleStart)
            repeat(o) { c = CycleMath.cycleFor(c.nextStart, prefs.cycleStart) }
            c.start to buildItems(bills, insts, circles, loans, if (o == 0) txns else emptyList(), prefs, repo, today, c, o).filter { !it.incoming }.sumOf { it.amount }
        }
    }

    Box(modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                SectionCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), container = MaterialTheme.colorScheme.primaryContainer) {
                    Label("Left to pay this month")
                    Big(Money.short(left) + " " + prefs.baseCurrency, size = 34)
                    Text(
                        if (leftCount == 0) "Nothing left to pay. Well done!" else if (leftCount == 1) "1 payment to go" else "$leftCount payments to go",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(monthName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (off == 0 && monthTotal > 0) {
                        Gap(8)
                        androidx.compose.material3.LinearProgressIndicator(progress = { ((monthTotal - left) / monthTotal).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        Text("Paid " + Money.short(monthTotal - left) + " of " + Money.short(monthTotal), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            lastBulk?.let { (undo, text) ->
                item {
                    SectionCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), container = MaterialTheme.colorScheme.tertiaryContainer) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("✓ $text", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            TextButton(onClick = { scope.launch { repo.undoPayGroup(undo); lastBulk = null } }) { Text("Undo") }
                            TextButton(onClick = { lastBulk = null }) { Text("OK") }
                        }
                    }
                }
            }
            if (dueNow.isNotEmpty()) item {
                val sum = dueNow.sumOf { it.amount }
                val lateN = dueNow.count { it.date.isBefore(today) }
                SectionCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), container = MaterialTheme.colorScheme.errorContainer) {
                    Text("⚠ Due today or late", fontWeight = FontWeight.Bold)
                    Text(
                        dueNow.size.toString() + (if (dueNow.size == 1) " payment" else " payments") + " · " + Money.short(sum) + " " + prefs.baseCurrency +
                            (if (lateN > 0) " · $lateN late" else ""),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Button(onClick = { confirmItems = dueNow }) { Text("Pay them all") }
                        TextButton(onClick = {
                            sel = sel + dueNow.map { selKey(it) }
                            openGroups = openGroups + dueNow.associate { groupOf(it, insts).first to true }
                        }) { Text("Review first") }
                    }
                }
            }
            if (off == 0) item {
                SectionCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Label("Coming months")
                    val top = outlook.maxOf { it.second }.coerceAtLeast(1.0)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                        outlook.forEachIndexed { idx, (start, total) ->
                            Column(
                                Modifier.weight(1f).clickable { off = idx; selected = null },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    Modifier.fillMaxWidth().height((8 + 64 * (total / top)).dp).clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = if (idx == 0) 1f else 0.55f)),
                                )
                                Text(start.format(monthFmt), fontSize = 11.sp)
                                Text(Money.short(total), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    if (prefs.salary > 0 && outlook[1].second > 0) {
                        Gap(4)
                        Text(
                            outlook[1].first.format(monthFmt) + " · " + Math.round(outlook[1].second / prefs.salary * 100) + "% of your salary",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (off == 0 && leftCount > 0) item {
                OutlinedButton(
                    onClick = { confirmItems = all.filter { selectable(it, 0) } },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) { Text("Pay all of " + cycle.start.format(monthFmt) + " (" + Money.short(left) + ")") }
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((f, name) in filterNames) FilterChip(selected = filter == f, onClick = { filter = f; selected = null }, label = { Text(name) })
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { off--; selected = null }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month") }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(monthName, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        if (off != 0) TextButton(onClick = { off = 0; selected = null }) { Text("This month") }
                    }
                    IconButton(onClick = { off++; selected = null }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month") }
                }
            }
            item {
                SectionCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        for (n in listOf("Sa", "Su", "Mo", "Tu", "We", "Th", "Fr")) {
                            Text(n, Modifier.weight(1f), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                    val offset = (cycle.start.dayOfWeek.value + 1) % 7
                    val cells = offset + cycle.lengthDays
                    val weeks = (cells + 6) / 7
                    for (w in 0 until weeks) {
                        Row(Modifier.fillMaxWidth()) {
                            for (c in 0 until 7) {
                                val idx = w * 7 + c - offset
                                if (idx < 0 || idx >= cycle.lengthDays) {
                                    Spacer(Modifier.weight(1f).height(54.dp))
                                } else {
                                    val d = cycle.start.plusDays(idx.toLong())
                                    val items = byDay[d].orEmpty()
                                    val isSel = selected == d
                                    val isToday = d == today
                                    Column(
                                        Modifier.weight(1f).height(54.dp).padding(1.dp).clip(RoundedCornerShape(10.dp))
                                            .then(if (isSel) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier)
                                            .then(if (isToday) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)) else Modifier)
                                            .clickable { selected = if (isSel) null else d },
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Text(
                                            "${d.dayOfMonth}" + if (plainByDay[d].orEmpty().isNotEmpty()) " •" else "", fontSize = 12.sp,
                                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                                            for (it in items.take(2)) CommitBadge(it, 16, version)
                                        }
                                        if (items.size > 2) Text("+${items.size - 2}", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            selected?.let { s ->
                item {
                    Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Showing " + s.format(dayFmt), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { selected = null }) { Text("Show all") }
                    }
                }
                item {
                    FilledTonalButton(
                        onClick = { nav.open(Route.Add(day = s.toEpochDay())) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) { Text("Add expense on " + s.format(dayFmt)) }
                }
                val plain = plainByDay[s].orEmpty()
                if (plain.isNotEmpty()) {
                    item { Label("Spent that day", Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp)) }
                    items(plain.size) { TxnRow(plain[it], categories) { nav.open(Route.Add(txnId = plain[it].id)) } }
                }
            }
            if (groupList.isEmpty() && paidList.isEmpty()) item {
                Text("Nothing here.", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            for (e in groupList) {
                val key = e.key.first
                val list = e.value.sortedBy { it.date }
                val pick = list.filter { selectable(it, off) }
                val nSel = pick.count { selKey(it) in sel }
                val hasLate = list.any { !it.incoming && it.date.isBefore(today) }
                val isOpen = openGroups[key] ?: hasLate
                val paidIn = all.filter { it.paid && !it.incoming && groupOf(it, insts).first == key }.sumOf { it.amount }
                val payoff = groupPayoff(key, insts, circles)
                item(key = "g-$key") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant).clickable { openGroups = openGroups + (key to !isOpen) }.padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (pick.isNotEmpty()) TriStateCheckbox(
                            state = if (nSel == 0) ToggleableState.Off else if (nSel == pick.size) ToggleableState.On else ToggleableState.Indeterminate,
                            onClick = { toggleGroup(list) },
                        )
                        Column(Modifier.weight(1f).padding(start = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(e.key.second, fontWeight = FontWeight.Bold)
                                if (hasLate) Text("  Late", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                list.size.toString() + (if (list.size == 1) " payment" else " payments") + " · " + Money.short(list.sumOf { it.amount }) + " left" +
                                    (if (paidIn > 0) " · " + Money.short(paidIn) + " paid" else ""),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (payoff != null) Text(
                                "Done by " + payoff.first.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.US)) + " · " + Money.short(payoff.second) + " left in total",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(if (isOpen) "⌃" else "⌄", fontSize = 20.sp, modifier = Modifier.padding(horizontal = 8.dp))
                    }
                }
                if (isOpen) items(list.size, key = { "g-$key-${selKey(list[it])}" }) { Line(list[it]) }
            }
            if (paidList.isNotEmpty()) {
                val isOpen = openGroups["paid"] ?: false
                item(key = "g-paid") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant).clickable { openGroups = openGroups + ("paid" to !isOpen) }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Paid", fontWeight = FontWeight.Bold)
                            Text(
                                paidList.size.toString() + (if (paidList.size == 1) " payment" else " payments") + " · " + Money.short(paidList.sumOf { it.amount }),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(if (isOpen) "⌃" else "⌄", fontSize = 20.sp)
                    }
                }
                if (isOpen) items(paidList.size, key = { "paid-$it-${paidList[it].refId}" }) { Line(paidList[it]) }
            }
            if (incomeList.isNotEmpty()) {
                item { Label("Income", Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp)) }
                items(incomeList.size) { Line(incomeList[it]) }
            }
            item { Gap(96) }
        }
        if (pickedItems.isNotEmpty()) {
            Row(
                Modifier.align(Alignment.BottomCenter).padding(12.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.primary).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(pickedItems.size.toString() + " selected", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    Text(Money.short(pickedItems.sumOf { it.amount }) + " " + prefs.baseCurrency, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.bodySmall)
                }
                androidx.compose.material3.Button(
                    onClick = { confirmItems = pickedItems },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = MaterialTheme.colorScheme.primary),
                ) { Text("Mark as paid") }
            }
        }
        if (pickedItems.isEmpty()) Box(Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            ExtendedFloatingActionButton(
                onClick = { menuOpen = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add") },
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                val blank = Bill(name = "", amount = 0.0, nextDue = today.toEpochDay(), anchorDay = today.dayOfMonth)
                DropdownMenuItem(text = { Text("Bill") }, onClick = { menuOpen = false; editBill = blank })
                DropdownMenuItem(text = { Text("Subscription") }, onClick = { menuOpen = false; editBill = blank.copy(kind = "SUB") })
                DropdownMenuItem(text = { Text("Installment") }, onClick = {
                    menuOpen = false
                    editInst = Installment(name = "", type = "Consumer finance", amount = 0.0, total = 12, firstDue = today.toEpochDay())
                })
                DropdownMenuItem(text = { Text("Credit card") }, onClick = {
                    menuOpen = false
                    editInst = Installment(name = "", type = "Credit card", amount = 0.0, total = 12, firstDue = today.toEpochDay())
                })
                DropdownMenuItem(text = { Text("Gam'eya") }, onClick = {
                    menuOpen = false
                    editCircle = CircleEntity(name = "", share = 0.0, months = 10, myTurn = 1, firstDue = today.toEpochDay())
                })
                DropdownMenuItem(text = { Text("Loan") }, onClick = { menuOpen = false; newLoan = true })
            }
        }
    }

    editBill?.let { BillEditor(repo, prefs, it) { editBill = null } }
    editInst?.let { InstallmentEditor(repo, it, insts.map { x -> x.type }) { editInst = null } }
    editCircle?.let { CircleEditor(repo, it) { editCircle = null } }
    if (newLoan) NewLoanDialog(repo, prefs) { newLoan = false }
    openLoan?.let { l -> LoanDialog(repo, prefs, loans.firstOrNull { it.id == l.id } ?: l) { openLoan = null } }
    payVariable?.let { b -> PayBillDialog(repo, b) { payVariable = null } }

    if (bulkOpen) {
        AlertDialog(
            onDismissRequest = { bulkOpen = false },
            title = { Text("Mark a whole group as paid") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = bulkMonth == 0, onClick = { bulkMonth = 0 }, label = { Text("This month") })
                        FilterChip(selected = bulkMonth == 1, onClick = { bulkMonth = 1 }, label = { Text("Next month") })
                    }
                    if (groups.isEmpty()) Text("Nothing to pay in this month.")
                    for (g in groups) {
                        Row(
                            Modifier.fillMaxWidth().clickable { confirmGroup = g; bulkOpen = false }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(g.title, fontWeight = FontWeight.SemiBold)
                                Text(g.items.size.toString() + (if (g.items.size == 1) " item" else " items"), style = MaterialTheme.typography.bodySmall)
                            }
                            Text(Money.short(g.total), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { bulkOpen = false }) { Text("Cancel") } },
        )
    }
    confirmGroup?.let { g ->
        AlertDialog(
            onDismissRequest = { confirmGroup = null },
            title = { Text("${g.title}: mark all as paid") },
            text = {
                Column {
                    Text("These payments will be recorded as paid now:")
                    Gap(6)
                    for (it in g.items) Row(Modifier.fillMaxWidth()) {
                        Text(it.name + if (it.n > 1) " × ${it.n}" else "", Modifier.weight(1f))
                        Text(Money.short(it.amount))
                    }
                    Gap(6)
                    Row(Modifier.fillMaxWidth()) {
                        Text("Total", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text(Money.short(g.total) + " " + prefs.baseCurrency, fontWeight = FontWeight.Bold)
                    }
                    Gap(6)
                    Text("You can undo it right after.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmGroup = null
                    scope.launch {
                        val undo = repo.payGroup(g)
                        lastBulk = undo to g.title + ": " + g.items.size + (if (g.items.size == 1) " item" else " items") + " paid"
                    }
                }) { Text("Mark all as paid") }
            },
            dismissButton = { TextButton(onClick = { confirmGroup = null }) { Text("Cancel") } },
        )
    }

    confirmItems?.let { items ->
        val lines = selectedLines(items)
        val keyOf = { l: PayItem -> l.kind + l.id }
        val variable = lines.filter { l -> l.kind == "BILL" && bills.firstOrNull { it.id == l.id }?.variable == true }.map(keyOf).toSet()
        var amounts by remember(items) { mutableStateOf(lines.associate { keyOf(it) to Math.round(it.amount / it.n).toString() }) }
        LaunchedEffect(items) { balance = repo.walletTotal(prefs) }
        val total = lines.sumOf { l -> if (keyOf(l) in variable) (amounts[keyOf(l)]?.toDoubleOrNull() ?: 0.0) * l.n else l.amount }
        AlertDialog(
            onDismissRequest = { confirmItems = null },
            title = { Text("Mark ${items.size} ${if (items.size == 1) "payment" else "payments"} as paid") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    for (l in lines) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(l.name + if (l.n > 1) " × ${l.n}" else "", Modifier.weight(1f))
                        if (keyOf(l) in variable) OutlinedTextField(
                            value = amounts[keyOf(l)] ?: "", onValueChange = { v -> amounts = amounts + (keyOf(l) to v) },
                            singleLine = true, modifier = Modifier.width(110.dp),
                        ) else Text(Money.short(l.amount), fontWeight = FontWeight.SemiBold)
                    }
                    Gap(8)
                    Row(Modifier.fillMaxWidth()) {
                        Text("Total", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text(Money.short(total) + " " + prefs.baseCurrency, fontWeight = FontWeight.Bold)
                    }
                    Row(Modifier.fillMaxWidth()) {
                        Text("Money left after paying", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        Text(Money.short(balance - total) + " " + prefs.baseCurrency, style = MaterialTheme.typography.bodySmall)
                    }
                    Gap(6)
                    Text("You can undo it right after.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmItems = null
                    sel = emptySet()
                    val paying = lines.map { l -> if (keyOf(l) in variable) l.copy(each = amounts[keyOf(l)]?.toDoubleOrNull()?.takeIf { it > 0 }) else l }
                    scope.launch {
                        val undo = repo.payGroup(PayGroup("sel", "sel", paying))
                        lastBulk = undo to items.size.toString() + (if (items.size == 1) " payment" else " payments") + " paid · " + Money.short(total) + " " + prefs.baseCurrency
                    }
                }) { Text("Mark as paid") }
            },
            dismissButton = { TextButton(onClick = { confirmItems = null }) { Text("Cancel") } },
        )
    }

    iconFor?.let { item ->
        val key = CommitIcons.key(item)
        val hasCustom = remember(key, version) { CommitIcons.load(ctx, key) != null }
        AlertDialog(
            onDismissRequest = { iconFor = null },
            title = { Text("Picture for ${item.name}") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CommitBadge(item, 64, version)
                    Text("Pick a photo or logo from your gallery to show here.")
                }
            },
            confirmButton = { TextButton(onClick = { picker.launch("image/*") }) { Text("Pick from gallery") } },
            dismissButton = {
                Row {
                    if (hasCustom) TextButton(onClick = { CommitIcons.clear(ctx, key); version++; iconFor = null }) { Text("Use default") }
                    TextButton(onClick = { iconFor = null }) { Text("Cancel") }
                }
            },
        )
    }
}
