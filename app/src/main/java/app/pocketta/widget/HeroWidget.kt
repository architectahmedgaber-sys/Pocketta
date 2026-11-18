package app.pocketta.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
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
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.pocketta.R
import app.pocketta.core.Money
import app.pocketta.data.HomeState
import app.pocketta.i18n.Tr
import app.pocketta.notify.Notifier

/**
 * The "card" widget: the same big coloured number as the Today screen (green, amber when most of today is spent,
 * red when over), with a progress bar and the dollar and gold under it.
 */
class HeroWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = Widgets.load(context)
        provideContent { GlanceTheme { HeroContent(context, s) } }
    }
}

/** Green, amber or red card background for today's status. */
fun heroBackground(s: HomeState): Int {
    val b = s.budget
    val frac = if (b.dailyAllowance > 0) ((b.dailyAllowance - b.leftToday) / b.dailyAllowance).toFloat() else 1f
    return when {
        b.leftToday < 0 -> R.drawable.w_bg_red
        frac >= 0.8f -> R.drawable.w_bg_amber
        else -> R.drawable.w_bg_green
    }
}

@Composable
private fun HeroContent(context: Context, s: HomeState?) {
    val size = LocalSize.current
    val compact = size.height < 100.dp
    val lang = WidgetLang.lang
    val bg = if (s != null) heroBackground(s) else R.drawable.w_bg_green
    Box(
        modifier = GlanceModifier.fillMaxSize().appWidgetBackground()
            .background(ImageProvider(bg)).clickable(openApp(context)).padding(if (compact) 12.dp else 18.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (s == null) {
            Text(Tr.t("Tap to set up Pocketta", lang), style = TextStyle(color = white, fontSize = 14.sp, fontWeight = FontWeight.Medium))
        } else {
            val b = s.budget
            val over = b.leftToday < 0
            val frac = if (b.dailyAllowance > 0) ((b.dailyAllowance - b.leftToday) / b.dailyAllowance).toFloat().coerceIn(0f, 1f) else 1f
            val amount = (if (over) "−" else "") + Money.short(kotlin.math.abs(b.leftToday))
            Column(modifier = GlanceModifier.fillMaxWidth()) {
                Text(
                    Tr.t(if (over) "Over today's budget" else "You can spend today", lang),
                    maxLines = 1, style = TextStyle(color = whiteSoft, fontSize = 12.sp),
                )
                Text(
                    amount + " " + s.prefs.baseCurrency,
                    maxLines = 1, style = TextStyle(color = white, fontSize = if (compact) 28.sp else 38.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(GlanceModifier.height(if (compact) 6.dp else 10.dp))
                LinearProgressIndicator(
                    progress = frac,
                    modifier = GlanceModifier.fillMaxWidth().height(8.dp).cornerRadius(4.dp),
                    color = white,
                    backgroundColor = ColorProvider(Color.White.copy(alpha = 0.3f)),
                )
                if (!compact) {
                    Spacer(GlanceModifier.height(10.dp))
                    val rates = Notifier.ratesLine(s.prefs, lang)
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        Text(
                            rates ?: Tr.t("${b.daysLeft} days left in this month", lang),
                            maxLines = 1, style = TextStyle(color = whiteSoft, fontSize = 12.sp),
                        )
                    }
                }
            }
        }
    }
}

class HeroWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HeroWidget()
}
