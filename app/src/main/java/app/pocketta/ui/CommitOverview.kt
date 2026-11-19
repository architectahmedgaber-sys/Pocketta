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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pocketta.core.Badges
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

/** Everything due or paid inside one budget cycle, as one list. */
private fun buildItems(
    bills: List<Bill>, insts: List<Installment>, circles: List<CircleEntity>, loans: List<Loan>,
    txns: List<app.pocketta.data.Txn>, prefs: Prefs, repo: Repo, today: LocalDate,
): List<CItem> {
    val cycle = CycleMath.cycleFor(today, prefs.cycleStart)
    val out = ArrayList<CItem>()
    val epoch = LocalDate.ofEpochDay(0)

    for (b in bills) {
        val f = Frequency.parse(b.frequency)
        val first = LocalDate.ofEpochDay(b.nextDue)
        val dates = Recurrence.occurrences(first, f, b.everyN, first, cycle.nextStart, b.anchorDay)
        dates.forEachIndexed { idx, d ->
            out += CItem(
                if (b.kind == "SUB") CKind.SUB else CKind.BILL, b.id, b.name,
                Recurrence.describe(f, b.everyN), b.amount * prefs.rateOf(b.currency), d, paid = false, payable = idx == 0,
            )
        }
    }
    for (i in insts) {
        val p = planOf(i)
        val kind = if (i.type.equals("Credit card", ignoreCase = true)) CKind.CARD else CKind.INSTALLMENT
        p.unpaidDueIn(epoch, cycle.nextStart).forEachIndexed { idx, d ->
            out += CItem(kind, i.id, i.name, "${i.paid + idx + 1} of ${i.total}", i.amount, d, paid = false, payable = idx == 0)
        }
    }
    for (c in circles) {
        circleOf(c).plan.unpaidDueIn(epoch, cycle.nextStart).forEachIndexed { idx, d ->
            out += CItem(CKind.CIRCLE, c.id, c.name, "Gam'eya · ${c.paid + idx + 1} of ${c.months}", c.share, d, paid = false, payable = idx == 0)
        }
    }
    for (l in loans) {
        val due = l.dueDay ?: continue
        if (l.closed) continue
        val d = LocalDate.ofEpochDay(due)
        if (!d.isBefore(cycle.nextStart)) continue
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
    } else if (prefs.salary > 0) {
        repo.salaryDateIn(cycle, prefs.salaryDay)?.let { d ->
            out += CItem(CKind.INCOME, 0, "Salary", "Expected", prefs.salary, d, paid = false, incoming = true)
        }
    }
    return out
}

@Composable
fun CommitOverview(repo: Repo, prefs: Prefs, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val today = LocalDate.now()
    val cycle = CycleMath.cycleFor(today, prefs.cycleStart)

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

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val target = iconFor
        if (uri != null && target != null && CommitIcons.save(ctx, CommitIcons.key(target), uri)) version++
        iconFor = null
    }

    val all = remember(bills, insts, circles, loans, txns, prefs, today) { buildItems(bills, insts, circles, loans, txns, prefs, repo, today) }
    val filtered = all.filter { Commitments.matches(filter, it) }
    val left = Commitments.leftToPay(all)
    val leftCount = Commitments.leftCount(all)
    val shown = selected?.let { s -> filtered.filter { it.date == s } } ?: filtered
    val late = Commitments.late(shown, today)
    val later = Commitments.later(shown, today)
    val done = Commitments.done(shown)
    val byDay = Commitments.byDay(filtered)
    val monthName = if (cycle.start.dayOfMonth == 1) cycle.start.format(monthFmt)
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

    @Composable
    fun Line(it: CItem) {
        Row(
            Modifier.fillMaxWidth().clickable { open(it) }.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CommitBadge(it, 44, version, onClick = { iconFor = it })
            Column(Modifier.weight(1f)) {
                Text(it.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(
                    it.date.format(dowFmt) + " · " + it.sub, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                (if (it.incoming) "+" else "") + Money.fmt(it.amount), fontWeight = FontWeight.SemiBold,
                color = if (it.incoming) Good else if (it.paid) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
            )
            if (it.payable && !it.paid && !it.incoming) TextButton(onClick = { pay(it) }) { Text("Paid") }
        }
    }

    Box(modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                SectionCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), container = MaterialTheme.colorScheme.primaryContainer) {
                    Label("Left to pay this month")
                    Big(Money.short(left) + " " + prefs.baseCurrency, size = 34)
                    Text(
                        if (leftCount == 0) "Nothing left to pay. Well done!" else "$leftCount payments to go",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(monthName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((f, name) in filterNames) FilterChip(selected = filter == f, onClick = { filter = f; selected = null }, label = { Text(name) })
                }
            }
            item {
                SectionCard(Modifier.padding(16.dp)) {
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
                                            "${d.dayOfMonth}", fontSize = 12.sp,
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
            }
            if (late.isNotEmpty()) {
                item { Label("Late", Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp)) }
                items(late.size) { Line(late[it]) }
            }
            item { Label("Later this month", Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp)) }
            if (later.isEmpty()) item { Text("Nothing here.", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(later.size) { Line(later[it]) }
            if (done.isNotEmpty()) {
                item { Label("Paid", Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp)) }
                items(done.size) { Line(done[it]) }
            }
            item { Gap(96) }
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
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
