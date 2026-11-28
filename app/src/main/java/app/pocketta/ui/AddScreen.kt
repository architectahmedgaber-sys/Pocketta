package app.pocketta.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.FlowRow
import app.pocketta.core.CycleMath
import app.pocketta.core.LimitAlerts
import app.pocketta.core.Money
import app.pocketta.core.QuickText
import app.pocketta.data.Category
import app.pocketta.data.Prefs
import app.pocketta.data.Repo
import app.pocketta.data.Source
import app.pocketta.data.Txn
import app.pocketta.data.Tags
import app.pocketta.data.TxType
import app.pocketta.notify.Notifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

/** The third kind on the add screen: money put in the piggy bank. It is saved as an expense that does not count as spending. */
const val PIGGY = "PIGGY"

@Composable
fun AddScreen(repo: Repo, nav: Nav, prefs: Prefs, route: Route.Add) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val categoriesFlow = remember { repo.dao.categoriesFlow() }
    val categories by categoriesFlow.collectAsState(initial = emptyList())

    var editing by remember { mutableStateOf<Txn?>(null) }
    var type by remember { mutableStateOf(route.type) }
    var input by remember { mutableStateOf("") }
    var noteField by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var parentId by remember { mutableStateOf<Long?>(null) }
    var pickedByHand by remember { mutableStateOf(false) }
    var walletId by remember { mutableLongStateOf(0L) }
    var toWalletId by remember { mutableLongStateOf(0L) }
    var currency by remember { mutableStateOf(prefs.baseCurrency) }
    var date by remember { mutableStateOf(if (route.day != 0L) LocalDate.ofEpochDay(route.day) else LocalDate.now()) }
    var person by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf<LocalDate?>(null) }
    var tagsText by remember { mutableStateOf("") }
    var knownTags by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) { knownTags = repo.allTags().take(10) }
    var loaded by remember { mutableStateOf(route.txnId == 0L) }
    // Piggy bank as a kind of spending: where it goes (-1 = general piggy bank, 0 = main goal, n = another goal).
    var goal by remember { mutableLongStateOf(-1L) }
    var need by remember { mutableStateOf("") }
    var how by remember { mutableStateOf("") }
    var place by remember { mutableStateOf("") }
    var recent by remember { mutableStateOf<List<Repo.RecentItem>>(emptyList()) }
    LaunchedEffect(Unit) { recent = repo.recentItems() }

    // Load the transaction being edited, or pick sensible defaults for a new one.
    LaunchedEffect(route.txnId, categories.isNotEmpty()) {
        if (categories.isEmpty()) return@LaunchedEffect
        if (route.txnId != 0L && editing == null) {
            val t = repo.dao.txn(route.txnId) ?: return@LaunchedEffect
            editing = t
            type = t.type
            input = numText(t.amount)
            noteField = t.note
            tagsText = t.tagList.joinToString(", ")
            need = t.need; how = t.how; place = t.place
            categoryId = t.categoryId
            parentId = categories.firstOrNull { it.id == t.categoryId }?.let { it.parentId ?: it.id }
            pickedByHand = true
            walletId = t.walletId
            toWalletId = t.toWalletId ?: 0L
            currency = t.currency
            date = LocalDate.ofEpochDay(t.epochDay)
            loaded = true
        } else if (walletId == 0L) {
            walletId = repo.newRecordWallet()
            if (route.amount > 0 && input.isEmpty()) input = numText(route.amount)
            if (route.note.isNotBlank() && noteField.isEmpty()) noteField = route.note
            if (route.currency.isNotBlank() && route.currency in prefs.currencies) currency = route.currency
            val wanted = if (route.category != 0L) route.category else prefs.lastCategory
            if (type == TxType.EXPENSE && wanted != 0L) {
                val c = categories.firstOrNull { it.id == wanted && !it.archived }
                if (c != null) { categoryId = c.id; parentId = c.parentId ?: c.id }
            }
        }
    }

    // Receipt photo: read the total and fill in the amount.
    var receiptUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var scanState by remember { mutableStateOf<String?>(null) }
    fun readReceipt(uri: android.net.Uri?) {
        if (uri == null) return
        scanState = "Reading the receipt..."
        scope.launch {
            val text = app.pocketta.scan.ReceiptScanner.read(context, uri)
            val total = text?.let { app.pocketta.core.ReceiptParser.total(it) }
            if (total != null) {
                input = numText(total)
                scanState = "Found " + Money.fmt(total) + ". Check it is right."
            } else {
                scanState = "Could not find an amount. Type it in."
            }
        }
    }
    val takePhoto = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.TakePicture()) { ok ->
        if (ok) readReceipt(receiptUri) else scanState = null
    }
    val pickPhoto = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { readReceipt(it) }
    fun startCamera() {
        val dir = java.io.File(context.cacheDir, "receipts").apply { mkdirs() }
        val f = java.io.File(dir, "r" + System.currentTimeMillis() + ".jpg")
        val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".files", f)
        receiptUri = uri
        takePhoto.launch(uri)
    }

    // Typing "koshary 60" in the note works too: the number becomes the amount when the keypad is empty.
    val quick = QuickText.parse(noteField)
    val typedAmount = parseNumber(input)?.takeIf { it > 0 }
    val amount = typedAmount ?: quick.amount?.takeIf { it > 0 }
    val note = if (typedAmount == null && quick.amount != null) quick.note else noteField.trim()

    // Guess the category from the words typed ("koshary" -> Food).
    LaunchedEffect(quick.note, type) {
        if (pickedByHand || quick.note.isBlank() || type == TxType.TRANSFER) return@LaunchedEffect
        delay(250)
        val guess = repo.guessCategory(quick.note, type)
        if (guess != null) {
            categoryId = guess
            parentId = categories.firstOrNull { it.id == guess }?.let { it.parentId ?: it.id }
        }
    }

    val kindCats = categories.filter { it.kind == (if (type == TxType.INCOME) TxType.INCOME else TxType.EXPENSE) && it.name != "Piggy bank" && it.name != "From piggy bank" }
    val parents = kindCats.filter { it.parentId == null }
    val subs = kindCats.filter { it.parentId != null && it.parentId == parentId }
    val catName = categories.firstOrNull { it.id == categoryId }?.name
    val isBorrowed = type == TxType.INCOME && catName.equals("Borrowed", true) && editing == null

    val canSave = amount != null && amount > 0 && walletId != 0L &&
        (type == TxType.TRANSFER || type == PIGGY || categoryId != null) &&
        (!isBorrowed || person.isNotBlank())

    fun save() {
        if (!canSave || amount == null) return
        scope.launch {
            if (type == PIGGY) {
                repo.savePiggy(amount * prefs.rateOf(currency), goalId = goal, epochDay = date.toEpochDay(), note = note)
                nav.back()
                return@launch
            }
            if (isBorrowed) {
                repo.addLoan(person.trim(), lent = false, amount = amount * prefs.rateOf(currency), walletId = walletId, day = date, due = dueDate, note = note)
            } else {
                val base = editing ?: Txn(type = type, amount = 0.0, walletId = walletId, epochDay = date.toEpochDay())
                repo.saveTxn(
                    base.copy(
                        type = type, amount = amount, currency = currency, rate = prefs.rateOf(currency),
                        walletId = walletId, toWalletId = if (type == TxType.TRANSFER) toWalletId else null,
                        categoryId = if (type == TxType.TRANSFER) null else categoryId,
                        note = note, epochDay = date.toEpochDay(), tags = Tags.join(Tags.parse(tagsText)),
                        need = if (prefs.addNeed && type == TxType.EXPENSE) need else base.need,
                        how = if (prefs.addHow) how else base.how,
                        place = if (prefs.addWhere) place.trim() else base.place,
                    )
                )
                if (type == TxType.EXPENSE && editing == null) checkOverBudget(context, repo, prefs)
            }
            nav.back()
        }
    }

    val title = when {
        editing != null -> "Edit"
        type == TxType.INCOME -> "Add income"
        type == TxType.TRANSFER -> "Move money"
        type == PIGGY -> "Put in piggy bank"
        else -> "Add expense"
    }
    fun press(k: String) {
        input = when (k) {
            "⌫" -> input.dropLast(1)
            "." -> if (input.contains('.')) input else if (input.isEmpty()) "0." else "$input."
            else -> if (input == "0") k else if (input.substringAfter('.', "").length >= 2 && input.contains('.')) input else (input + k).take(10)
        }
    }
    Scaffold(
        topBar = {
            BackBar(title, { nav.back() }) {
                editing?.let { t ->
                    IconButton(onClick = { scope.launch { repo.deleteTxn(t); nav.back() } }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                }
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).imePadding()) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (editing?.source.let { it != null && it != Source.NONE }) {
                    Text("This was recorded from a bill, installment, gam'eya or loan.", style = MaterialTheme.typography.bodySmall)
                }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val types = if (type == TxType.TRANSFER) listOf(TxType.TRANSFER to "Transfer") else if (editing != null) listOf(TxType.EXPENSE to "Expense", TxType.INCOME to "Income") else listOf(TxType.EXPENSE to "Expense", TxType.INCOME to "Income", PIGGY to "🐷 Piggy bank")
                    types.forEachIndexed { i, (t, label) ->
                        SegmentedButton(
                            selected = type == t,
                            onClick = { if (t != type) { type = t; categoryId = null; parentId = null; pickedByHand = false } },
                            shape = SegmentedButtonDefaults.itemShape(i, types.size),
                        ) { Text(label) }
                    }
                }
                // Amount, big and centered, with the currency next to it.
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                        .background(if (type == TxType.INCOME) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer)
                        .padding(vertical = 18.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            amount?.let { Money.fmt(it) } ?: "0",
                            fontSize = 46.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                            color = if (amount == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(" $currency", fontSize = 18.sp, modifier = Modifier.padding(bottom = 8.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    if (currency != prefs.baseCurrency && amount != null) {
                        Text("= ${Money.fmt(amount * prefs.rateOf(currency))} ${prefs.baseCurrency}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (prefs.currencies.size > 1) {
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (c in prefs.currencies) {
                                FilterChip(selected = currency == c, onClick = { currency = c }, label = { Text(c) })
                            }
                        }
                    }
                }
                if (type == TxType.EXPENSE && editing == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.OutlinedButton(onClick = { startCamera() }, modifier = Modifier.weight(1f)) { Text("Scan receipt") }
                        androidx.compose.material3.OutlinedButton(onClick = { pickPhoto.launch("image/*") }, modifier = Modifier.weight(1f)) { Text("From gallery") }
                    }
                    scanState?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                OutlinedTextField(
                    value = noteField,
                    onValueChange = {
                        noteField = it
                    },
                    placeholder = { Text(if (type == TxType.EXPENSE) "What did you buy? e.g. koshary" else "Note (optional)") },
                    singleLine = true,
                    shape = FieldShape,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, capitalization = KeyboardCapitalization.Sentences),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (type == TxType.EXPENSE && editing == null && recent.isNotEmpty()) {
                    Label("Bought before: tap one")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (r in recent) {
                            FilterChip(
                                selected = false,
                                onClick = {
                                    input = numText(r.amount); noteField = r.note
                                    val c = categories.firstOrNull { it.id == r.categoryId }
                                    if (c != null) { categoryId = c.id; parentId = c.parentId ?: c.id; pickedByHand = true }
                                },
                                label = { Text(r.note + " · " + Money.fmt(r.amount)) },
                            )
                        }
                    }
                }
                if (type == PIGGY) {
                    Label("Goes to")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = goal == -1L, onClick = { goal = -1L }, label = { Text("General piggy bank") })
                        if (prefs.goalAmount > 0) FilterChip(selected = goal == 0L, onClick = { goal = 0L }, label = { Text(prefs.goalName.ifBlank { "My goal" }) })
                        for (g in prefs.goals) FilterChip(selected = goal == g.id, onClick = { goal = g.id }, label = { Text(g.name) })
                    }
                }
                if (type != TxType.TRANSFER && type != PIGGY) {
                    Label("Category")
                    CategoryGrid(parents, parentId) { c -> parentId = c.id; categoryId = c.id; pickedByHand = true }
                    if (subs.isNotEmpty()) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (c in subs) {
                                FilterChip(
                                    selected = categoryId == c.id,
                                    onClick = { categoryId = if (categoryId == c.id) parentId else c.id; pickedByHand = true },
                                    label = { Text((Emoji.of(c.name)?.plus(" ") ?: "") + c.name) },
                                )
                            }
                        }
                    }
                }
                if (isBorrowed) {
                    FieldGroup("Who lent you the money") {
                        TextInput("Borrowed from", person, { person = it })
                        DateField("Pay back by (optional)", dueDate, { dueDate = it }, allowClear = true)
                        Text("This also goes to Owe → Loans as money you owe.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (type != PIGGY) {
                if (prefs.addNeed && type == TxType.EXPENSE) {
                    Label("Need or want?")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = need == "NEED", onClick = { need = if (need == "NEED") "" else "NEED" }, label = { Text("Need") })
                        FilterChip(selected = need == "WANT", onClick = { need = if (need == "WANT") "" else "WANT" }, label = { Text("Want") })
                    }
                }
                if (prefs.addHow) {
                    Label(if (type == TxType.INCOME) "Received how?" else "Paid how?")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((k, l) in listOf("CASH" to "Cash", "CARD" to "Card", "WALLET" to "Mobile wallet")) {
                            FilterChip(selected = how == k, onClick = { how = if (how == k) "" else k }, label = { Text(l) })
                        }
                    }
                }
                if (prefs.addWhere) {
                    TextInput(if (type == TxType.INCOME) "Came from" else "Where", place, { place = it })
                }
                Label("Tags (optional)")
                OutlinedTextField(
                    value = tagsText, onValueChange = { tagsText = it },
                    placeholder = { Text("wedding, trip") }, singleLine = true, shape = FieldShape, modifier = Modifier.fillMaxWidth(),
                )
                if (knownTags.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (g in knownTags) {
                            val on = g in Tags.parse(tagsText)
                            FilterChip(
                                selected = on,
                                onClick = { val l = Tags.parse(tagsText); tagsText = (if (on) l - g else l + g).joinToString(", ") },
                                label = { Text("#$g") },
                            )
                        }
                    }
                }
                val today = LocalDate.now()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = date == today, onClick = { date = today }, label = { Text("Today") })
                    FilterChip(selected = date == today.minusDays(1), onClick = { date = today.minusDays(1) }, label = { Text("Yesterday") })
                    var pick by remember { mutableStateOf(false) }
                    FilterChip(
                        selected = date != today && date != today.minusDays(1),
                        onClick = { pick = true },
                        label = { Text(if (date != today && date != today.minusDays(1)) date.format(dayFmt) else "Other day") },
                    )
                    if (pick) DayPicker(date, { date = it; pick = false }, { pick = false })
                }
                }
                Gap(4)
            }
            Keypad(::press)
            Button(
                onClick = { save() }, enabled = canSave,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(54.dp),
                shape = RoundedCornerShape(18.dp),
            ) { Text(if (editing != null) "Save changes" else if (type == PIGGY) "Save in piggy bank" else "Save", fontSize = 17.sp) }
        }
    }
}

