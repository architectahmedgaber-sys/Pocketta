package app.pocketta.ui

import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.IconButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pocketta.core.Budget
import app.pocketta.core.BudgetResult
import app.pocketta.core.Forecast
import app.pocketta.core.Money
import app.pocketta.BuildConfig
import app.pocketta.data.HomeState
import app.pocketta.data.Online
import app.pocketta.data.Prefs
import app.pocketta.data.PayUndo
import app.pocketta.data.Repo
import app.pocketta.data.Upcoming
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(repo: Repo, nav: Nav, prefs: Prefs, modifier: Modifier) {
    val version by repo.version.collectAsState()
    var state by remember { mutableStateOf<HomeState?>(null) }
    var details by remember { mutableStateOf(false) }
    var more by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(version, prefs) { state = repo.home() }
    val s = state ?: return
    val cur = prefs.baseCurrency
    val b = s.budget
    val ctx = androidx.compose.ui.platform.LocalContext.current
    // The count-up is skipped when the phone has animations turned off.
    val animationsOn = remember {
        runCatching { android.provider.Settings.Global.getFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }.getOrDefault(true)
    }
    val spentToday = s.days.lastOrNull { it.first == s.today }?.second ?: 0.0
    val over = b.leftToday < 0
    val target = kotlin.math.abs(b.leftToday).toFloat()
    val shownAmount = remember { Animatable(if (animationsOn) 0f else target) }
    LaunchedEffect(target) { if (animationsOn) shownAmount.animateTo(target, tween(600)) else shownAmount.snapTo(target) }
    val usedToday = if (b.dailyAllowance > 0) (spentToday / b.dailyAllowance).toFloat().coerceIn(0f, 1f) else 0f

    val cycleFlow = remember(s.cycle.start) { repo.dao.txnsFlow(s.cycle.start.toEpochDay(), s.cycle.nextStart.toEpochDay()) }
    val cycleTxns by cycleFlow.collectAsState(initial = emptyList())
    val categories by remember { repo.dao.categoriesFlow() }.collectAsState(initial = emptyList())
    val recent = cycleTxns.sortedWith(compareByDescending<app.pocketta.data.Txn> { it.epochDay }.thenByDescending { it.id }).take(5)

    Box(modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.cycle.end.format(monthFmt), style = MaterialTheme.typography.headlineMedium)
                    Text("${b.daysLeft} days left in this month", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { nav.open(Route.Settings) }) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
            }
            if (prefs.updateVersion.isNotEmpty() && prefs.updateUrl.isNotEmpty() && Online.isNewer(prefs.updateVersion, BuildConfig.VERSION_NAME)) {
                SectionCard(container = MaterialTheme.colorScheme.tertiaryContainer, onClick = { nav.open(Route.Settings) }) {
                    Text("Pocketta ${prefs.updateVersion} is ready", fontWeight = FontWeight.Medium)
                    Text("Tap to update. Your data stays as it is.", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (b.budget <= 0 && s.salaryExpected <= 0) {
                SectionCard(onClick = { nav.open(Route.Income) }) {
                    Text("Add your salary to see how much you can spend", fontWeight = FontWeight.Medium)
                    Text("Tap here to set it", style = MaterialTheme.typography.bodySmall)
                }
            }

            // The one big thing: how much can I spend today?
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(HeroGradient))
                    .padding(20.dp)
            ) {
                Text(if (over) "Over today's amount" else "Spend today", color = HeroText.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        Money.short(shownAmount.value.toDouble()), fontSize = 54.sp, fontWeight = FontWeight.Light,
                        color = if (over) Color(0xFFF2B8A8) else GoldLight, maxLines = 1,
                        style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
                    )
                    Text(" $cur", fontSize = 16.sp, fontWeight = FontWeight.Normal, color = HeroText, modifier = Modifier.padding(bottom = 10.dp))
                }
                Gap(10)
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(HeroText.copy(alpha = 0.2f))) {
                    Box(Modifier.fillMaxWidth(usedToday).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Brush.horizontalGradient(listOf(GoldAccent, GoldLight))))
                }
                Gap(8)
                Text("${b.daysLeft} days left · commitments already deducted", color = HeroText.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
            }

            if (b.budget > 0) ForecastCard(b, cur)

            // Three small numbers.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniNumber("Income", Money.short(s.income + s.salaryExpected), Modifier.weight(1f))
                MiniNumber("Commitments", Money.short(b.committed), Modifier.weight(1f))
                MiniNumber("Remaining", Money.short(b.left), Modifier.weight(1f), if (b.left < 0) MaterialTheme.colorScheme.error else Color.Unspecified)
            }

            SyncChip()

            StartGuide(s, nav)

            if (s.upcoming.isNotEmpty()) UpcomingCard(repo, s.upcoming.take(3), s, cur)

            ReadyToAddCard(repo, version, cur)
            if (!prefs.smsSuggest && !prefs.smsOffered && s.days.any { it.second > 0 }) SmsOfferCard(repo)

            if (recent.isNotEmpty()) {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Label("Last transactions", Modifier.weight(1f))
                        TextButton(onClick = { nav.tab(Route.Month) }) { Text("All ›") }
                    }
                    for (t in recent) TxnRow(t, categories) { nav.open(Route.Add(txnId = t.id)) }
                }
            }

            // Everything else lives below the fold.
            TextButton(onClick = { more = !more }) { Text(if (more) "Hide extras" else "More: insights, converter, goals, details") }
            if (more) {
                StreakCard(s)
                QuickAddCard(repo, prefs)
                ConverterCard(prefs)
                PlanEntryCard(repo, nav, prefs)
                WealthCard(repo, nav, prefs)
                GameyaCard(repo, prefs)
                WeekCard(repo, s)
                PiggyAutoCard(repo, prefs)
                GoalCard(repo, prefs)

                TextButton(onClick = { details = !details }) { Text(if (details) "Hide details" else "Show details (forecast, days, how it is calculated)") }
                if (details) {
                    SectionCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Label("Month-end forecast")
                                Text("${Money.short(b.forecastSpend)} $cur", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.titleMedium)
                            }
                            if (b.forecastDiff >= 0) Pill("${Money.short(b.forecastDiff)} under", Good)
                            else Pill("${Money.short(-b.forecastDiff)} over", MaterialTheme.colorScheme.error)
                        }
                    }
                    if (s.days.isNotEmpty()) {
                        SectionCard {
                            Label("This month, day by day")
                            Gap(8)
                            DayBars(s)
                        }
                    }
                    SectionCard {
                        Label("How the budget is made")
                        Gap(6)
                        Line("Income received", Money.short(s.income))
                        if (s.salaryExpected > 0) Line("Salary still coming", Money.short(s.salaryExpected))
                        if (s.setAside > 0) Line("Set aside (yearly bills, savings)", "−" + Money.short(s.setAside))
                        Line("Spent so far", "−" + Money.short(b.spent))
                        Line("Still to pay this month", "−" + Money.short(b.committed))
                        Line("Left", Money.short(b.left), bold = true)
                    }
                }
            }
            Gap(24)
        }
        // Miqrish, the assistant: a small button instead of a tab.
        SmallFloatingActionButton(
            onClick = { nav.open(Route.Assistant) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.primary,
        ) { androidx.compose.material3.Text("🪙", fontSize = 20.sp) }
    }
}

