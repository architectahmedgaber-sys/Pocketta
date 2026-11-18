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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import app.pocketta.core.Money
import app.pocketta.core.QuickText
import app.pocketta.data.Category
import app.pocketta.data.Prefs
import app.pocketta.data.Repo
import app.pocketta.data.Source
import app.pocketta.data.Txn
import app.pocketta.data.TxType
import app.pocketta.notify.Notifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun AddScreen(repo: Repo, nav: Nav, prefs: Prefs, route: Route.Add) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val categoriesFlow = remember { repo.dao.categoriesFlow() }
    val walletsFlow = remember { repo.dao.walletsFlow() }
    val categories by categoriesFlow.collectAsState(initial = emptyList())
    val wallets by walletsFlow.collectAsState(initial = emptyList())

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
    var date by remember { mutableStateOf(LocalDate.now()) }
    var person by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf<LocalDate?>(null) }
    var loaded by remember { mutableStateOf(route.txnId == 0L) }

    // Load the transaction being edited, or pick sensible defaults for a new one.
    LaunchedEffect(route.txnId, wallets.isNotEmpty(), categories.isNotEmpty()) {
        if (wallets.isEmpty() || categories.isEmpty()) return@LaunchedEffect
        if (route.txnId != 0L && editing == null) {
            val t = repo.dao.txn(route.txnId) ?: return@LaunchedEffect
            editing = t
            type = t.type
            input = numText(t.amount)
            noteField = t.note
            categoryId = t.categoryId
            parentId = categories.firstOrNull { it.id == t.categoryId }?.let { it.parentId ?: it.id }
            pickedByHand = true
            walletId = t.walletId
            toWalletId = t.toWalletId ?: 0L
            currency = t.currency
            date = LocalDate.ofEpochDay(t.epochDay)
            loaded = true
        } else if (walletId == 0L) {
            walletId = wallets.firstOrNull { it.id == prefs.lastWallet }?.id ?: wallets.first().id
            toWalletId = wallets.firstOrNull { it.id != walletId }?.id ?: walletId
            if (type == TxType.EXPENSE && prefs.lastCategory != 0L) {
                val c = categories.firstOrNull { it.id == prefs.lastCategory && !it.archived }
                if (c != null) { categoryId = c.id; parentId = c.parentId ?: c.id }
            }
        }
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

    val kindCats = categories.filter { it.kind == (if (type == TxType.INCOME) TxType.INCOME else TxType.EXPENSE) }
    val parents = kindCats.filter { it.parentId == null }
    val subs = kindCats.filter { it.parentId != null && it.parentId == parentId }
    val catName = categories.firstOrNull { it.id == categoryId }?.name
    val isBorrowed = type == TxType.INCOME && catName.equals("Borrowed", true) && editing == null

    val canSave = amount != null && amount > 0 && walletId != 0L &&
        (type == TxType.TRANSFER && toWalletId != walletId || type != TxType.TRANSFER && categoryId != null) &&
        (!isBorrowed || person.isNotBlank())

    fun save() {
        if (!canSave || amount == null) return
        scope.launch {
            if (isBorrowed) {
                repo.addLoan(person.trim(), lent = false, amount = amount * prefs.rateOf(currency), walletId = walletId, day = date, due = dueDate, note = note)
            } else {
                val base = editing ?: Txn(type = type, amount = 0.0, walletId = walletId, epochDay = date.toEpochDay())
                repo.saveTxn(
                    base.copy(
                        type = type, amount = amount, currency = currency, rate = prefs.rateOf(currency),
                        walletId = walletId, toWalletId = if (type == TxType.TRANSFER) toWalletId else null,
                        categoryId = if (type == TxType.TRANSFER) null else categoryId,
                        note = note, epochDay = date.toEpochDay(),
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
                    val types = listOf(TxType.EXPENSE to "Expense", TxType.INCOME to "Income", TxType.TRANSFER to "Transfer")
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
                            fontSize = 46.sp, fontWeight = FontWeight.Bold, maxLines = 1,
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
                OutlinedTextField(
                    value = noteField,
                    onValueChange = {
                        noteField = it
                    },
                    placeholder = { Text(if (type == TxType.EXPENSE) "What was it? e.g. koshary 60" else "Note (optional)") },
                    singleLine = true,
                    shape = FieldShape,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, capitalization = KeyboardCapitalization.Sentences),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (type != TxType.TRANSFER) {
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
                Label(if (type == TxType.TRANSFER) "From" else "Wallet")
                WalletRow(wallets.map { it.id to it.name }, walletId) { walletId = it }
                if (type == TxType.TRANSFER) {
                    Label("To")
                    WalletRow(wallets.map { it.id to it.name }, toWalletId) { toWalletId = it }
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
                Gap(4)
            }
            Keypad(::press)
            Button(
                onClick = { save() }, enabled = canSave,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(54.dp),
                shape = RoundedCornerShape(18.dp),
            ) { Text(if (editing != null) "Save changes" else "Save", fontSize = 17.sp) }
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
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
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
    Column(
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
    }
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

private suspend fun checkOverBudget(context: Context, repo: Repo, prefs: Prefs) {
    if (!prefs.budgetAlert) return
    val h = repo.home()
    val b = h.budget
    if (b.leftToday >= 0 || b.budget <= 0) return
    val key = "overAlert"
    val today = h.today.toString()
    if (repo.dao.settings().firstOrNull { it.key == key }?.value == today) return
    repo.set(key, today)
    val tomorrow = if (b.daysLeft > 1) b.left / (b.daysLeft - 1) else 0.0
    Notifier.overBudget(context, -b.leftToday, tomorrow, prefs.baseCurrency)
}

fun categoryLabel(categories: List<Category>, id: Long?): String {
    val c = categories.firstOrNull { it.id == id } ?: return "No category"
    val parent = c.parentId?.let { pid -> categories.firstOrNull { it.id == pid } }
    return if (parent != null) "${parent.name} · ${c.name}" else c.name
}
