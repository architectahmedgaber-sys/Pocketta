package app.pocketta.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
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
fun MoreScreen(repo: Repo, nav: Nav, prefs: Prefs, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionCard {
            Label("Look")
            Gap(6)
            Choices(listOf("AUTO", "LIGHT", "DARK"), prefs.theme, { when (it) { "LIGHT" -> "☀️ Light"; "DARK" -> "🌙 Dark"; else -> "Auto" } }, { v ->
                scope.launch { repo.set("theme", v) }
            })
        }
        MoreCard("📅", "Month plan", "Every month like your spreadsheet: income, installments, living, what is left") { nav.open(Route.MonthPlan) }
        MoreCard("🐷", "Piggy bank", "Everything you saved, month by month") { nav.open(Route.Piggy) }
        MoreCard("🎯", "Goals", "Umra, a car, a laptop, each with its own date") { nav.open(Route.Goals) }
        MoreCard("🏦", "Everything I own", "Your money and gold in one number, in dollars and gold, and how it changed") { nav.open(Route.Wealth) }
        MoreCard("🧮", "Plan ahead", "Can I afford this? What if I save? When money runs short") { nav.open(Route.Plan) }
        MoreCard("💰", "Income & salary", "Your monthly salary, other income, money set aside") { nav.open(Route.Income) }
        MoreCard("#️⃣", "Tags", "Weddings, trips, projects: see the total of each across categories") { nav.open(Route.Tags) }
        MoreCard("🏷️", "Categories", "Categories, sub-categories and monthly limits") { nav.open(Route.Categories) }
        MoreCard("💱", "Currencies & gold", "Convert money and gold, today's prices, your own rates") { nav.open(Route.Currencies) }
        MoreCard("🔔", "Notifications", "Switch each notification on or off, and send a test") { nav.open(Route.Notifications) }
        MoreCard("⚙️", "Settings", "Month start, reminders, fingerprint lock, backup") { nav.open(Route.Settings) }
    }
}

@Composable
private fun MoreCard(emoji: String, title: String, sub: String, onClick: () -> Unit) {
    SectionCard(onClick = onClick) {
        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                Modifier.size(60.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Text(emoji, fontSize = 28.sp) }
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", fontSize = 28.sp, color = MaterialTheme.colorScheme.outline)
        }
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
    var household by remember { mutableStateOf(numText(prefs.household)) }
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
                    NumberField("Set aside for savings each month (optional)", savings, { savings = it }, suffix = prefs.baseCurrency)
                    Gap(8)
                    NumberField("Household shopping each month (optional)", household, { household = it }, suffix = prefs.baseCurrency)
                    Text("Kept apart from your flexible money. Groceries you add use this amount first.", style = MaterialTheme.typography.bodySmall)
                    Gap(8)
                    Button(onClick = {
                        scope.launch {
                            repo.set("salary", parseNumber(salary) ?: 0.0)
                            repo.set("salaryDay", (day.toIntOrNull() ?: 1).coerceIn(1, 31))
                            repo.set("salaryWallet", wallet ?: wallets.firstOrNull()?.id ?: 0L)
                            repo.set("savingsPerMonth", parseNumber(savings) ?: 0.0)
                            repo.set("household", parseNumber(household) ?: 0.0)
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
    var pickCountry by remember { mutableStateOf(false) }
    val country0 = app.pocketta.core.Countries.find(prefs.country, prefs.baseCurrency)
    var refreshing by remember { mutableStateOf(false) }
    Scaffold(topBar = { BackBar("Currencies & gold", { nav.back() }) }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ConverterCard(prefs)
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
            GoldSection(repo, prefs)
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
                val country = app.pocketta.core.Countries.find(prefs.country, prefs.baseCurrency)
                val ar = LocalLang.current == app.pocketta.i18n.Tr.AR
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        (country?.let { it.flag + " " + (if (ar) it.ar else it.en) + " · " } ?: "") + prefs.baseCurrency,
                        Modifier.weight(1f), fontWeight = FontWeight.SemiBold,
                    )
                    TextButton(onClick = { pickCountry = true }) { Text("Change") }
                }
                Text("Numbers look like: ${Money.fmt(1234.5678)} ${prefs.baseCurrency}", style = MaterialTheme.typography.bodySmall)
                Text("Everything is totalled in this currency. Internet rates and gold are converted to it.", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextInput("Another currency (code)", base, { base = it.uppercase().take(4) }, Modifier.weight(1f))
                    TextButton(onClick = {
                        scope.launch {
                            if (base.isNotBlank()) { repo.setCurrency(base.trim(), ""); app.pocketta.data.Online.refreshRates(repo, true) }
                        }
                    }) { Text("Save") }
                }
            }
            if (pickCountry) {
                var sel by remember { mutableStateOf(country0?.code ?: "") }
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { pickCountry = false },
                    title = { Text("Pick your country") },
                    text = {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            CountryPicker(sel) { c -> sel = c.code }
                            Text("Changing the currency does not convert your old amounts, only the symbol and decimals.", style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val c = app.pocketta.core.Countries.choices.firstOrNull { it.code == sel }
                            pickCountry = false
                            if (c != null) scope.launch {
                                repo.setCurrency(c.currency, if (c.code.startsWith("CUR:")) "" else c.code)
                                app.pocketta.data.Online.refreshRates(repo, true)
                            }
                        }) { Text("Save") }
                    },
                    dismissButton = { TextButton(onClick = { pickCountry = false }) { Text("Cancel") } },
                )
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
    var picked by remember { mutableStateOf(app.pocketta.core.Countries.all.first()) }
    Scaffold { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Pocketta", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Choices(listOf("EN", "AR"), LocalLang.current, { if (it == "AR") "العربية" else "English" }, { v -> scope.launch { repo.set("lang", v) } })
            Text("Your money for the month: what you can spend today, your bills, installments, gam'eya and loans. Everything stays on your phone.")
            Text("Your country", fontWeight = FontWeight.SemiBold)
            Text("We set your currency from it. You can change it later in Settings.", style = MaterialTheme.typography.bodySmall)
            CountryPicker(picked.code) { picked = it }
            NumberField("Monthly salary (optional)", salary, { salary = it }, suffix = picked.currency)
            NumberField("Salary day of the month", salaryDay, { salaryDay = it }, decimal = false)
            Text("Your month starts on the salary day. You can change this later in Settings.", style = MaterialTheme.typography.bodySmall)
            Button(onClick = {
                scope.launch {
                    repo.setCurrency(picked.currency, if (picked.code.startsWith("CUR:")) "" else picked.code)
                    repo.ensureSeeded()
                    repo.set("cycleStart", (salaryDay.toIntOrNull() ?: 1).coerceIn(1, 31))
                    repo.set("salary", parseNumber(salary) ?: 0.0)
                    repo.set("salaryDay", (salaryDay.toIntOrNull() ?: 1).coerceIn(1, 31))
                    repo.set("savingsPerMonth", parseNumber(savings) ?: 0.0)
                    repo.set("lock", lock)
                    repo.set("onboarded", true)
                    repo.ensureSalary()
                    onDone()
                }
            }, modifier = Modifier.padding(top = 8.dp)) { Text("Start") }
        }
    }
}