@Composable
private fun MiniNumber(label: String, value: String, modifier: Modifier, color: Color = Color.Unspecified) {
    SectionCard(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = color, maxLines = 1, style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"))
    }
}

@Composable
private fun Line(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = if (bold) FontWeight.Medium else FontWeight.Normal)
    }
}

@Composable
private fun DayBars(s: HomeState) {
    val days = s.days.takeLast(31)
    val max = (days.maxOfOrNull { it.second } ?: 0.0).coerceAtLeast(s.dayReference).coerceAtLeast(1.0)
    val ok = MaterialTheme.colorScheme.primary
    val near = Warn
    val over = MaterialTheme.colorScheme.error
    val empty = MaterialTheme.colorScheme.surfaceVariant
    Canvas(Modifier.fillMaxWidth().height(64.dp)) {
        val n = days.size
        val gap = 3.dp.toPx()
        val w = ((size.width - gap * (n - 1)) / n).coerceAtLeast(1f)
        days.forEachIndexed { i, (_, v) ->
            val h = if (v <= 0) 3.dp.toPx() else (v / max * size.height).toFloat().coerceAtLeast(3.dp.toPx())
            val c = if (v <= 0) empty else when (Budget.level(v, s.dayReference)) {
                Budget.Level.OVER -> over
                Budget.Level.NEAR -> near
                else -> ok
            }
            drawRoundRect(c, Offset(i * (w + gap), size.height - h), Size(w, h), CornerRadius(4.dp.toPx()))
        }
    }
}

