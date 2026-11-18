package app.pocketta.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.pocketta.MainActivity
import app.pocketta.core.Money
import app.pocketta.data.Repo
import app.pocketta.data.Suggestion
import app.pocketta.i18n.Tr

val NoteKey = ActionParameters.Key<String>("note")
val AmountKey = ActionParameters.Key<String>("amount")

/** Logs a repeated expense straight from the widget, without opening the app. */
class QuickAddCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val note = parameters[NoteKey] ?: return
        val amount = parameters[AmountKey]?.toDoubleOrNull() ?: return
        Repo.get(context).quickExpense(note, amount)
    }
}

/** The "+" button, with one-tap buttons for the things you add again and again when there is room. */
class AddWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = Widgets.load(context)
        val sug = if (s != null) runCatching { WidgetData.suggestions(context, s) }.getOrDefault(emptyList()) else emptyList()
        provideContent { GlanceTheme { AddContent(context, sug) } }
    }
}

@Composable
private fun AddContent(context: Context, sug: List<Suggestion>) {
    val size = LocalSize.current
    val small = size.height < 90.dp || size.width < 90.dp
    val wide = size.width >= 200.dp && size.height < 110.dp
    val lang = WidgetLang.lang
    Box(
        modifier = GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(26.dp)
            .background(cardBg).padding(if (small) 6.dp else 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        val plus = @Composable {
            Box(
                modifier = GlanceModifier.size(if (small) 38.dp else 48.dp).cornerRadius(16.dp).background(primaryTile)
                    .clickable(openApp(context, MainActivity.ACTION_ADD)),
                contentAlignment = Alignment.Center,
            ) {
                Text("+", style = TextStyle(color = onPrimaryTile, fontSize = 28.sp, fontWeight = FontWeight.Medium))
            }
        }
        if (wide) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                plus()
                Spacer(GlanceModifier.width(12.dp))
                Text(Tr.t("Add expense", lang), maxLines = 1, style = TextStyle(color = inkColor, fontSize = 15.sp, fontWeight = FontWeight.Bold))
                val maxChips = if (size.width >= 300.dp) 2 else 1
                for (x in sug.take(maxChips)) {
                    Spacer(GlanceModifier.width(8.dp))
                    Box(
                        modifier = GlanceModifier.cornerRadius(14.dp).background(chipBg).padding(horizontal = 10.dp, vertical = 6.dp)
                            .clickable(actionRunCallback<QuickAddCallback>(actionParametersOf(NoteKey to x.note, AmountKey to x.amount.toString()))),
                    ) {
                        Text(
                            Tr.t(x.note, lang) + " " + Money.short(x.amount), maxLines = 1,
                            style = TextStyle(color = goodColor, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                        )
                    }
                }
            }
        } else {
            androidx.glance.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                plus()
                if (!small) label("Add expense", 12)
            }
        }
    }
}

class AddWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AddWidget()
}
