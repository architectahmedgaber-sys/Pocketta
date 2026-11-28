package app.pocketta.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pocketta.core.Money
import app.pocketta.core.QuickText
import app.pocketta.data.HomeState
import app.pocketta.data.Prefs
import app.pocketta.data.Repo
import app.pocketta.data.Txn
import app.pocketta.data.WeekData
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek

/** Type a sentence ("koshary 60", "yesterday taxi 85") or tap a repeated expense to log it in one step. */
@Composable
fun QuickAddCard(repo: Repo, prefs: Prefs) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val version by repo.version.collectAsState()
    var text by rememberSaveable { mutableStateOf("") }
    var hint by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf<Txn?>(null) }
    var savedCategory by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf(emptyList<app.pocketta.data.Suggestion>()) }
    LaunchedEffect(version) { suggestions = repo.suggestions() }
    LaunchedEffect(saved) {
        if (saved != null) {
            delay(7000)
            saved = null
        }
    }

    fun add(note: String, amount: Double, daysAgo: Int) {
        scope.launch {
            val t = repo.quickExpense(note, amount, daysAgo)
            val cats = repo.dao.categories()
            savedCategory = cats.firstOrNull { it.id == t.categoryId }?.name ?: "No category"
            saved = t
            hint = false
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            checkOverBudget(context, repo, prefs)
        }
    }

    fun submit() {
        val q = QuickText.parse(text)
        val a = q.amount
        if (a == null || a <= 0.0) {
            hint = true
            return
        }
        add(q.note, a, q.daysAgo)
        text = ""
    }

    SectionCard {
        Label("Quick add")
        Gap(6)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = text, onValueChange = { text = it; hint = false }, singleLine = true,
                placeholder = { Text("Type: koshary 60  or  yesterday taxi 85", maxLines = 1) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                shape = FieldShape,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { submit() }, enabled = text.isNotBlank()) { Text("Add") }
        }
        if (hint) {
            Gap(4)
            Text("Type what you spent, with the amount", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        val t = saved
        if (t != null) {
            Gap(8)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "✓  " + tr("Saved") + "  " + Money.fmt(t.amount) + " · " + tr(savedCategory),
                    Modifier.weight(1f), color = Good, fontWeight = FontWeight.Medium,
                )
                TextButton(onClick = { scope.launch { repo.deleteTxn(t); saved = null } }) { Text("Undo") }
            }
        }
        if (suggestions.isNotEmpty() && t == null) {
            Gap(8)
            Text("One tap to add again", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Gap(4)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (s in suggestions) {
                    AssistChip(onClick = { add(s.note, s.amount, 0) }, label = { Text(s.note + " " + Money.fmt(s.amount), maxLines = 1) })
                }
            }
        }
    }
}

