package app.pocketta.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pocketta.core.Money
import app.pocketta.core.Piggy
import app.pocketta.data.Prefs
import app.pocketta.data.Repo
import kotlinx.coroutines.launch
import java.time.LocalDate

/** The piggy bank: everything you saved, month by month, with a running total. Put money in, or take some out. */
@Composable
fun PiggyScreen(repo: Repo, nav: Nav, prefs: Prefs) {
    val scope = rememberCoroutineScope()
    val cur = prefs.baseCurrency
    val total = Piggy.total(prefs.piggy)
    val months = Piggy.months(prefs.piggy, LocalDate.now())
    var dialog by remember { mutableStateOf<Int?>(null) } // 1 = put in, -1 = take out
    var text by remember { mutableStateOf("") }
    val avg = Piggy.averagePerMonth(months)
    val today = LocalDate.now()
    var spend by remember { mutableStateOf(0.0) }
    androidx.compose.runtime.LaunchedEffect(prefs.piggy.size, total) { spend = repo.monthlySpend() }
    val cover = Piggy.coverMonths(total, spend)
    var editTarget by remember { mutableStateOf(false) }
    var targetText by remember { mutableStateOf("") }

    Scaffold(topBar = { BackBar("Piggy bank", { nav.back() }) }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionCard(container = MaterialTheme.colorScheme.primary) {
                val on = MaterialTheme.colorScheme.onPrimary
                Text(tr("In your piggy bank").uppercase(), color = on.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall)
                Gap(4)
                Text(Money.short(total) + " " + cur, color = on, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.displaySmall)
                if (avg != null) Text(
                    "Saved over " + months.size + " months, about " + Money.short(avg) + " a month",
                    color = on.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium,
                )
                Gap(12)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { text = ""; dialog = 1 }, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = on, contentColor = MaterialTheme.colorScheme.primary),
                    ) { Text("Put in") }
                    OutlinedButton(
                        onClick = { text = ""; dialog = -1 }, modifier = Modifier.weight(1f), enabled = total > 0,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = on),
                    ) { Text("Take out") }
                }
            }
            if (cover != null) {
                SectionCard {
                    Label("How long it lasts")
                    Gap(4)
                    Text("Your piggy bank covers " + cover + " months of your spending", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                    Text("You spend about " + Money.short(spend) + " a month", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SectionCard {
                Label("Monthly target")
                Gap(4)
                val target = prefs.piggyTarget
                if (target <= 0) {
                    Text("Pick an amount to put in every month and see how many months in a row you hit it.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Gap(6)
                    Button(onClick = { targetText = ""; editTarget = true }) { Text("Set a monthly target") }
                } else {
                    val got = Piggy.thisMonth(months, today)
                    val streak = Piggy.streak(months, target, today)
                    val hit = got >= target
                    Text("This month: " + Money.short(got) + " of " + Money.short(target) + " " + cur, fontWeight = FontWeight.SemiBold)
                    Gap(6)
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { (got / target).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                        color = if (hit) Good else MaterialTheme.colorScheme.primary,
                    )
                    Gap(6)
                    Text(
                        if (hit) "You hit this month's target" else "Still " + Money.short(target - got) + " to go this month",
                        color = if (hit) Good else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val run = streak + if (hit) 1 else 0
                    Text(
                        when {
                            run == 0 -> "No streak yet. Hit the target this month to start one."
                            run == 1 -> "1 month in a row"
                            else -> "$run months in a row"
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                    TextButton(onClick = { targetText = numText(target); editTarget = true }) { Text("Change target") }
                }
            }
            if (months.isEmpty()) {
                SectionCard {
                    Text("Nothing in your piggy bank yet. Tap Put in when you set money aside.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                SectionCard {
                    Label("Saved each month")
                    Gap(10)
                    val shown = months.takeLast(6)
                    val top = shown.maxOf { it.saved }.coerceAtLeast(1.0)
                    Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                        shown.forEachIndexed { i, m ->
                            val last = i == shown.lastIndex
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                                Text(Money.short(m.saved), style = MaterialTheme.typography.labelSmall, fontWeight = if (last) FontWeight.Bold else FontWeight.Normal)
                                val h = (96 * (m.saved.coerceAtLeast(0.0) / top)).toInt().coerceAtLeast(3)
                                Column(
                                    Modifier.fillMaxWidth().padding(top = 4.dp).height(h.dp).clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                        .background(if (last) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                                ) {}
                                Text(m.month.format(shortMonthFmt), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
                SectionCard {
                    Label("Month by month")
                    Gap(4)
                    months.reversed().forEachIndexed { i, m ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(m.month.format(monthFmt), fontWeight = FontWeight.SemiBold)
                                if (i == 0) Text("so far", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(Money.signed(m.saved), fontWeight = FontWeight.SemiBold, color = if (m.saved < 0) MaterialTheme.colorScheme.error else Good)
                                Text("Total " + Money.short(m.total), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            Gap(24)
        }
    }

    if (editTarget) {
        AlertDialog(
            onDismissRequest = { editTarget = false },
            title = { Text("Monthly target") },
            text = { NumberField("Amount each month (0 to remove)", targetText, { targetText = it }, suffix = cur) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repo.set("piggyTarget", (parseNumber(targetText) ?: 0.0).coerceAtLeast(0.0)) }
                    editTarget = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editTarget = false }) { Text("Cancel") } },
        )
    }

    val mode = dialog
    if (mode != null) {
        AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(if (mode > 0) "Put in" else "Take out") },
            text = {
                Column {
                    NumberField("Amount", text, { text = it }, suffix = cur)
                    if (mode < 0) Text("In your piggy bank: " + Money.short(total) + " " + cur, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val v = (parseNumber(text) ?: 0.0).let { if (mode < 0) it.coerceAtMost(total) else it }
                    if (v > 0) scope.launch { repo.piggyAdd(mode * v) }
                    dialog = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
    }
}
