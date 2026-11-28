package app.pocketta.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pocketta.core.Money
import app.pocketta.data.Prefs
import app.pocketta.data.Repo
import app.pocketta.share.SummaryImage
import java.time.LocalDate

/** A simple monthly report: spent, income, net, the month before, the top 3 categories and the top 3 expenses. */
@Composable
fun ReportScreen(repo: Repo, nav: Nav, prefs: Prefs, startOffset: Int) {
    val version by repo.version.collectAsState()
    var offset by remember { mutableIntStateOf(startOffset.coerceAtLeast(0)) }
    var data by remember { mutableStateOf<Repo.ReportData?>(null) }
    val context = LocalContext.current
    LaunchedEffect(offset, version) { data = repo.monthReport(offset) }
    val cur = prefs.baseCurrency
    val d = data

    Scaffold(topBar = { BackBar("Monthly report", { nav.back() }) }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (d == null) return@Column
            val r = d.report
            val monthName = d.cycle.end.format(monthFmt) + " " + d.cycle.end.year
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { offset++ }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month") }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Report " + d.cycle.end.format(monthFmt), style = MaterialTheme.typography.titleLarge)
                    Text("${d.cycle.start.format(dayFmt)} → ${d.cycle.end.format(dayFmt)}", style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { if (offset > 0) offset-- }, enabled = offset > 0) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
                }
            }

            // 1. Hero: spent, income, net and per day.
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(HeroGradient)).padding(20.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Spent", color = HeroText.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
                        Text(Money.short(r.spent), fontSize = 34.sp, fontWeight = FontWeight.Light, color = GoldLight, maxLines = 1)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Income", color = HeroText.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
                        Text(Money.short(r.income), fontSize = 26.sp, fontWeight = FontWeight.Light, color = HeroText, maxLines = 1)
                    }
                }
                Gap(10)
                Text(
                    "Net: ${if (r.net >= 0) "+" else "−"}${Money.short(kotlin.math.abs(r.net))} $cur · ${Money.short(r.perDay)} $cur a day",
                    color = HeroText.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall,
                )
            }

            if (r.spent <= 0) {
                SectionCard { Text("No spending recorded this month", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                return@Column
            }

            // 2. Compared with the month before.
            SectionCard {
                val prevName = d.previous.end.format(monthFmt)
                val pct = r.changePct
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Compared with $prevName", fontWeight = FontWeight.Medium)
                        val diff = r.spent - r.prevSpent
                        Text(
                            if (pct == null) "No data for the month before"
                            else if (diff >= 0) "You spent ${Money.short(diff)} $cur more than the month before"
                            else "You spent ${Money.short(-diff)} $cur less than the month before",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (pct != null) {
                        if (pct > 0) Pill("▲ $pct%", MaterialTheme.colorScheme.error)
                        else if (pct < 0) Pill("▼ ${-pct}%", Good)
                        else Pill("0%")
                    }
                }
            }

            // 3. Top 3 categories.
            SectionCard {
                Label("Top 3 categories")
                Gap(6)
                for (c in r.topCategories) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Row {
                            Text(c.name.ifBlank { "No category" }, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                            Text("${Money.short(c.amount)} · ${c.share}%")
                        }
                        Gap(4)
                        Progress(c.share / 100f)
                    }
                }
            }

            // 4. Top 3 expenses.
            SectionCard {
                Label("Top 3 expenses")
                Gap(6)
                for (t in r.topExpenses) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t.note.ifBlank { "—" }, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text(LocalDate.ofEpochDay(t.day).format(dayFmt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(Money.short(t.amount), fontWeight = FontWeight.Medium)
                    }
                }
            }

            // 5. Share the picture.
            OutlinedButton(onClick = {
                SummaryImage.share(
                    context,
                    SummaryImage.Data(
                        month = monthName, currency = cur, spent = r.spent, income = r.income, previousSpent = r.prevSpent,
                        categories = d.categories.map { SummaryImage.Slice(it.first.ifBlank { "No category" }, it.second) },
                    ),
                    prefs.lang,
                )
            }) { Text("Share picture") }
            Gap(24)
        }
    }
}
