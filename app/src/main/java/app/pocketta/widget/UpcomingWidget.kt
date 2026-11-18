package app.pocketta.widget

import android.content.Context
import androidx.compose.runtime.Composable
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
import app.pocketta.MainActivity
import app.pocketta.core.Money
import app.pocketta.data.HomeState
import app.pocketta.i18n.Tr
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The next bills, installments and circle shares, as a short list. */
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
    val rows = ((LocalSize.current.height.value - 54) / 26).toInt().coerceIn(1, 6)
    val fmt = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    val lang = WidgetLang.lang
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        label("Next up", 12)
        Spacer(GlanceModifier.height(6.dp))
        if (s.upcoming.isEmpty()) {
            label("Nothing due soon", 13)
        }
        for (u in s.upcoming.take(rows)) {
            val overdue = u.due.isBefore(s.today)
            Row(modifier = GlanceModifier.fillMaxWidth().height(26.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Tr.t(u.title, lang), maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(color = inkColor, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    Tr.t(if (overdue) "late" else u.due.format(fmt), lang), maxLines = 1,
                    style = TextStyle(color = if (overdue) badColor else subColor, fontSize = 12.sp),
                )
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    (if (u.incoming) "+" else "") + Money.short(u.amount), maxLines = 1,
                    style = TextStyle(
                        color = if (overdue) badColor else if (u.incoming) goodColor else inkColor,
                        fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
    }
}

class UpcomingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UpcomingWidget()
}