/** Gold you bought: grams and the price you paid, with profit or loss against today's price. */
@Composable
private fun GoldSection(repo: Repo, prefs: Prefs) {
    val scope = rememberCoroutineScope()
    var karat by remember { mutableStateOf("21") }
    var grams by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    val buys = prefs.goldBuys
    val summary = app.pocketta.core.Gold.summary(buys, prefs.gold)
    fun save(list: List<app.pocketta.core.GoldBuy>) { scope.launch { repo.set("goldBuys", app.pocketta.core.Gold.format(list)) } }
    SectionCard {
        Label("My gold")
        Text("Write down gold you bought to see your profit or loss against today's price.", style = MaterialTheme.typography.bodySmall)
        if (summary != null) {
            Gap(4)
            val up = summary.profit >= 0
            val color = if (up) androidx.compose.ui.graphics.Color(0xFF1F8A4C) else androidx.compose.ui.graphics.Color(0xFFC62828)
            Text(
                (if (up) "Profit " else "Loss ") + Money.short(kotlin.math.abs(summary.profit)) + " " + prefs.baseCurrency,
                fontWeight = FontWeight.Bold, color = color, style = MaterialTheme.typography.titleMedium,
            )
            Text((if (up) "+" else "−") + Money.fmt(kotlin.math.abs(summary.percent)) + "%", color = color, style = MaterialTheme.typography.bodySmall)
            Text(
                Money.fmt(summary.grams) + " g · Paid " + Money.short(summary.cost) + " · Worth " + Money.short(summary.value) + " " + prefs.baseCurrency,
                style = MaterialTheme.typography.bodySmall,
            )
        } else if (buys.isNotEmpty()) {
            Text("Today's gold price isn't downloaded yet.", style = MaterialTheme.typography.bodySmall)
        }
        for ((i, b) in buys.withIndex()) {
            Divider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Karat ${b.karat} · ${Money.fmt(b.grams)} g", fontWeight = FontWeight.SemiBold)
                    Text("Bought at ${Money.fmt(b.pricePerGram)} per gram", style = MaterialTheme.typography.bodySmall)
                    app.pocketta.core.Gold.profit(b, prefs.gold)?.let { pr ->
                        Text(
                            (if (pr >= 0) "Profit " else "Loss ") + Money.short(kotlin.math.abs(pr)) + " " + prefs.baseCurrency,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (pr >= 0) androidx.compose.ui.graphics.Color(0xFF1F8A4C) else androidx.compose.ui.graphics.Color(0xFFC62828),
                        )
                    }
                }
                TextButton(onClick = { save(buys.filterIndexed { j, _ -> j != i }) }) { Text("Remove") }
            }
        }
        Gap(8)
        Choices(listOf("24", "21", "18"), karat, { "Karat $it" }, { karat = it })
        Gap(4)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Grams", grams, { grams = it }, Modifier.weight(1f))
            NumberField("Price per gram", price, { price = it }, Modifier.weight(1f), suffix = prefs.baseCurrency)
        }
        val g = parseNumber(grams)
        val pr = parseNumber(price)
        Button(
            enabled = g != null && g > 0 && pr != null && pr > 0,
            onClick = {
                save(buys + app.pocketta.core.GoldBuy(karat, g ?: 0.0, pr ?: 0.0, LocalDate.now().toEpochDay()))
                grams = ""; price = ""
            },
        ) { Text("Add gold") }
        prefs.gold[karat]?.let { Text("Today's price per gram: ${Money.short(it)} ${prefs.baseCurrency}", style = MaterialTheme.typography.bodySmall) }
    }
}