@Composable
private fun CategoryGrid(items: List<Category>, selected: Long?, onPick: (Category) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val perRow = if (maxWidth > 500.dp) 6 else 4
        val gap = 8.dp
        val w = (maxWidth - gap * (perRow - 1)) / perRow
        FlowRow(horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(gap)) {
            for (c in items) {
                val on = c.id == selected
                Column(
                    Modifier.width(w).clip(RoundedCornerShape(16.dp))
                        .background(if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                        .clickable { onPick(c) }.padding(vertical = 10.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(Emoji.of(c.name) ?: c.name.take(1).uppercase(), fontSize = 24.sp)
                    Text(
                        c.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                        color = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun WalletRow(wallets: List<Pair<Long, String>>, selected: Long, onPick: (Long) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((id, name) in wallets) {
            FilterChip(
                selected = id == selected, onClick = { onPick(id) },
                label = { Text((Emoji.of(name)?.plus(" ") ?: "") + name) },
            )
        }
    }
}

@Composable
private fun Keypad(onKey: (String) -> Unit) {
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", ".", "0", "⌫")
    // The digits keep their usual places even when the app is in Arabic (right to left).
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (row in keys.chunked(3)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (k in row) {
                    Box(
                        Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest).clickable { onKey(k) },
                        contentAlignment = Alignment.Center,
                    ) { Text(k, fontSize = 22.sp, fontWeight = FontWeight.Medium) }
                }
            }
        }
    } }
}

@Composable
fun DayPicker(date: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = date.toEpochDay() * 86_400_000L)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onPick(LocalDate.ofEpochDay(Math.floorDiv(it, 86_400_000L))) } ?: onDismiss() }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(state = state) }
}

