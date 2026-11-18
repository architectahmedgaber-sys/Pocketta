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
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import app.pocketta.core.Money
import app.pocketta.data.HomeState

class TodayWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(50.dp, 50.dp), DpSize(110.dp, 50.dp), DpSize(110.dp, 110.dp)))

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
    val color = if (over) GlanceTheme.colors.error else null
    val main = if (over) "−" + Money.short(-b.leftToday) else Money.short(b.leftToday)
    if (size.width < 100.dp) {
        Column(modifier = GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            label("Today", 10)
            value(main, 15, color)
        }
        return
    }
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        label(if (over) "Over today's budget" else "Today you can spend", 11)
        value("$main ${s.prefs.baseCurrency}", if (size.height < 100.dp) 20 else 26, color)
        if (size.height >= 100.dp) {
            Spacer(GlanceModifier.height(8.dp))
            val frac = if (b.dailyAllowance > 0) ((b.dailyAllowance - b.leftToday) / b.dailyAllowance).toFloat().coerceIn(0f, 1f) else 1f
            LinearProgressIndicator(
                progress = frac,
                modifier = GlanceModifier.fillMaxWidth().height(6.dp).cornerRadius(3.dp),
                color = if (over) GlanceTheme.colors.error else GlanceTheme.colors.primary,
                backgroundColor = GlanceTheme.colors.secondaryContainer,
            )
            Spacer(GlanceModifier.height(6.dp))
            label("Daily ${Money.short(b.dailyAllowance)} · ${b.daysLeft} days left", 11)
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
