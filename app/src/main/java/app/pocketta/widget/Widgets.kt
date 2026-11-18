package app.pocketta.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.pocketta.MainActivity
import app.pocketta.core.Budget
import app.pocketta.data.HomeState
import app.pocketta.data.Repo
import app.pocketta.i18n.Tr

/** The language widgets draw in (set from the settings whenever a widget loads its data). */
object WidgetLang {
    @Volatile var lang: String = Tr.EN
}

object Widgets {
    suspend fun updateAll(context: Context) {
        HeroWidget().updateAll(context)
        TodayWidget().updateAll(context)
        AddWidget().updateAll(context)
        MonthWidget().updateAll(context)
        UpcomingWidget().updateAll(context)
        WeekWidget().updateAll(context)
        GoalWidget().updateAll(context)
        NextDueWidget().updateAll(context)
        CircleWidget().updateAll(context)
        RatesWidget().updateAll(context)
        DebtsWidget().updateAll(context)
        TopCategoryWidget().updateAll(context)
        SalaryWidget().updateAll(context)
        CategoryLimitWidget().updateAll(context)
        RecentWidget().updateAll(context)
        QuickCatsWidget().updateAll(context)
        StreakWidget().updateAll(context)
    }

    suspend fun load(context: Context): HomeState? = runCatching {
        val repo = Repo.get(context)
        val p = repo.prefs()
        WidgetLang.lang = p.lang
        if (!p.onboarded) null else repo.home()
    }.getOrNull()
}

// ---- Palette: the same greens as the app, readable on light and dark home screens ----
val cardBg: ColorProvider = ColorProvider(day = Color(0xFFFBFDF9), night = Color(0xFF232A26))
val inkColor: ColorProvider = ColorProvider(day = Color(0xFF18231D), night = Color(0xFFEEF4EE))
val subColor: ColorProvider = ColorProvider(day = Color(0xFF5C6B62), night = Color(0xFFAAB8AE))
val trackColor: ColorProvider = ColorProvider(day = Color(0xFFDCE8DF), night = Color(0xFF38433C))
val chipBg: ColorProvider = ColorProvider(day = Color(0xFFDCEFE2), night = Color(0xFF2F4A3C))
val goodColor: ColorProvider = ColorProvider(day = Color(0xFF1F7A3F), night = Color(0xFF8FD8A4))
val warnColor: ColorProvider = ColorProvider(day = Color(0xFF9A6B00), night = Color(0xFFFFC94D))
val badColor: ColorProvider = ColorProvider(day = Color(0xFFB3261E), night = Color(0xFFF2B8B5))
val onPrimaryTile: ColorProvider = ColorProvider(day = Color.White, night = Color(0xFF0B2A1A))
val primaryTile: ColorProvider = ColorProvider(day = Color(0xFF1F6B4F), night = Color(0xFF8FD8A4))
val white: ColorProvider = ColorProvider(Color.White)
val whiteSoft: ColorProvider = ColorProvider(Color.White.copy(alpha = 0.78f))

// Fixed colours for ring drawings (bitmaps can't follow light/dark, so these work on both).
const val RING_GREEN = 0xFF2E9E6E.toInt()
const val RING_AMBER = 0xFFE0A000.toInt()
const val RING_RED = 0xFFD9453B.toInt()

fun levelRing(level: Budget.Level): Int = when (level) {
    Budget.Level.OVER -> RING_RED
    Budget.Level.NEAR -> RING_AMBER
    else -> RING_GREEN
}

fun openApp(context: Context, action: String? = null, category: Long = 0L): Action =
    actionStartActivity(Intent(context, MainActivity::class.java).apply {
        if (action != null) this.action = action
        if (category != 0L) putExtra(MainActivity.EXTRA_CATEGORY, category)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    })

@Composable
fun levelColor(level: Budget.Level): ColorProvider = when (level) {
    Budget.Level.OVER -> badColor
    Budget.Level.NEAR -> warnColor
    else -> goodColor
}

/** The rounded card every widget sits on. */
@Composable
fun WidgetCard(state: HomeState?, onClick: Action, content: @Composable () -> Unit) {
    val size = LocalSize.current
    val compact = size.height < 90.dp || size.width < 90.dp
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(26.dp)
            .background(cardBg)
            .clickable(onClick)
            .padding(if (compact) 10.dp else 16.dp),
        contentAlignment = if (compact) Alignment.Center else Alignment.CenterStart,
    ) {
        if (state == null) {
            Text(
                Tr.t(if (compact) "Set up" else "Tap to set up Pocketta", WidgetLang.lang),
                style = TextStyle(color = inkColor, fontSize = 13.sp, fontWeight = FontWeight.Medium),
            )
        } else {
            content()
        }
    }
}

@Composable
fun label(text: String, size: Int = 12, color: ColorProvider? = null) = Text(
    Tr.t(text, WidgetLang.lang),
    maxLines = 1,
    style = TextStyle(color = color ?: subColor, fontSize = size.sp),
)

@Composable
fun value(text: String, size: Int = 26, color: ColorProvider? = null) = Text(
    text,
    maxLines = 1,
    style = TextStyle(color = color ?: inkColor, fontSize = size.sp, fontWeight = FontWeight.Bold),
)

/** A small rounded pill with text, used for dates and amounts. */
@Composable
fun Pill(text: String, textColor: ColorProvider, bg: ColorProvider = chipBg, size: Int = 12) {
    Box(
        modifier = GlanceModifier.cornerRadius(14.dp).background(bg).padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(Tr.t(text, WidgetLang.lang), maxLines = 1, style = TextStyle(color = textColor, fontSize = size.sp, fontWeight = FontWeight.Bold))
    }
}

/** New widget texts carry their own Arabic, so they read right without the word list. */
fun tr(en: String, ar: String): String = if (WidgetLang.lang == Tr.AR) ar else en

/** "today", "tomorrow", "in 3 days" or "2 days late". */
fun dayText(days: Int): String = when {
    days < -1 -> tr("${-days} days late", "متأخر ${-days} يوم")
    days == -1 -> tr("1 day late", "متأخر يوم")
    days == 0 -> tr("Today", "النهارده")
    days == 1 -> tr("Tomorrow", "بكره")
    else -> tr("in $days days", "بعد $days يوم")
}

fun trend(change: Double?): String = when {
    change == null || kotlin.math.abs(change) < 0.0005 -> ""
    change > 0 -> " ▲"
    else -> " ▼"
}