@Composable
private fun UpcomingCard(repo: Repo, items: List<Upcoming>, s: HomeState, cur: String) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    SectionCard {
        Label("Coming soon")
        for (u in items) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(u.title, maxLines = 1)
                    val late = u.due.isBefore(s.today)
                    Text(
                        if (late) "Late · ${u.due.format(dayFmt)}" else u.due.format(dayFmt),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (late) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text((if (u.incoming) "+" else "") + Money.short(u.amount), fontWeight = FontWeight.Medium, color = if (u.incoming) Good else MaterialTheme.colorScheme.onSurface)
                if (!u.due.isAfter(s.today.plusDays(3))) {
                    TextButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); scope.launch { payUpcoming(repo, u) } }) { Text(if (u.incoming) "Got it" else "Paid") }
                }
            }
        }
        Text("Amounts in $cur", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Runs a payment and offers Undo for it (the money record and what it changed are reversed together). */
suspend fun withPayUndo(repo: Repo, text: String, billDue: List<Pair<Long, Long>> = emptyList(), block: suspend () -> Unit) {
    val before = repo.dao.allTxns().map { x -> x.id }.toSet()
    block()
    val made = repo.dao.allTxns().filter { x -> x.id !in before }.map { x -> x.id }
    if (made.isNotEmpty() || billDue.isNotEmpty()) UndoBus.show(text) { repo.undoPayGroup(PayUndo(made, billDue)) }
}

suspend fun payUpcoming(repo: Repo, u: Upcoming) {
    val dao = repo.dao
    val before = dao.allTxns().map { it.id }.toSet()
    val billDue = ArrayList<Pair<Long, Long>>()
    when (u.kind) {
        "BILL" -> dao.bill(u.id)?.let { billDue += it.id to it.nextDue; repo.payBill(it) }
        "INSTALLMENT" -> dao.installment(u.id)?.let { repo.payInstallment(it) }
        "CIRCLE" -> dao.circle(u.id)?.let { repo.payCircle(it) }
        "LOAN" -> dao.loan(u.id)?.let { repo.payNextInstalment(it) }
    }
    val made = dao.allTxns().filter { it.id !in before }.map { it.id }
    // Undo reverses the money record and what it changed (bill date, instalment, loan balance).
    if (made.isNotEmpty() || billDue.isNotEmpty()) UndoBus.show(if (u.incoming) "Marked received" else "Marked paid") { repo.undoPayGroup(PayUndo(made, billDue)) }
}

/** A quiet line under the numbers: when the cloud copy was last updated (only for people who signed in). */
@Composable
private fun SyncChip() {
    val sync by app.pocketta.data.CloudSync.ui.collectAsState()
    if (!sync.signedIn || sync.lastSyncAt <= 0L) return
    val whenText = java.time.Instant.ofEpochMilli(sync.lastSyncAt).atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("MMM d, HH:mm", java.util.Locale.ENGLISH))
    val text = if (sync.status == "error" || sync.status == "paused") "Not synced · last time $whenText" else "Synced · $whenText"
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Offered once, in context (after the first expenses), instead of in the first-run questions. */
@Composable
private fun SmsOfferCard(repo: Repo) {
    val scope = rememberCoroutineScope()
    val ask = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        scope.launch { repo.set("smsSuggest", granted); repo.set("smsOffered", true) }
    }
    SectionCard {
        Text("Add expenses from your bank messages?", fontWeight = FontWeight.Medium)
        Text("Messages that look like spending show up here as cards. They are read on this phone only and never sent anywhere. Nothing is saved until you tap Add.", style = MaterialTheme.typography.bodySmall)
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.Button(onClick = { ask.launch(android.Manifest.permission.RECEIVE_SMS) }) { Text("Turn on") }
            TextButton(onClick = { scope.launch { repo.set("smsOffered", true) } }) { Text("Not now") }
        }
    }
}

