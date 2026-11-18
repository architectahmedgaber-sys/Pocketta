package app.pocketta.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
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

object Widgets {
    suspend fun updateAll(context: Context) {
        TodayWidget().updateAll(context)
        AddWidget().updateAll(context)
        MonthWidget().updateAll(context)
        UpcomingWidget().updateAll(context)
    }

    suspend fun load(context: Context): HomeState? = runCatching {
        val repo = Repo.get(context)
        if (!repo.prefs().onboarded) null else repo.home()
    }.getOrNull()
}

val warnColor: ColorProvider = ColorProvider(day = Color(0xFF9A6B00), night = Color(0xFFFFC94D))
val goodColor: ColorProvider = ColorProvider(day = Color(0xFF1F7A3F), night = Color(0xFF8FD8A4))

fun openApp(context: Context, action: String? = null): Action =
    actionStartActivity(Intent(context, MainActivity::class.java).apply {
        if (action != null) this.action = action
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    })

@Composable
fun levelColor(level: Budget.Level): ColorProvider = when (level) {
    Budget.Level.OVER -> GlanceTheme.colors.error
    Budget.Level.NEAR -> warnColor
    else -> GlanceTheme.colors.primary
}

@Composable
fun WidgetCard(state: HomeState?, onClick: Action, content: @Composable () -> Unit) {
    val size = LocalSize.current
    val compact = size.height < 90.dp || size.width < 90.dp
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(24.dp)
            .background(GlanceTheme.colors.widgetBackground)
            .clickable(onClick)
            .padding(if (compact) 8.dp else 14.dp),
        contentAlignment = if (compact) Alignment.Center else Alignment.CenterStart,
    ) {
        if (state == null) {
            Text(
                if (compact) "Set up" else "Tap to set up Pocketta",
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium),
            )
        } else {
            content()
        }
    }
}

@Composable
fun label(text: String, size: Int = 12) = Text(
    text,
    maxLines = 1,
    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = size.sp),
)

@Composable
fun value(text: String, size: Int = 26, color: ColorProvider? = null) = Text(
    text,
    maxLines = 1,
    style = TextStyle(color = color ?: GlanceTheme.colors.onSurface, fontSize = size.sp, fontWeight = FontWeight.Bold),
)
