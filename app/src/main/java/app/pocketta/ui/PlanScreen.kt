package app.pocketta.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pocketta.core.Money
import app.pocketta.core.Planning
import app.pocketta.data.CatStat
import app.pocketta.data.PlanData
import app.pocketta.data.Prefs
import app.pocketta.data.Repo
import java.time.LocalDate

/** Three tools for deciding ahead: can I afford this, what if I cut something, and what to do when money runs short. */
@Composable
fun PlanScreen(repo: Repo, nav: Nav, prefs: Prefs) {
    val version by repo.version.collectAsState()
    var data by remember { mutableStateOf<PlanData?>(null) }
    LaunchedEffect(version, prefs) { data = repo.planData() }
    Scaffold(topBar = { BackBar("Plan ahead", { nav.back() }) }) { pad ->
        val d = data
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (d == null) return@Column
            EmergencyCard(d, prefs.baseCurrency, always = true)
            AffordCard(d, prefs)
            SimulateCard(d, prefs)
            Gap(24)
        }
    }
}

@Composable
private fun PlanLine(label: String, value: String, color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified, bold: Boolean = false) {
    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, color = color, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium)
    }
}

private fun monthsText(m: Int): String = if (m <= 0) "now" else if (m == 1) "1 month" else "$m months"

