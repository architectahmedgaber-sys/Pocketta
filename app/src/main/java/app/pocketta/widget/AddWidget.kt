package app.pocketta.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.pocketta.MainActivity

class AddWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                val small = LocalSize.current.height < 90.dp || LocalSize.current.width < 90.dp
                Column(
                    modifier = GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(24.dp)
                        .background(GlanceTheme.colors.widgetBackground)
                        .clickable(openApp(context, MainActivity.ACTION_ADD)).padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = GlanceModifier.size(if (small) 36.dp else 44.dp).cornerRadius(14.dp).background(GlanceTheme.colors.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("+", style = TextStyle(color = GlanceTheme.colors.onPrimary, fontSize = 26.sp, fontWeight = FontWeight.Medium))
                    }
                    if (!small) label("Add expense", 12)
                }
            }
        }
    }
}

class AddWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AddWidget()
}