/** On Friday and Saturday: how the last 7 days went, against the 7 before. Written by Miqrish when the user has a key. */
@Composable
fun WeekCard(repo: Repo, s: HomeState) {
    if (s.today.dayOfWeek != DayOfWeek.FRIDAY && s.today.dayOfWeek != DayOfWeek.SATURDAY) return
    val context = androidx.compose.ui.platform.LocalContext.current
    val version by repo.version.collectAsState()
    var data by remember { mutableStateOf<WeekData?>(null) }
    var ai by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(version, s.today) {
        val w = repo.week(s.today, s.dayReference)
        data = w
        if (w.spent > 0) {
            val saved = app.pocketta.ai.AiWeeklyStore.ensure(context, repo, s, w, generate = false)
            if (saved != null) ai = saved
            else if (app.pocketta.ai.AiConfig(context).key().isNotBlank() && s.prefs.weeklyAiOn) {
                busy = true
                ai = app.pocketta.ai.AiWeeklyStore.ensure(context, repo, s, w)
                busy = false
            }
        }
    }
    val d = data ?: return
    if (d.spent <= 0.0) return
    val cur = s.prefs.baseCurrency
    SectionCard(container = MaterialTheme.colorScheme.tertiaryContainer) {
        Label(if (ai != null) "Your week · from Miqrish" else "Your week")
        Gap(4)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Big(Money.short(d.spent) + " " + cur, size = 26)
            Box(Modifier.weight(1f))
            if (d.previous > 0) {
                val pct = ((d.spent - d.previous) / d.previous * 100).toInt()
                when {
                    pct > 0 -> Pill("▲ $pct% vs last week", MaterialTheme.colorScheme.error)
                    pct < 0 -> Pill("▼ ${-pct}% vs last week", Good)
                    else -> Pill("＝ vs last week")
                }
            }
        }
        Gap(6)
        val aiText = ai
        if (aiText != null) {
            Text(aiText, style = MaterialTheme.typography.bodyMedium)
        } else if (busy) {
            Text("Miqrish is writing your summary...", style = MaterialTheme.typography.bodySmall)
        } else {
            val pct = if (d.previous > 0) ((d.spent - d.previous) / d.previous * 100).toInt() else null
            val (first, second) = app.pocketta.core.Planning.weekSentences(d.spent, pct, d.topCategory, d.daysUnder, cur)
            Text(first, style = MaterialTheme.typography.bodyMedium)
            Text(second, style = MaterialTheme.typography.bodyMedium)
            Text("💡 " + app.pocketta.core.Streak.weeklyTip(d.spent, d.previous, d.topCategory, s.budget.flexLeft, s.budget.daysLeft), fontWeight = FontWeight.Medium)
            if (app.pocketta.ai.AiConfig(context).key().isBlank()) {
                Text("Add your own key in Miqrish to get this written for you every Friday.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** The saving challenge: days in a row where the last 7 days stayed under the weekly share of the flexible money. */
@Composable
fun StreakCard(s: HomeState) {
    val k = s.streak ?: return
    val cur = s.prefs.baseCurrency
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (k.days > 0) "🔥 ${k.days} " + (if (k.days == 1) "day" else "days") + " in a row" else "Saving challenge", fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            if (k.best > k.days) Text("best ${k.best}", style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (k.days > 0) "Every day the last 7 days stayed under" else "Keep the last 7 days under", style = MaterialTheme.typography.bodySmall)
            Text(Money.short(k.target) + " " + cur, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            if (k.days == 0) Text("to start a streak.", style = MaterialTheme.typography.bodySmall)
        }
        Gap(6)
        Progress((k.weekSpent / k.target).toFloat().coerceIn(0f, 1f), color = if (k.weekSpent > k.target) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        Gap(4)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(Money.short(kotlin.math.abs(k.weekLeft)) + " " + cur, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            Text(if (k.weekLeft >= 0) "left for this week" else "over this week", style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** A ring that fills as you save towards one goal (Umra, a car, a wedding...). */
@Composable
fun PiggyAutoCard(repo: Repo, prefs: Prefs) {
    if (prefs.piggyAuto <= 0 && prefs.piggyAutoPct <= 0) return
    val scope = rememberCoroutineScope()
    val version by repo.version.collectAsState()
    var due by remember { mutableStateOf(0.0) }
    androidx.compose.runtime.LaunchedEffect(version, prefs.piggyAuto, prefs.piggyAutoPct, prefs.piggyAutoDone) { due = repo.piggyAutoDue() }
    if (due <= 0) return
    SectionCard(container = MaterialTheme.colorScheme.secondaryContainer) {
        Text("Move to your piggy bank?", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.titleMedium)
        Text(Money.short(due) + " " + prefs.baseCurrency, fontWeight = FontWeight.Medium, fontSize = 28.sp)
        Text("Your monthly saving, now that the salary is in.", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Button(onClick = { scope.launch { repo.piggyAutoAnswer(true) } }) { Text("Yes, move it") }
            TextButton(onClick = { scope.launch { repo.piggyAutoAnswer(false) } }) { Text("Not this month") }
        }
    }
}

@Composable
fun GoalCard(repo: Repo, prefs: Prefs) {
    if (prefs.goalAmount <= 0.0) return
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    var addText by remember { mutableStateOf("") }
    val frac = (prefs.goalSaved / prefs.goalAmount).toFloat().coerceIn(0f, 1f)
    val track = MaterialTheme.colorScheme.surfaceVariant
    val ring = if (frac >= 1f) Good else MaterialTheme.colorScheme.primary
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(84.dp)) {
                    val stroke = 11.dp.toPx()
                    val inset = stroke / 2
                    val sz = Size(size.width - stroke, size.height - stroke)
                    drawArc(track, -90f, 360f, false, Offset(inset, inset), sz, style = Stroke(stroke))
                    if (frac > 0f) drawArc(ring, -90f, 360f * frac, false, Offset(inset, inset), sz, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Text((frac * 100).toInt().toString() + "%", fontWeight = FontWeight.Medium, fontSize = 18.sp)
            }
            Column(Modifier.weight(1f)) {
                Label("Savings goal")
                Text(prefs.goalName.ifBlank { "Savings goal" }, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    if (frac >= 1f) "Goal reached!" else "Saved " + Money.short(prefs.goalSaved) + " of " + Money.short(prefs.goalAmount),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { adding = true }) { Text("Add to goal") }
            }
        }
    }
    if (adding) {
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Add to goal") },
            text = { NumberField("Amount", addText, { addText = it }, suffix = prefs.baseCurrency) },
            confirmButton = {
                TextButton(onClick = {
                    val v = parseNumber(addText) ?: 0.0
                    if (v > 0) scope.launch { repo.savePiggy(v, goalId = 0L) }
                    addText = ""
                    adding = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } },
        )
    }
}