/** Bank messages that look like spending, waiting for one tap. Nothing is saved until the person taps Add. */
@Composable
private fun ReadyToAddCard(repo: Repo, version: Int, cur: String) {
    var cards by remember { mutableStateOf(emptyList<app.pocketta.core.SmsCard>()) }
    LaunchedEffect(version) { cards = repo.smsCards() }
    if (cards.isEmpty()) return
    val scope = rememberCoroutineScope()
    SectionCard {
        Label("Ready to add")
        Text("From your bank messages. Nothing is saved until you tap Add.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        for (c in cards) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(c.merchant.ifBlank { "Bank message" }, fontWeight = FontWeight.Medium, maxLines = 1)
                    Text(Money.fmt(c.amount) + " " + c.currency.ifBlank { cur }, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { scope.launch { repo.dismissSmsCard(c.id) } }) { Text("Dismiss") }
                androidx.compose.material3.Button(onClick = {
                    scope.launch {
                        val t = repo.acceptSmsCard(c)
                        UndoBus.show("Saved") { repo.dao.txn(t.id)?.let { repo.deleteTxn(it) }; repo.addSmsCard(c) }
                    }
                }) { Text("Add") }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** First-run guide: three steps that tell a new user what to do and where their numbers go. It hides itself when done. */
@Composable
private fun StartGuide(s: HomeState, nav: Nav) {
    val hasIncome = s.budget.budget > 0 || s.salaryExpected > 0
    val hasExpense = s.days.any { it.second > 0 }
    val hasCommit = s.upcoming.isNotEmpty()
    var hidden by rememberSaveable { mutableStateOf(false) }
    if (hidden || (hasIncome && hasExpense && hasCommit)) return
    SectionCard(container = MaterialTheme.colorScheme.primaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Start here", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { hidden = true }) { Text("Hide") }
        }
        Text("Three quick steps and Pocketta works for you.", style = MaterialTheme.typography.bodySmall)
        Gap(8)
        GuideStep(1, "Put in your salary", "So it can tell you what you can spend each day", hasIncome) { nav.open(Route.Income) }
        GuideStep(2, "Add what you spent today", "Tap Add expense, type the amount, pick a category", hasExpense) { nav.open(Route.Add()) }
        GuideStep(3, "Add a bill, installment or gam'eya", "Pocketta reminds you and counts it in your budget", hasCommit) { nav.tab(Route.Commit) }
    }
}

@Composable
private fun GuideStep(n: Int, title: String, hint: String, done: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (done) "✓" else n.toString(), fontWeight = FontWeight.Medium, color = if (done) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(hint, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Month-end forecast, visible on Today: three states (over, tight, ok), and when the money runs out at today's pace. */
@Composable
private fun ForecastCard(b: BudgetResult, cur: String) {
    val status = Forecast.status(b.forecastDiff, b.budget)
    val warn = status != Forecast.Status.OK
    val runOut = if (warn) Forecast.runOutIn(b.left, b.avgPerDay, b.daysLeft) else null
    val headline = when (status) {
        Forecast.Status.OVER -> "You will go over the budget by ${Money.short(-b.forecastDiff)} $cur"
        Forecast.Status.TIGHT -> "Small margin left: ${Money.short(b.forecastDiff)} $cur"
        Forecast.Status.OK -> "You will finish ${Money.short(b.forecastDiff)} $cur under budget"
    }
    SectionCard(container = if (status == Forecast.Status.OVER) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainer) {
        Label("Month-end forecast")
        Gap(4)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(headline, Modifier.weight(1f), fontWeight = FontWeight.Medium, style = MaterialTheme.typography.titleMedium)
            when (status) {
                Forecast.Status.OVER -> Pill("Over budget", MaterialTheme.colorScheme.error)
                Forecast.Status.TIGHT -> Pill("Tight", Warn)
                Forecast.Status.OK -> Pill("On track", Good)
            }
        }
        if (runOut != null) {
            Gap(6)
            Text(
                if (runOut == 0) "At today's pace your money has run out" else "At today's pace your money runs out in $runOut days",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (runOut > 0) {
                Gap(2)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Money runs out on", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(java.time.LocalDate.now().plusDays(runOut.toLong()).format(dayFmt), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                }
            }
        }
        if (warn && b.dailyAllowance >= 1) {
            Gap(6)
            Text("Spend up to ${Money.short(Math.floor(b.dailyAllowance))} $cur a day to stay within budget", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
