package app.pocketta.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.pocketta.core.Budget
import app.pocketta.core.Money
import app.pocketta.data.HomeState

/** Today's allowance: a ring when there is room, a number when there isn't. */
class TodayWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(
        setOf(DpSize(50.dp, 50.dp), DpSize(110.dp, 50.dp), DpSize(110.dp, 110.dp), DpSize(200.dp, 110.dp)),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = Widgets.load(context)
        provideContent {
            GlanceTheme {
                WidgetCard(s, openApp(context)) { if (s != null) TodayContent(s) }
            }
        }
    }
}

@Composable
private fun TodayContent(s: HomeState) {
    val size = LocalSize.current
    val b = s.budget
    val over = b.leftToday < 0
    val frac = if (b.dailyAllowance > 0) ((b.dailyAllowance - b.leftToday) / b.dailyAllowance).toFloat().coerceIn(0f, 1f) else 1f
    val level = if (over) Budget.Level.OVER else Budget.level(frac.toDouble(), 1.0)
    val color = if (over) badColor else null
    val main = if (over) "−" + Money.short(-b.leftToday) else Money.short(b.leftToday)

    if (size.height < 100.dp) {
        // Short: just the number.
        Column(modifier = GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            label("Today", 10)
            value(if (size.width < 100.dp) main else "$main ${s.prefs.baseCurrency}", if (size.width < 100.dp) 16 else 20, color)
        }
        return
    }
    val ring = ringSizeFor(size, if (size.width >= 190.dp) 0 else 18)
    if (size.width >= 190.dp) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RingBox(frac, levelRing(level), ring) { value(main, 17, color) }
            Spacer(GlanceModifier.width(14.dp))
            Column {
                label(if (over) "Over today's budget" else "Today you can spend", 12)
                value("$main ${s.prefs.baseCurrency}", 24, color)
                Spacer(GlanceModifier.height(2.dp))
                label("Daily ${Money.short(b.dailyAllowance)} · ${b.daysLeft} days left", 11)
            }
        }
    } else {
        Column(modifier = GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            RingBox(frac, levelRing(level), ring) { value(main, 16, color) }
            Spacer(GlanceModifier.height(4.dp))
            label(if (over) "over today" else "left today", 11)
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
