package app.pocketta.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pocketta.core.Budget
import app.pocketta.core.Money

val chartPalette = listOf(
    Color(0xFF17493A), Color(0xFFC2571A), Color(0xFF2B5FB0), Color(0xFF8E3B8C),
    Color(0xFFE8A33D), Color(0xFF00687A), Color(0xFFB3261E), Color(0xFF6D7B74),
)

/** One slice of the donut. [limit] > 0 adds a small bar under the name that fills towards the category's monthly limit. */
data class DonutItem(val name: String, val value: Double, val limit: Double = 0.0)

/** A coloured ring that shows at a glance which categories take the biggest share, no numbers needed. */
@Composable
fun DonutCard(items: List<DonutItem>, total: Double, modifier: Modifier = Modifier) {
    if (total <= 0.0) return
    val shown = items.take(7) + (if (items.size > 7) listOf(DonutItem("Other", items.drop(7).sumOf { it.value })) else emptyList())
    SectionCard(modifier) {
        Label("Where it went")
        Gap(10)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 26.dp.toPx()
                    val inset = stroke / 2
                    val topLeft = Offset(inset, inset)
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    var start = -90f
                    shown.forEachIndexed { i, item ->
                        val sweep = (item.value / total * 360.0).toFloat()
                        drawArc(chartPalette[i % chartPalette.size], start, (sweep - 1.5f).coerceAtLeast(0.5f), false, topLeft, arcSize, style = Stroke(stroke))
                        start += sweep
                    }
                }
                Text("Spent", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                shown.forEachIndexed { i, item ->
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(chartPalette[i % chartPalette.size]))
                            Text(item.name, Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.bodyMedium)
                            Text("${(item.value / total * 100).toInt()}%", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (item.limit > 0) {
                            val level = Budget.level(item.value, item.limit)
                            val c = when (level) {
                                Budget.Level.OVER -> MaterialTheme.colorScheme.error
                                Budget.Level.NEAR -> Warn
                                else -> MaterialTheme.colorScheme.primary
                            }
                            Progress((item.value / item.limit).toFloat(), c, Modifier.padding(top = 3.dp))
                            Text(
                                Money.short(item.value) + " / " + Money.short(item.limit),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One bar per day of the month: tall bars are the expensive days. */
@Composable
fun DaysCard(values: List<Double>, modifier: Modifier = Modifier) {
    if (values.none { it > 0.0 }) return
    val max = values.max()
    val base = MaterialTheme.colorScheme.primary
    val peak = MaterialTheme.colorScheme.error
    val empty = MaterialTheme.colorScheme.surfaceVariant
    SectionCard(modifier) {
        Label("Which days cost the most")
        Gap(10)
        Canvas(Modifier.fillMaxWidth().height(90.dp)) {
            val n = values.size
            val gap = 3.dp.toPx()
            val w = ((size.width - gap * (n - 1)) / n).coerceAtLeast(1f)
            values.forEachIndexed { i, v ->
                val h = if (v <= 0.0) 3.dp.toPx() else (v / max * size.height).toFloat().coerceAtLeast(3.dp.toPx())
                val c = if (v <= 0.0) empty else if (v >= max * 0.85) peak else base
                drawRoundRect(c, Offset(i * (w + gap), size.height - h), Size(w, h), CornerRadius(4.dp.toPx()))
            }
        }
    }
}

/** This month against last month for the biggest categories: two bars each, longer bar = more money. */
@Composable
fun CompareCard(items: List<Triple<String, Double, Double>>, modifier: Modifier = Modifier) {
    if (items.isEmpty() || items.all { it.third <= 0.0 }) return
    val max = items.maxOf { maxOf(it.second, it.third) }.coerceAtLeast(1.0)
    SectionCard(modifier) {
        Label("This month vs last month")
        Gap(8)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                Text("This month", style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outline))
                Text("Last month", style = MaterialTheme.typography.bodySmall)
            }
        }
        for ((name, now, before) in items) {
            Gap(10)
            Text(name, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
            Gap(4)
            Progress((now / max).toFloat(), color = if (before > 0 && now > before * 1.2) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            Gap(3)
            Progress((before / max).toFloat(), color = MaterialTheme.colorScheme.outline)
        }
    }
}

/** The last months as bars (the newest is the right one), with how the newest compares to the month before. */
@Composable
fun TrendCard(months: List<Pair<String, Double>>, modifier: Modifier = Modifier) {
    if (months.count { it.second > 0 } < 2) return
    val max = months.maxOf { it.second }.coerceAtLeast(1.0)
    val now = months.last().second
    val prev = months.getOrNull(months.size - 2)?.second ?: 0.0
    val base = MaterialTheme.colorScheme.primary
    val old = MaterialTheme.colorScheme.outlineVariant
    SectionCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("Last 6 months", Modifier.weight(1f))
            if (prev > 0) {
                val pct = Math.round((now - prev) / prev * 100).toInt()
                when {
                    pct > 0 -> Pill("▲ $pct% vs last month", MaterialTheme.colorScheme.error)
                    pct < 0 -> Pill("▼ ${-pct}% vs last month", Good)
                    else -> Pill("＝ same as last month")
                }
            }
        }
        Gap(10)
        Row(Modifier.fillMaxWidth().height(130.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            months.forEachIndexed { i, (name, v) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    if (v > 0) Text(Money.short(v), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    Box(
                        Modifier.fillMaxWidth().height((if (v > 0) (v / max * 84.0).coerceAtLeast(4.0) else 3.0).dp)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)).background(if (i == months.lastIndex) base else old),
                    )
                    Text(name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
    }
}