/** Enter a price and see what it does to today's number, this month and the savings goal. */
@Composable
fun AffordCard(d: PlanData, p: Prefs) {
    var text by remember { mutableStateOf("") }
    val price = parseNumber(text) ?: 0.0
    val b = d.home.budget
    val cur = p.baseCurrency
    SectionCard {
        Label("Can I afford this?")
        Gap(8)
        NumberField("Price", text, { text = it }, suffix = cur)
        if (price > 0) {
            val r = Planning.canAfford(price, b.dailyAllowance, b.left, b.daysLeft, p.goalAmount, p.goalSaved, p.savingsPerMonth)
            val tone = when (r.verdict) {
                Planning.Verdict.YES -> Good
                Planning.Verdict.TIGHT -> Warn
                Planning.Verdict.NO -> MaterialTheme.colorScheme.error
            }
            Gap(10)
            Text(
                when (r.verdict) {
                    Planning.Verdict.YES -> "Yes, you can afford it"
                    Planning.Verdict.TIGHT -> "You can, but it will be tight"
                    Planning.Verdict.NO -> "Not this month"
                },
                color = tone, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium,
            )
            Gap(6)
            PlanLine("Daily number now", Money.short(r.dailyBefore) + " " + cur)
            PlanLine("Daily number after", Money.short(r.dailyAfter) + " " + cur, color = tone, bold = true)
            PlanLine("Left this month now", Money.short(r.leftBefore) + " " + cur)
            PlanLine("Left this month after", Money.short(r.leftAfter) + " " + cur, color = tone, bold = true)
            if (r.shortfall > 0) {
                Gap(4)
                Text("It would have to come out of your savings: " + Money.short(r.shortfall) + " " + cur, style = MaterialTheme.typography.bodySmall)
                val goalMonths = Planning.monthsToGoal(p.goalAmount, p.goalSaved, p.savingsPerMonth)
                val delay = r.goalDelayMonths
                if (delay != null && delay > 0 && goalMonths != null) {
                    val was = Planning.goalDate(d.home.today, goalMonths)
                    val now = Planning.goalDate(d.home.today, goalMonths + delay)
                    PlanLine("Goal date", was.format(monthYearFmt) + " → " + now.format(monthYearFmt), color = MaterialTheme.colorScheme.error, bold = true)
                    Text("Your goal moves back " + monthsText(delay), style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            Gap(4)
            Text("Type a price to see what it does to this month and your goal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Cut a category by 10/20/30% and see how much sooner the savings goal comes. */
@Composable
fun SimulateCard(d: PlanData, p: Prefs) {
    val cats = d.categories.filter { it.name !in Planning.fixedCategories && it.monthly(d.dayIndex, d.home.cycle.lengthDays) > 0 }.take(8)
    var picked by remember { mutableStateOf<String?>(null) }
    var percent by remember { mutableStateOf(20) }
    val cur = p.baseCurrency
    SectionCard {
        Label("What if I save?")
        Gap(6)
        if (cats.isEmpty()) {
            Text("Add some spending first, then try cutting a category", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@SectionCard
        }
        val cat: CatStat = cats.firstOrNull { it.name == picked } ?: cats.first()
        Choices(cats, cat, { it.name }, { picked = it.name })
        Gap(8)
        Choices(listOf(10, 20, 30, 50), percent, { "$it%" }, { percent = it })
        Gap(8)
        val monthly = cat.monthly(d.dayIndex, d.home.cycle.lengthDays)
        val sim = Planning.simulateCut(monthly, percent, p.goalAmount, p.goalSaved, p.savingsPerMonth)
        PlanLine("You spend per month", Money.short(monthly) + " " + cur)
        PlanLine("You would save per month", Money.short(sim.extraPerMonth) + " " + cur, color = Good, bold = true)
        PlanLine("You would save per year", Money.short(sim.extraPerMonth * 12) + " " + cur)
        if (p.goalAmount <= 0) {
            Gap(4)
            Text("Set a savings goal in Settings to see the date", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (p.goalSaved >= p.goalAmount) {
            Gap(4)
            Text("Goal reached!", color = Good, fontWeight = FontWeight.SemiBold)
        } else if (sim.monthsAfter != null) {
            val before = sim.monthsBefore
            val after = sim.monthsAfter!!
            Gap(4)
            if (before != null) PlanLine("Goal date now", Planning.goalDate(d.home.today, before).format(monthYearFmt))
            PlanLine("Goal date with this cut", Planning.goalDate(d.home.today, after).format(monthYearFmt), color = Good, bold = true)
            if (sim.monthsSooner > 0) Text("Sooner by " + monthsText(sim.monthsSooner), color = Good, fontWeight = FontWeight.SemiBold)
            else if (before == null) Text("Without a monthly saving the goal has no date. This cut gives it one.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Shown when the money will run out before the month ends: when, and what to postpone or cut. */
@Composable
fun EmergencyCard(d: PlanData, cur: String, always: Boolean = false, onClick: (() -> Unit)? = null) {
    val e = d.emergency
    if (e == null) {
        if (always) {
            SectionCard {
                Label("Emergency mode")
                Gap(4)
                Text("Your money should last until the end of the month", color = Good, fontWeight = FontWeight.SemiBold)
            }
        }
        return
    }
    SectionCard(container = MaterialTheme.colorScheme.errorContainer, onClick = onClick) {
        Label("Emergency mode")
        Gap(4)
        Text("Your money may run out", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Gap(4)
        PlanLine("Money runs out around", e.runOutDate.format(dayFmt), bold = true)
        PlanLine("Days before the month ends", e.daysShort.toString())
        Gap(6)
        PlanLine("Missing by month end", Money.short(e.shortfall) + " " + cur, color = MaterialTheme.colorScheme.error, bold = true)
        if (always) {
            if (e.postpone.isNotEmpty()) {
                Gap(8)
                Label("Postpone if you can")
                for (p in e.postpone) PlanLine(p.name + " · " + p.due.format(dayFmt), Money.short(p.amount) + " " + cur)
            }
            if (e.cuts.isNotEmpty()) {
                Gap(8)
                Label("Spend less on")
                for (c in e.cuts) PlanLine(c.name, "−" + Money.short(c.amount) + " " + cur)
            }
            if (e.uncovered > 0) {
                Gap(8)
                PlanLine("Still missing after this", Money.short(e.uncovered) + " " + cur, color = MaterialTheme.colorScheme.error, bold = true)
                Text("Think about extra income or moving money from savings", style = MaterialTheme.typography.bodySmall)
            } else {
                Gap(8)
                Text("Do both and you reach the end of the month.", color = Good, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Gap(4)
            Text("Tap to see what to postpone or cut", style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Home entry: the emergency warning when it applies, otherwise a quiet shortcut to the planning tools. */
@Composable
fun PlanEntryCard(repo: Repo, nav: Nav, prefs: Prefs) {
    val version by repo.version.collectAsState()
    var data by remember { mutableStateOf<PlanData?>(null) }
    LaunchedEffect(version, prefs) { data = repo.planData() }
    val d = data ?: return
    if (d.emergency != null) EmergencyCard(d, prefs.baseCurrency, onClick = { nav.open(Route.Plan) })
    RisesCard(d)
    SectionCard(onClick = { nav.open(Route.Plan) }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("🧮")
            Column(Modifier.weight(1f)) {
                Text("Plan ahead", fontWeight = FontWeight.Bold)
                Text("Can I afford this? What if I save?", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box { Text("›", color = MaterialTheme.colorScheme.outline) }
        }
    }
}