/** After an expense: say so once per cycle when a category has gone over its monthly limit. Under the limit, nothing. */
suspend fun checkCategoryLimits(context: Context, repo: Repo) {
    val p = repo.prefs()
    if (!p.limitAlert) return
    val over = repo.categoriesOverLimit()
    if (over.isEmpty()) return
    val day = CycleMath.cycleFor(LocalDate.now(), p.cycleStart).start.toEpochDay()
    val fresh = LimitAlerts.fresh(day, p.limitAlerted, over.map { it.first.id })
    if (fresh.isEmpty()) return
    val known = LimitAlerts.parse(p.limitAlerted).let { (d, ids) -> if (d == day) ids else emptySet() }
    repo.set("limitAlerted", LimitAlerts.format(day, known + fresh))
    for ((c, spent) in over.filter { it.first.id in fresh }) Notifier.categoryLimit(context, c.id, c.name, spent, c.monthlyLimit, p.baseCurrency, p.lang)
}

suspend fun checkOverBudget(context: Context, repo: Repo, prefs: Prefs) {
    runCatching { checkCategoryLimits(context, repo) }
    if (!prefs.budgetAlert) return
    val h = repo.home()
    val b = h.budget
    if (b.flexLeft >= 0 || b.budget <= 0) return
    val key = "overAlert"
    val today = h.today.toString()
    if (repo.dao.settings().firstOrNull { it.key == key }?.value == today) return
    repo.set(key, today)
    Notifier.overBudget(context, -b.flexLeft, prefs.baseCurrency, prefs.lang)
}

fun categoryLabel(categories: List<Category>, id: Long?): String {
    val c = categories.firstOrNull { it.id == id } ?: return "No category"
    val parent = c.parentId?.let { pid -> categories.firstOrNull { it.id == pid } }
    return if (parent != null) "${parent.name} · ${c.name}" else c.name
}
