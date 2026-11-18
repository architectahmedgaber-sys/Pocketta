package app.pocketta.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
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
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import app.pocketta.core.Budget
import app.pocketta.core.Money
import app.pocketta.data.HomeState
import java.time.format.DateTimeFormatter
import java.util.Locale

class MonthWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(110.dp, 50.dp), DpSize(180.dp, 110.dp)))

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
    val b = s.budget
    val used = b.spent + b.committed
    val frac = if (b.budget > 0) (used / b.budget).toFloat().coerceIn(0f, 1f) else 1f
    val level = Budget.level(used, b.budget)
    val title = s.cycle.end.format(DateTimeFormatter.ofPattern("MMMM", Locale.US))
    val tall = LocalSize.current.height >= 100.dp
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            label(title, 12)
            Spacer(GlanceModifier.defaultWeight())
            label("${(frac * 100).toInt()}%", 12)
        }
        if (tall) value(Money.short(b.left) + " left", 22, if (b.left < 0) GlanceTheme.colors.error else null)
        Spacer(GlanceModifier.height(6.dp))
        LinearProgressIndicator(
            progress = frac,
            modifier = GlanceModifier.fillMaxWidth().height(8.dp).cornerRadius(4.dp),
            color = levelColor(level),
            backgroundColor = GlanceTheme.colors.secondaryContainer,
        )
        Spacer(GlanceModifier.height(6.dp))
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            label(if (tall) "Spent ${Money.short(b.spent)} · bills ${Money.short(b.committed)}" else "${Money.short(b.left)} left", 11)
            Spacer(GlanceModifier.defaultWeight())
            label("${b.daysLeft} days", 11)
        }
    }
}

class MonthWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonthWidget()
}
