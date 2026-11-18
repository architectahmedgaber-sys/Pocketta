package app.pocketta.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import app.pocketta.core.Budget
import app.pocketta.core.Money
import app.pocketta.data.HomeState
import java.time.format.DateTimeFormatter
import java.util.Locale

/** This month's budget: a ring of what is spent, and what is left. */
class MonthWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = Widgets.load(context)
        provideContent {
            GlanceTheme {
                WidgetCard(s, openApp(context)) { if (s != null) MonthContent(s) }
            }
        }
    }
}

@Composable
private fun MonthContent(s: HomeState) {
    val size = LocalSize.current
    val b = s.budget
    val used = b.spent + b.committed
    val frac = if (b.budget > 0) (used / b.budget).toFloat().coerceIn(0f, 1f) else 1f
    val level = Budget.level(used, b.budget)
    val title = s.cycle.end.format(DateTimeFormatter.ofPattern("MMMM", Locale.US))
    val leftColor = if (b.left < 0) badColor else null
    if (size.height >= 100.dp) {
        val ring = ringSizeFor(size, 0)
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RingBox(frac, levelRing(level), ring) { value("${(frac * 100).toInt()}%", 16) }
            if (size.width >= 170.dp) {
                Spacer(GlanceModifier.width(14.dp))
                Column {
                    label(title, 12)
                    value(Money.short(b.left) + " left", 22, leftColor)
                    Spacer(GlanceModifier.height(2.dp))
                    label("Spent ${Money.short(b.spent)} · bills ${Money.short(b.committed)}", 11)
                    label("${b.daysLeft} days left", 11)
                }
            }
        }
    } else {
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                label(title, 12)
                Spacer(GlanceModifier.defaultWeight())
                label("${(frac * 100).toInt()}%", 12)
            }
            Spacer(GlanceModifier.height(6.dp))
            LinearProgressIndicator(
                progress = frac,
                modifier = GlanceModifier.fillMaxWidth().height(8.dp).cornerRadius(4.dp),
                color = levelColor(level),
                backgroundColor = trackColor,
            )
            Spacer(GlanceModifier.height(6.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                label("${Money.short(b.left)} left", 11)
                Spacer(GlanceModifier.defaultWeight())
                label("${b.daysLeft} days", 11)
            }
        }
    }
}

class MonthWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonthWidget()
}
