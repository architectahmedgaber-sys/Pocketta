package app.pocketta.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import app.pocketta.core.Money
import app.pocketta.data.Category
import app.pocketta.data.Prefs
import app.pocketta.data.Repo
import app.pocketta.data.TxType
import app.pocketta.data.Wallet
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun MoreScreen(nav: Nav, modifier: Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("More", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp))
        ListRow("Income & salary", "Your monthly salary, other income, money set aside", icon = "💰", onClick = { nav.open(Route.Income) })
        ListRow("Wallets", "Cash, bank, Vodafone Cash, cards and balances", icon = "👛", onClick = { nav.open(Route.Wallets) })
        ListRow("Categories", "Categories, sub-categories and monthly limits", icon = "🏷️", onClick = { nav.open(Route.Categories) })
        ListRow("Currencies & gold", "Today's dollar, euro and gold prices, or your own rate", icon = "💱", onClick = { nav.open(Route.Currencies) })
        ListRow("Settings", "Month start, reminders, fingerprint lock, backup", icon = "⚙️", onClick = { nav.open(Route.Settings) })
    }
}

// ---------------- Income ----------------

@Composable
fun IncomeScreen(repo: Repo, nav: Nav, prefs: Prefs) {
    val scope = rememberCoroutineScope()
    val walletsFlow = remember { repo.dao.walletsFlow() }
    val wallets by walletsFlow.collectAsState(initial = emptyList())
    val catFlow = remember { repo.dao.categoriesFlow() }
    val categories by catFlow.collectAsState(initial = emptyList())
    val cycle = CycleMath.cycleFor(LocalDate.now(), prefs.cycleStart)
    val txFlow = remember(cycle.start) { repo.dao.txnsFlow(cycle.start.toEpochDay(), cycle.nextStart.toEpochDay()) }
    val txns by txFlow.collectAsState(initial = emptyList())
    val incomes = txns.filter { it.type == TxType.INCOME }
    var salary by remember { mutableStateOf(numText(prefs.salary)) }
    var day by remember { mutableStateOf(prefs.salaryDay.toString()) }
    var wallet by remember { mutableStateOf<Long?>(prefs.salaryWallet.takeIf { it != 0L }) }
    var savings by remember { mutableStateOf(numText(prefs.savingsPerMonth)) }
    Scaffold(topBar = { BackBar("Income & salary", { nav.back() }) }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad)) {
            item {
                SectionCard(Modifier.padding(16.dp)) {
                    Label("Monthly salary")
                    Text("Added by itself every month on its day. If one month is different, edit it in Spend.", style = MaterialTheme.typography.bodySmall)
                    Gap(8)
                    NumberField("Salary", salary, { salary = it }, suffix = prefs.baseCurrency)
                    Gap(8)
                    NumberField("Day of the month it arrives", day, { day = it }, decimal = false)
                    Gap(8)
                    Label("Goes into")
                    Choices(wallets.map { it.id }, wallet ?: wallets.firstOrNull()?.id, { id -> wallets.firstOrNull { it.id == id }?.name ?: "" }, { wallet = it })
                    Gap(8)
                    NumberField("Set aside for savings each month (optional)", savings, { savings = it }, suffix = prefs.baseCurrency)
                    Gap(8)
                    Button(onClick = {
                        scope.launch {
                            repo.set("salary", parseNumber(salary) ?: 0.0)
                            repo.set("salaryDay", (day.toIntOrNull() ?: 1).coerceIn(1, 31))
                            repo.set("salaryWallet", wallet ?: wallets.firstOrNull()?.id ?: 0L)
                            repo.set("savingsPerMonth", parseNumber(savings) ?: 0.0)
                            repo.ensureSalary()
                        }
                    }) { Text("Save") }
                }
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Label("Income this month · ${Money.short(incomes.sumOf { it.base })}", Modifier.weight(1f))
                    OutlinedButton(onClick = { nav.open(Route.Add(type = TxType.INCOME)) }) { Text("Add income") }
                }
            }
            if (incomes.isEmpty()) item { Text("Nothing yet this month.", Modifier.padding(16.dp)) }
            items(incomes, key = { it.id }) { t ->
                TxnRow(t, categories, wallets.associate { it.id to it.name }) { nav.open(Route.Add(txnId = t.id)) }
            }
            item {
                Text(
                    "Other income: gam'eya payout, borrowed money (also saved as a debt), side work, gifts. Pick the category when you add it.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

// ---------------- Wallets ----------------

private val walletTypes = listOf("CASH", "BANK", "MOBILE", "CARD", "OTHER")
private fun walletTypeName(t: String) = when (t) {
    "CASH" -> "Cash"; "BANK" -> "Bank"; "MOBILE" -> "Mobile wallet"; "CARD" -> "Credit card"; else -> "Other"
}

@Composable
fun WalletsScreen(repo: Repo, nav: Nav, prefs: Prefs) {
    val walletsFlow = remember { repo.dao.walletsFlow() }
    val wallets by walletsFlow.collectAsState(initial = emptyList())
    val balFlow = remember { repo.balancesFlow() }
    val balances by balFlow.collectAsState(initial = emptyMap())
    var editing by remember { mutableStateOf<Wallet?>(null) }
    Scaffold(topBar = {
        BackBar("Wallets", { nav.back() }) {
            IconButton(onClick = { editing = Wallet(name = "", sort = wallets.size) }) { Icon(Icons.Filled.Add, contentDescription = "Add wallet") }
        }
    }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad)) {
            item {
                SectionCard(Modifier.padding(16.dp), container = MaterialTheme.colorScheme.primaryContainer) {
                    Label("Total")
                    Big("${Money.short(wallets.sumOf { balances[it.id] ?: 0.0 })} ${prefs.baseCurrency}", size = 26)
                    TextButton(onClick = { nav.open(Route.Add(type = TxType.TRANSFER)) }) { Text("Move money between wallets") }
                }
            }
            items(wallets, key = { it.id }) { w ->
                val bal = balances[w.id] ?: 0.0
                val shown = if (w.currency != prefs.baseCurrency) bal / prefs.rateOf(w.currency) else bal
                ListRow(w.name, walletTypeName(w.type) + if (w.currency != prefs.baseCurrency) " · ${w.currency}" else "", icon = w.name, onClick = { editing = w }) {
                    Text(Money.fmt(shown), fontWeight = FontWeight.SemiBold, color = if (bal < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                }
            }
            item {
                Text(
                    "Balances start from the opening amount you set and follow everything you record.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
    editing?.let { WalletEditor(repo, prefs, it, balances[it.id]) { editing = null } }
}

@Composable
private fun WalletEditor(repo: Repo, prefs: Prefs, w: Wallet, balance: Double?, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(w.name) }
    var type by remember { mutableStateOf(w.type) }
    var currency by remember { mutableStateOf(w.currency) }
    var opening by remember { mutableStateOf(numText(w.opening)) }
    var actual by remember { mutableStateOf("") }
    FormDialog(
        if (w.id == 0L) "New wallet" else w.name,
        onDismiss = onClose,
        saveEnabled = name.isNotBlank(),
        onDelete = if (w.id != 0L) ({ scope.launch { repo.dao.updateWallet(w.copy(archived = true)); repo.changed() }; onClose() }) else null,
        onSave = {
            scope.launch {
                var o = parseNumber(opening) ?: 0.0
                // "I actually have X": move the opening balance so the balance matches.
                val real = parseNumber(actual)
                if (real != null && balance != null) o += (real * prefs.rateOf(currency) - balance) / prefs.rateOf(currency)
                val nw = w.copy(name = name.trim(), type = type, currency = currency, opening = o)
                if (w.id == 0L) repo.dao.insertWallet(nw) else repo.dao.updateWallet(nw)
                repo.changed()
            }
            onClose()
        },
    ) {
        TextInput("Name", name, { name = it })
        Choices(walletTypes, type, { walletTypeName(it) }, { type = it })
        if (prefs.currencies.size > 1) Choices(prefs.currencies, currency, { it }, { currency = it })
        NumberField("Opening balance", opening, { opening = it }, suffix = currency)
        if (w.id != 0L) {
            NumberField("Or: how much is in it right now?", actual, { actual = it }, suffix = currency)
            Text("We'll fix the balance to match.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ---------------- Categories ----------------

@Composable
fun CategoriesScreen(repo: Repo, nav: Nav) {
    val flow = remember { repo.dao.categoriesFlow() }
    val categories by flow.collectAsState(initial = emptyList())
    var kind by remember { mutableStateOf(TxType.EXPENSE) }
    var editing by remember { mutableStateOf<Category?>(null) }
    val list = categories.filter { it.kind == kind }
    val parents = list.filter { it.parentId == null }
    Scaffold(topBar = {
        BackBar("Categories", { nav.back() }) {
            IconButton(onClick = { editing = Category(name = "", kind = kind, sort = parents.size) }) { Icon(Icons.Filled.Add, contentDescription = "Add category") }
        }
    }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad)) {
            item {
                Choices(listOf(TxType.EXPENSE, TxType.INCOME), kind, { if (it == TxType.EXPENSE) "Spending" else "Income" }, { kind = it }, Modifier.padding(horizontal = 16.dp))
            }
            for (p in parents) {
                item(key = p.id) {
                    ListRow(p.name, if (p.monthlyLimit > 0) "Limit ${Money.fmt(p.monthlyLimit)} a month" else null, icon = p.name, onClick = { editing = p }) {
                        if (kind == TxType.EXPENSE) TextButton(onClick = { editing = Category(name = "", parentId = p.id, kind = kind) }) { Text("+ Sub") }
                    }
                }
                items(list.filter { it.parentId == p.id }, key = { it.id }) { c ->
                    Row(Modifier.padding(start = 40.dp)) {
                        ListRow(c.name, null, onClick = { editing = c })
                    }
                }
            }
        }
    }
    editing?.let { c ->
        val scope = rememberCoroutineScope()
        var name by remember(c) { mutableStateOf(c.name) }
        var limit by remember(c) { mutableStateOf(numText(c.monthlyLimit)) }
        FormDialog(
            if (c.id == 0L) (if (c.parentId != null) "New sub-category" else "New category") else c.name,
            onDismiss = { editing = null },
            saveEnabled = name.isNotBlank(),
            onDelete = if (c.id != 0L) ({ scope.launch { repo.dao.updateCategory(c.copy(archived = true)); repo.changed() }; editing = null }) else null,
            onSave = {
                scope.launch {
                    val nc = c.copy(name = name.trim(), monthlyLimit = parseNumber(limit) ?: 0.0)
                    if (c.id == 0L) repo.dao.insertCategory(nc) else repo.dao.updateCategory(nc)
                    repo.changed()
                }
                editing = null
            },
        ) {
            TextInput("Name", name, { name = it })
            if (c.parentId == null && c.kind == TxType.EXPENSE) {
                NumberField("Monthly limit (optional)", limit, { limit = it })
                Text("The bar turns amber near the limit and red when you go over.", style = MaterialTheme.typography.bodySmall)
            }
            if (c.id != 0L) Text("Deleting hides it. Past records keep it.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ---------------- Currencies ----------------

private val knownCurrencies = listOf("USD", "EUR", "SAR", "AED", "KWD", "GBP", "QAR", "TRY", "JOD", "OMR", "BHD", "CHF", "CAD", "AUD", "CNY", "JPY")

@Composable
fun CurrenciesScreen(repo: Repo, nav: Nav, prefs: Prefs) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("") }
    var base by remember { mutableStateOf(prefs.baseCurrency) }
    var refreshing by remember { mutableStateOf(false) }
    Scaffold(topBar = { BackBar("Currencies & gold", { nav.back() }) }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Today's rates from the internet", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (prefs.ratesUpdated.isNotEmpty()) "Updated ${prefs.ratesUpdated.take(10)}" else "Not downloaded yet",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    androidx.compose.material3.Switch(checked = prefs.autoRates, onCheckedChange = { v -> scope.launch { repo.set("autoRates", v) } })
                }
                if (prefs.autoRates) {
                    Gap(8)
                    Text("Show these in the app", style = MaterialTheme.typography.labelLarge)
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (c in knownCurrencies) {
                            val on = c in prefs.autoCurrencies
                            androidx.compose.material3.FilterChip(
                                selected = on,
                                onClick = {
                                    val next = if (on) prefs.autoCurrencies - c else prefs.autoCurrencies + c
                                    scope.launch { repo.set("autoCurrencies", next.joinToString(",")) }
                                },
                                label = { Text(c) },
                            )
                        }
                    }
                    Gap(8)
                    for (c in prefs.autoCurrencies) {
                        prefs.onlineRates[c]?.let { r ->
                            Row {
                                Text("1 $c", Modifier.weight(1f))
                                Text(
                                    "${Money.fmt(r)} ${prefs.baseCurrency}" + if (prefs.isManual(c)) " (you set ${Money.fmt(prefs.rates[c] ?: 0.0)})" else "",
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                    if (prefs.gold.isNotEmpty()) {
                        Gap(8)
                        Text("Gold, per gram", style = MaterialTheme.typography.labelLarge)
                        for (k in listOf("24", "21", "18")) {
                            prefs.gold[k]?.let { g ->
                                Row {
                                    Text("Karat $k", Modifier.weight(1f))
                                    Text("${Money.short(g)} ${prefs.baseCurrency}", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        Text("From the world gold price and the dollar rate. Shops may differ a little.", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(enabled = !refreshing, onClick = {
                        refreshing = true
                        scope.launch { app.pocketta.data.Online.refreshRates(repo, force = true); refreshing = false }
                    }) { Text(if (refreshing) "Updating..." else "Update now") }
                }
            }
            SectionCard {
                Label("Your own rates")
                Text("A rate you type here always wins over the internet one.", style = MaterialTheme.typography.bodySmall)
                for ((c, r) in prefs.rates.toSortedMap()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("1 $c = ${Money.fmt(r)} ${prefs.baseCurrency}", Modifier.weight(1f))
                        TextButton(onClick = { code = c; rate = numText(r) }) { Text("Edit") }
                        TextButton(onClick = { scope.launch { repo.set("rates", Prefs.formatRates(prefs.rates - c)) } }) { Text("Remove") }
                    }
                }
                Gap(4)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextInput("Code (USD)", code, { code = it.uppercase().take(4) }, Modifier.weight(1f))
                    NumberField("Rate", rate, { rate = it }, Modifier.weight(1f))
                }
                val r = parseNumber(rate)
                Button(
                    enabled = code.isNotBlank() && r != null && r > 0 && code != prefs.baseCurrency,
                    onClick = {
                        scope.launch { repo.set("rates", Prefs.formatRates(prefs.rates + (code.trim() to (r ?: 1.0)))) }
                        code = ""; rate = ""
                    },
                ) { Text("Save rate") }
            }
            SectionCard {
                Label("Main currency")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextInput("Code", base, { base = it.uppercase().take(4) }, Modifier.weight(1f))
                    TextButton(onClick = { scope.launch { if (base.isNotBlank()) repo.set("baseCurrency", base.trim()) } }) { Text("Save") }
                }
                Text("Everything is totalled in this currency. Internet rates are in EGP.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun OnboardingScreen(repo: Repo, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var start by remember { mutableStateOf("1") }
    var salary by remember { mutableStateOf("") }
    var salaryDay by remember { mutableStateOf("1") }
    var savings by remember { mutableStateOf("") }
    var lock by remember { mutableStateOf(false) }
    Scaffold { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Pocketta", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("Your money for the month: what you can spend today, your bills, installments, gam'eya and loans. Everything stays on your phone.")
            NumberField("Monthly salary (optional)", salary, { salary = it }, suffix = "EGP")
            NumberField("Salary day of the month", salaryDay, { salaryDay = it }, decimal = false)
            NumberField("My month starts on day", start, { start = it }, decimal = false)
            Text("Most people pick the day the salary arrives.", style = MaterialTheme.typography.bodySmall)
            NumberField("Save every month (optional)", savings, { savings = it }, suffix = "EGP")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = lock, onCheckedChange = { lock = it })
                Text("Lock the app with fingerprint")
            }
            Button(onClick = {
                scope.launch {
                    repo.ensureSeeded()
                    repo.set("cycleStart", (start.toIntOrNull() ?: 1).coerceIn(1, 31))
                    repo.set("salary", parseNumber(salary) ?: 0.0)
                    repo.set("salaryDay", (salaryDay.toIntOrNull() ?: 1).coerceIn(1, 31))
                    repo.set("savingsPerMonth", parseNumber(savings) ?: 0.0)
                    repo.set("lock", lock)
                    repo.set("onboarded", true)
                    repo.ensureSalary()
                    onDone()
                }
            }, modifier = Modifier.padding(top = 8.dp)) { Text("Start") }
            Text("You can change all of this later in More.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
