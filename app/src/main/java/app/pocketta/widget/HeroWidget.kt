package app.pocketta.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.pocketta.core.Money
import app.pocketta.data.HomeState
import app.pocketta.i18n.Tr
import app.pocketta.notify.Notifier

/**
 * The "card" widget: the same big coloured number as the Today screen (green, amber when most of today is spent,
 * red when over), with the dollar and gold under it.
 */
class HeroWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = Widgets.load(context)
        provideContent { GlanceTheme { HeroContent(context, s) } }
    }
}

@Composable
private fun HeroContent(context: Context, s: HomeState?) {
    val size = LocalSize.current
    val compact = size.height < 100.dp
    val white = ColorProvider(Color.White)
    val soft = ColorProvider(Color.White.copy(alpha = 0.75f))
    val lang = WidgetLang.lang
    var bg = Color(0xFF1F6B4F)
    var frac = 0f
    var over = false
    if (s != null) {
        val b = s.budget
        over = b.leftToday < 0
        frac = if (b.dailyAllowance > 0) ((b.dailyAllowance - b.leftToday) / b.dailyAllowance).toFloat().coerceIn(0f, 1f) else 1f
        bg = when {
            over -> Color(0xFFB3261E)
            frac >= 0.8f -> Color(0xFF9A5B00)
            else -> Color(0xFF1F6B4F)
        }
    }
    Box(
        modifier = GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(24.dp)
            .background(ColorProvider(bg)).clickable(openApp(context)).padding(if (compact) 10.dp else 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (s == null) {
            Text(Tr.t("Tap to set up Pocketta", lang), style = TextStyle(color = white, fontSize = 14.sp, fontWeight = FontWeight.Medium))
        } else {
            val b = s.budget
            val amount = (if (over) "−" else "") + Money.short(kotlin.math.abs(b.leftToday))
            Column(modifier = GlanceModifier.fillMaxWidth()) {
                Text(
                    Tr.t(if (over) "Over today's budget" else "You can spend today", lang),
                    maxLines = 1, style = TextStyle(color = soft, fontSize = 12.sp),
                )
                Text(
                    amount + " " + s.prefs.baseCurrency,
                    maxLines = 1, style = TextStyle(color = white, fontSize = if (compact) 26.sp else 36.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(GlanceModifier.height(8.dp))
                LinearProgressIndicator(
                    progress = frac,
                    modifier = GlanceModifier.fillMaxWidth().height(6.dp).cornerRadius(3.dp),
                    color = white,
                    backgroundColor = ColorProvider(Color.White.copy(alpha = 0.3f)),
                )
                if (!compact) {
                    Spacer(GlanceModifier.height(8.dp))
                    val rates = Notifier.ratesLine(s.prefs, lang)
                    Text(
                        rates ?: Tr.t("${b.daysLeft} days left in this month", lang),
                        maxLines = 1, style = TextStyle(color = soft, fontSize = 12.sp),
                    )
                }
            }
        }
    }
}

class HeroWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HeroWidget()
}
