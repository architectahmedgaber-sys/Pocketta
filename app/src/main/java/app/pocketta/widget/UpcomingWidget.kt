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
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import app.pocketta.MainActivity
import app.pocketta.core.Money
import app.pocketta.data.HomeState
import java.time.format.DateTimeFormatter
import java.util.Locale

class UpcomingWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = Widgets.load(context)
        provideContent {
            GlanceTheme {
                WidgetCard(s, openApp(context, MainActivity.ACTION_BILLS)) { if (s != null) UpcomingContent(s) }
            }
        }
    }
}

@Composable
private fun UpcomingContent(s: HomeState) {
    val rows = ((LocalSize.current.height.value - 40) / 22).toInt().coerceIn(1, 6)
    val fmt = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        label("Next up", 12)
        Spacer(GlanceModifier.height(4.dp))
        if (s.upcoming.isEmpty()) {
            label("Nothing due soon", 13)
        }
        for (u in s.upcoming.take(rows)) {
            Row(modifier = GlanceModifier.fillMaxWidth().height(22.dp)) {
                val overdue = u.due.isBefore(s.today)
                Row(modifier = GlanceModifier.defaultWeight()) {
                    value(u.title, 13)
                    label("  " + if (overdue) "late" else u.due.format(fmt), 12)
                }
                value(
                    (if (u.incoming) "+" else "") + Money.short(u.amount), 13,
                    if (overdue) GlanceTheme.colors.error else if (u.incoming) goodColor else null,
                )
            }
        }
    }
}

class UpcomingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UpcomingWidget()
}
