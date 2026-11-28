package app.pocketta.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pocketta.core.GoalList
import app.pocketta.core.GoalNeed
import app.pocketta.core.Money
import app.pocketta.core.Piggy
import app.pocketta.core.Planning
import app.pocketta.core.SavingsGoal
import app.pocketta.data.Prefs
import app.pocketta.data.Repo
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Id used for the main goal (the one in Settings and on Today). The extra goals have ids from 1. */
private const val MAIN = 0L

/** All the savings goals: the main one plus any extras, each with its own ring, monthly amount and finish date. */
@Composable
fun GoalsScreen(repo: Repo, nav: Nav, prefs: Prefs) {
    val scope = rememberCoroutineScope()
    val cur = prefs.baseCurrency
    val goals = buildList {
        if (prefs.goalAmount > 0) add(SavingsGoal(MAIN, prefs.goalName.ifBlank { "Savings goal" }, prefs.goalAmount, prefs.goalSaved, prefs.savingsPerMonth))
        addAll(prefs.goals)
    }
    val sorted = GoalList.byFinish(goals)
    val firstId = sorted.firstOrNull { !it.reached && it.monthsLeft != null }?.id
    val perMonth = goals.sumOf { it.monthly }
    var editing by remember { mutableStateOf<SavingsGoal?>(null) } // id -1 = new
    var adding by remember { mutableStateOf<SavingsGoal?>(null) }
    var addText by remember { mutableStateOf("") }
    val today = LocalDate.now()

    Scaffold(
        topBar = { BackBar("Goals", { nav.back() }, actions = { TextButton(onClick = { editing = SavingsGoal(-1, "", 0.0, 0.0, 0.0) }) { Text("Add a goal") } }) },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val piggy = Piggy.total(prefs.piggy)
            SectionCard(container = MaterialTheme.colorScheme.secondaryContainer, onClick = { nav.open(Route.Piggy) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Label("Piggy bank")
                        Text(Money.short(piggy) + " " + cur, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.titleLarge)
                    }
                    Text("›", color = MaterialTheme.colorScheme.outline)
                }
            }
            if (goals.isEmpty()) {
                SectionCard {
                    Text("No goals yet. Add one (Umra, a car, a laptop) and see when you will reach it.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Gap(8)
                    Button(onClick = { editing = SavingsGoal(-1, "", 0.0, 0.0, 0.0) }) { Text("Add a goal") }
                }
            } else {
                Text(
                    (if (goals.size == 1) "1 goal. You put " + Money.short(perMonth) + " a month towards it."
                    else goals.size.toString() + " goals. You put " + Money.short(perMonth) + " a month towards them."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                for (g in sorted) {
                    GoalRow(g, g.id == firstId && goals.size > 1, cur, today, onEdit = { editing = g }, onAdd = { addText = ""; adding = g })
                }
            }
            Gap(24)
        }
    }

    val e = editing
    if (e != null) {
        var name by remember(e.id) { mutableStateOf(if (e.id == MAIN) prefs.goalName else e.name) }
        var target by remember(e.id) { mutableStateOf(numText(e.target)) }
        var saved by remember(e.id) { mutableStateOf(numText(e.saved)) }
        var monthly by remember(e.id) { mutableStateOf(numText(e.monthly)) }
        var deadline by remember(e.id) { mutableStateOf(e.deadline?.let { LocalDate.ofEpochDay(it) }) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (e.id == -1L) "New goal" else "Edit goal") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                    TextInput("Goal name", name, { name = it })
                    NumberField("Goal amount", target, { target = it }, suffix = cur)
                    NumberField("Saved so far", saved, { saved = it }, suffix = cur)
                    NumberField("Put towards it each month", monthly, { monthly = it }, suffix = cur)
                    // Only goals in the list have a target date (the main goal from Settings does not).
                    if (e.id != MAIN) DateField("Target date (optional)", deadline, { deadline = it }, allowClear = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val t = parseNumber(target) ?: 0.0
                    val sv = parseNumber(saved) ?: 0.0
                    val m = parseNumber(monthly) ?: 0.0
                    if (t > 0) scope.launch {
                        when (e.id) {
                            MAIN -> {
                                repo.set("goalName", name.trim()); repo.set("goalAmount", t); repo.set("goalSaved", sv); repo.set("savingsPerMonth", m)
                            }
                            -1L -> repo.saveGoals(prefs.goals + SavingsGoal(GoalList.nextId(prefs.goals), name.trim().ifBlank { "Savings goal" }, t, sv, m, deadline?.toEpochDay()))
                            else -> repo.saveGoals(prefs.goals.map { if (it.id == e.id) it.copy(name = name.trim().ifBlank { "Savings goal" }, target = t, saved = sv, monthly = m, deadline = deadline?.toEpochDay()) else it })
                        }
                    }
                    editing = null
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    if (e.id != -1L) TextButton(onClick = {
                        scope.launch {
                            if (e.id == MAIN) { repo.set("goalName", ""); repo.set("goalAmount", 0.0); repo.set("goalSaved", 0.0) }
                            else repo.saveGoals(prefs.goals.filter { it.id != e.id })
                        }
                        editing = null
                    }) { Text("Delete this goal", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { editing = null }) { Text("Cancel") }
                }
            },
        )
    }
    val a = adding
    if (a != null) {
        AlertDialog(
            onDismissRequest = { adding = null },
            title = { Text("Add to goal") },
            text = {
                Column {
                    NumberField("Amount", addText, { addText = it }, suffix = cur)
                    Text("Also goes into your piggy bank", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val v = parseNumber(addText) ?: 0.0
                    if (v > 0) scope.launch { repo.savePiggy(v, goalId = if (a.id == MAIN) 0L else a.id) }
                    adding = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { adding = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun GoalRow(g: SavingsGoal, first: Boolean, cur: String, today: LocalDate, onEdit: () -> Unit, onAdd: () -> Unit) {
    val frac = g.fraction.toFloat()
    val track = MaterialTheme.colorScheme.surfaceVariant
    val ring = if (g.reached) Good else MaterialTheme.colorScheme.primary
    SectionCard(onClick = onEdit, container = if (first) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(72.dp)) {
                    val stroke = 9.dp.toPx()
                    val inset = stroke / 2
                    val sz = Size(size.width - stroke, size.height - stroke)
                    drawArc(track, -90f, 360f, false, Offset(inset, inset), sz, style = Stroke(stroke))
                    if (frac > 0f) drawArc(ring, -90f, 360f * frac, false, Offset(inset, inset), sz, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Text((frac * 100).toInt().toString() + "%", fontWeight = FontWeight.Medium, fontSize = 15.sp)
            }
            Column(Modifier.weight(1f)) {
                if (first) Text(tr("Finishes first").uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                Text(g.name, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(Money.short(g.saved) + " of " + Money.short(g.target) + " " + cur, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (g.deadline != null) {
                    Text("Target: ${Money.short(g.target)} $cur · by ${LocalDate.ofEpochDay(g.deadline).format(dayYearFmt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (g.reached) {
                    Text("Goal reached!", color = Good, fontWeight = FontWeight.Medium)
                } else {
                    val m = g.monthsLeft
                    if (g.monthly > 0) Text(Money.short(g.monthly) + " a month", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (m != null) Text("Done by " + Planning.goalDate(today, m).format(monthYearFmt), fontWeight = FontWeight.Medium)
                    else Text("No date yet. Put an amount towards it each month.", style = MaterialTheme.typography.bodySmall, color = Warn)
                    val sooner = GoalList.monthsSoonerWith(g.target, g.saved, g.monthly, 1000.0)
                    if (sooner != null && sooner >= 6) {
                        Text("Add 1,000 a month and it moves up " + sooner + " months", style = MaterialTheme.typography.bodySmall, color = Warn)
                    }
                    TextButton(onClick = onAdd) { Text("Add to goal") }
                }
            }
        }
        val need = GoalNeed.need(g.target, g.saved, g.monthly, g.deadline?.let { LocalDate.ofEpochDay(it) }, today)
        if (need.status == GoalNeed.Status.ONTRACK || need.status == GoalNeed.Status.BEHIND) {
            Gap(8)
            val ok = need.status == GoalNeed.Status.ONTRACK
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (ok) "You need ${Money.short(need.needed!!.toDouble())} $cur a month and you save ${Money.short(g.monthly)} $cur"
                    else "You need ${Money.short(need.needed!!.toDouble())} $cur a month to finish by then (${need.months} months). You save ${Money.short(g.monthly)} $cur now.",
                    Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                )
                if (ok) Pill("On track", Good) else Pill("Behind", Warn)
            }
        }
    }
}
