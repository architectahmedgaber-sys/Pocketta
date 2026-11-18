package app.pocketta.ui

import android.content.Context
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
    val focus = remember { FocusRequester() }

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
    LaunchedEffect(loaded) { if (loaded && route.txnId == 0L) runCatching { focus.requestFocus() } }

    val quick = QuickText.parse(input)
    val amount = quick.amount
    val note = noteField.ifBlank { quick.note }

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
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (editing?.source.let { it != null && it != Source.NONE }) {
                Text("This was recorded from a bill, installment, circle or loan.", style = MaterialTheme.typography.bodySmall)
            }
            Choices(
                listOf(TxType.EXPENSE, TxType.INCOME, TxType.TRANSFER), type,
                { when (it) { TxType.EXPENSE -> "Expense"; TxType.INCOME -> "Income"; else -> "Transfer" } },
                { if (it != type) { type = it; categoryId = null; parentId = null; pickedByHand = false } },
            )
            Column(Modifier.fillMaxWidth()) {
                Label("Amount")
                Text(
                    (amount?.let { Money.fmt(it) } ?: "0") + " $currency",
                    fontSize = 40.sp, fontWeight = FontWeight.Bold,
                    color = if (amount == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                if (quick.note.isNotBlank() && noteField.isBlank()) Text("Note: ${quick.note}", style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text(if (type == TxType.EXPENSE) "Amount, or quick: koshary 60" else "Amount") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, capitalization = KeyboardCapitalization.None),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            if (type != TxType.TRANSFER) {
                Label("Category")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (c in parents) {
                        FilterChip(
                            selected = parentId == c.id,
                            onClick = { parentId = c.id; categoryId = c.id; pickedByHand = true },
                            label = { Text(c.name) },
                        )
                    }
                }
                if (subs.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (c in subs) {
                            FilterChip(
                                selected = categoryId == c.id,
                                onClick = { categoryId = if (categoryId == c.id) parentId else c.id; pickedByHand = true },
                                label = { Text(c.name) },
                            )
                        }
                    }
                }
            }
            if (isBorrowed) {
                TextInput("Borrowed from", person, { person = it })
                DateField("Pay back by (optional)", dueDate, { dueDate = it }, allowClear = true)
                Text("This also goes to Owe → Loans as money you owe.", style = MaterialTheme.typography.bodySmall)
            }
            Label(if (type == TxType.TRANSFER) "From" else "Wallet")
            Choices(wallets.map { it.id }, walletId, { id -> wallets.firstOrNull { it.id == id }?.name ?: "" }, { walletId = it })
            if (type == TxType.TRANSFER) {
                Label("To")
                Choices(wallets.map { it.id }, toWalletId, { id -> wallets.firstOrNull { it.id == id }?.name ?: "" }, { toWalletId = it })
            }
            if (prefs.currencies.size > 1) {
                Label("Currency")
                Choices(prefs.currencies, currency, { it }, { currency = it })
                if (currency != prefs.baseCurrency && amount != null) {
                    Text("= ${Money.fmt(amount * prefs.rateOf(currency))} ${prefs.baseCurrency}", style = MaterialTheme.typography.bodySmall)
                }
            }
            Label("Date")
            val today = LocalDate.now()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = date == today, onClick = { date = today }, label = { Text("Today") })
                FilterChip(selected = date == today.minusDays(1), onClick = { date = today.minusDays(1) }, label = { Text("Yesterday") })
            }
            DateField("Or pick a day", date, { if (it != null) date = it })
            TextInput("Note (optional)", noteField, { noteField = it })
            Button(onClick = { save() }, enabled = canSave, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text("Save")
            }
            Gap(24)
        }
    }
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
