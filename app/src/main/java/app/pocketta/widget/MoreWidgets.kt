package app.pocketta.widget

import android.content.Context
import androidx.compose.runtime.Composable
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
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.pocketta.MainActivity
import app.pocketta.core.Budget
import app.pocketta.core.Money
import app.pocketta.data.HomeState
import app.pocketta.ui.Emoji
import java.time.format.DateTimeFormatter
import java.util.Locale

private suspend fun <T> safe(block: suspend () -> T): T? = runCatching { block() }.getOrNull()

private fun symbol(name: String): String = Emoji.of(name) ?: "🧾"

// ============ Week chart ============

class WeekWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = Widgets.load(context)
        val days = s?.let { safe { WidgetData.weekDays(context, it) } }.orEmpty()
        provideContent {
            GlanceTheme {
                WidgetCard(s, openApp(context)) { if (s != null) WeekContent(s, days) }
            }
        }
    }
}

@Composable
private fun WeekContent(s: HomeState, days: List<Pair<java.time.LocalDate, Double>>) {
    val size = LocalSize.current
    val total = days.sumOf { it.second }
    val ref = s.dayReference
    val top = maxOf(days.maxOfOrNull { it.second } ?: 0.0, ref, 1.0)
    val chartH = (size.height.value - 86f).coerceIn(24f, 120f)
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("This week", "الأسبوع ده"), maxLines = 1, style = TextStyle(color = inkColor, fontSize = 14.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.defaultWeight())
            Pill(Money.short(total) + " " + s.prefs.baseCurrency, goodColor)
        }
        Spacer(GlanceModifier.height(8.dp))
        Row(modifier = GlanceModifier.fillMaxWidth().height(chartH.dp), verticalAlignment = Alignment.Bottom) {
            for ((d, v) in days) {
                val h = (v / top * chartH).toFloat().coerceAtLeast(4f)
                val color = if (d == s.today && v <= ref) goodColor else levelColor(Budget.level(v, ref))
                Column(modifier = GlanceModifier.defaultWeight().padding(horizontal = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = GlanceModifier.fillMaxWidth().height(h.dp).cornerRadius(6.dp).background(color)) {}
                }
            }
        }
        Spacer(GlanceModifier.height(4.dp))
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            for ((d, _) in days) {
                Text(
                    app.pocketta.i18n.Tr.t(d.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.US), WidgetLang.lang),
                    maxLines = 1, modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(color = subColor, fontSize = 10.sp, textAlign = TextAlign.Center),
                )
            }
        }
    }
}

class WeekWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeekWidget()
}

// ============ Savings goal ============

class GoalWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = Widgets.load(context)
        provideContent {
            GlanceTheme {
                WidgetCard(s, openApp(context, MainActivity.ACTION_SETTINGS)) { if (s != null) GoalContent(s) }
            }
        }
    }
}

@Composable
private fun GoalContent(s: HomeState) {
    val p = s.prefs
    if (p.goalAmount <= 0) {
        Column {
            label(tr("Savings goal", "هدف الادخار"), 12)
            Spacer(GlanceModifier.height(4.dp))
            label(tr("Tap to set a goal", "دوس وحدد هدف"), 13, inkColor)
        }
        return
    }
    val size = LocalSize.current
    val frac = (p.goalSaved / p.goalAmount).toFloat().coerceIn(0f, 1f)
    val ring = ringSizeFor(size, if (size.width >= 190.dp) 0 else 18)
    val name = p.goalName.ifBlank { tr("Savings goal", "هدف الادخار") }
    if (size.width >= 190.dp) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RingBox(frac, RING_GREEN, ring) { value("${(frac * 100).toInt()}%", 16) }
            Spacer(GlanceModifier.width(14.dp))
            Column {
                label(name, 12)
                value(Money.short(p.goalSaved), 24)
                label(tr("of ${Money.short(p.goalAmount)} ${p.baseCurrency}", "من ${Money.short(p.goalAmount)} ${p.baseCurrency}"), 11)
            }
        }
    } else {
        Column(modifier = GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            RingBox(frac, RING_GREEN, ring) { value("${(frac * 100).toInt()}%", 16) }
            Spacer(GlanceModifier.height(4.dp))
            label(name, 11)
        }
    }
}

class GoalWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GoalWidget()
}

// ============ Next bill or installment ============

class NextDueWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = Widgets.load(context)
        provideContent {
            GlanceTheme {
                WidgetCard(s, openApp(context, MainActivity.ACTION_BILLS)) { if (s != null) NextDueContent(s) }
            }
        }
    }
}

@Composable
private fun NextDueContent(s: HomeState) {
    val u = s.upcoming.firstOrNull { !it.incoming }
    if (u == null) {
        Column {
            label(tr("Next due", "الجاي عليك"), 12)
            Spacer(GlanceModifier.height(4.dp))
            value(tr("All clear", "مفيش حاجة"), 18, goodColor)
        }
        return
    }
    val days = java.time.temporal.ChronoUnit.DAYS.between(s.today, u.due).toInt()
    val urgent = days <= 3
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        label(tr("Next due", "الجاي عليك"), 12)
        Spacer(GlanceModifier.height(2.dp))
        value(Money.short(u.amount), 28)
        Text(
            app.pocketta.i18n.Tr.t(u.title, WidgetLang.lang), maxLines = 1,
            style = TextStyle(color = inkColor, fontSize = 14.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(GlanceModifier.height(6.dp))
        Pill(dayText(days), if (urgent) warnColor else goodColor)
    }
}

class NextDueWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextDueWidget()
}

// ============ Gam'eya countdown ============

class CircleWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = Widgets.load(context)
        val c = s?.let { safe { WidgetData.circle(context, it) } }
        provideContent {
            GlanceTheme {
                WidgetCard(s, openApp(context, MainActivity.ACTION_BILLS)) { if (s != null) CircleContent(s, c) }
            }
        }
    }
}

@Composable
private fun CircleContent(s: HomeState, c: CircleInfo?) {
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        label(tr("Gam'eya 🤝", "الجمعية 🤝"), 12)
        Spacer(GlanceModifier.height(4.dp))
        if (c == null) {
            label(tr("No gam'eya yet", "مفيش جمعية لسه"), 13, inkColor)
        } else if (c.received || c.daysToPayout < 0) {
            value(tr("Received ✓", "قبضت ✓"), 20, goodColor)
            label(tr("${Money.short(c.payout)} ${s.prefs.baseCurrency}", "${Money.short(c.payout)} ${s.prefs.baseCurrency}"), 12)
        } else {
            label(tr("Your turn in", "دورك بعد"), 13, inkColor)
            Row(verticalAlignment = Alignment.Bottom) {
                value("${c.daysToPayout}", 34, goodColor)
                Spacer(GlanceModifier.width(4.dp))
                Text(tr("days", "يوم"), style = TextStyle(color = subColor, fontSize = 14.sp))
            }
            label(tr("You get ${Money.short(c.payout)} ${s.prefs.baseCurrency}", "تقبض ${Money.short(c.payout)} ${s.prefs.baseCurrency}"), 11)
        }
    }
}

class CircleWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CircleWidget()
}

// ============ Dollar and gold ============

class RatesWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = Widgets.load(context)
        provideContent {
            GlanceTheme {
                WidgetCard(s, openApp(context)) { if (s != null) RatesContent(s) }
            }
        }
    }
}

private data class RateItem(val name: String, val value: Double, val change: Double?)

@Composable
private fun RatesContent(s: HomeState) {
    val p = s.prefs
    val items = ArrayList<RateItem>()
    (p.rates["USD"] ?: p.onlineRates["USD"])?.let { items += RateItem(tr("Dollar", "دولار"), it, p.rateChange("USD")) }
    p.gold["21"]?.let { items += RateItem(tr("Gold 21", "ذهب 21"), it, p.goldChange("21")) }
    (p.rates["EUR"] ?: p.onlineRates["EUR"])?.let { items += RateItem(tr("Euro", "يورو"), it, p.rateChange("EUR")) }
    p.gold["24"]?.let { items += RateItem(tr("Gold 24", "ذهب 24"), it, p.goldChange("24")) }
    if (items.isEmpty()) {
        label(tr("Rates update once a day", "الأسعار بتتحدث مرة في اليوم"), 13, inkColor)
        return
    }
    val size = LocalSize.current
    @Composable fun arrow(ch: Double?) {
        val t = trend(ch)
        if (t.isNotEmpty()) Text(t, style = TextStyle(color = if ((ch ?: 0.0) > 0) goodColor else badColor, fontSize = 12.sp))
    }
    if (size.height < 100.dp) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            for ((i, r) in items.take(2).withIndex()) {
                if (i > 0) Spacer(GlanceModifier.width(18.dp))
                Column {
                    label(r.name, 11)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        value(Money.fmt(r.value), 19)
                        arrow(r.change)
                    }
                }
            }
        }
    } else {
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            label(tr("Rates", "الأسعار"), 12)
            Spacer(GlanceModifier.height(4.dp))
            for (r in items.take(4)) {
                Row(modifier = GlanceModifier.fillMaxWidth().height(26.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(r.name, maxLines = 1, modifier = GlanceModifier.defaultWeight(), style = TextStyle(color = subColor, fontSize = 13.sp))
                    value(Money.fmt(r.value), 16)
                    arrow(r.change)
                }
            }
        }
    }
}

class RatesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RatesWidget()
}

// ============ Debts ============

class DebtsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = Widgets.load(context)
        val d = s?.let { safe { WidgetData.debts(context) } }
        provideContent {
            GlanceTheme {
                WidgetCard(s, openApp(context)) { if (s != null) DebtsContent(s, d) }
            }
        }
    }
}

@Composable
private fun DebtsContent(s: HomeState, d: DebtSummary?) {
    if (d == null || (d.lent <= 0 && d.borrowed <= 0)) {
        Column {
            label(tr("Debts", "الديون"), 12)
            Spacer(GlanceModifier.height(4.dp))
            value(tr("All clear ✓", "مفيش ديون ✓"), 18, goodColor)
        }
        return
    }
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        label(tr("Debts", "الديون"), 12)
        Spacer(GlanceModifier.height(6.dp))
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                label(tr("Owed to you", "ليك عند الناس"), 11)
                value(Money.short(d.lent), 22, goodColor)
                label(tr("${d.lentCount} people", "${d.lentCount} حد"), 11)
            }
            Column(modifier = GlanceModifier.defaultWeight()) {
                label(tr("You owe", "عليك"), 11)
                value(Money.short(d.borrowed), 22, if (d.borrowed > 0) badColor else null)
                label(tr("${d.borrowedCount} people", "${d.borrowedCount} حد"), 11)
            }
        }
    }
}

class DebtsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DebtsWidget()
}

// ============ Top category ============

class TopCategoryWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = Widgets.load(context)
        val cats = s?.let { safe { WidgetData.categories(context, it) } }.orEmpty()
        provideContent {
            GlanceTheme {
                WidgetCard(s, openApp(context)) { if (s != null) TopCategoryContent(s, cats) }
            }
        }
    }
}

@Composable
private fun TopCategoryContent(s: HomeState, cats: List<CatSpend>) {
    val top = cats.firstOrNull()
    if (top == null) {
        Column {
            label(tr("Top spending", "أكتر حاجة صرفت فيها"), 12)
            Spacer(GlanceModifier.height(4.dp))
            label(tr("No spending yet", "لسه مفيش مصروف"), 13, inkColor)
        }
        return
    }
    val size = LocalSize.current
    val total = cats.sumOf { it.spent }
    val frac = if (total > 0) (top.spent / total).toFloat() else 0f
    val ring = ringSizeFor(size, if (size.width >= 190.dp) 0 else 18)
    val name = symbol(top.name) + " " + app.pocketta.i18n.Tr.t(top.name, WidgetLang.lang)
    if (size.width >= 190.dp) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RingBox(frac, RING_AMBER, ring) { value("${(frac * 100).toInt()}%", 16) }
            Spacer(GlanceModifier.width(14.dp))
            Column {
                label(tr("Top spending", "أكتر حاجة صرفت فيها"), 11)
                Text(name, maxLines = 1, style = TextStyle(color = inkColor, fontSize = 16.sp, fontWeight = FontWeight.Bold))
                label("${Money.short(top.spent)} ${s.prefs.baseCurrency}", 12)
            }
        }
    } else {
        Column(modifier = GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            RingBox(frac, RING_AMBER, ring) { value("${(frac * 100).toInt()}%", 16) }
            Spacer(GlanceModifier.height(4.dp))
            Text(name, maxLines = 1, style = TextStyle(color = inkColor, fontSize = 11.sp, fontWeight = FontWeight.Bold))
        }
    }
}

class TopCategoryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TopCategoryWidget()
}

// ============ Payday countdown ============

class SalaryWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = Widgets.load(context)
        val sal = s?.let { safe { WidgetData.salary(context, it) } }
        provideContent {
            GlanceTheme {
                WidgetCard(s, openApp(context, MainActivity.ACTION_SETTINGS)) { if (s != null) SalaryContent(s, sal) }
            }
        }
    }
}

@Composable
private fun SalaryContent(s: HomeState, sal: SalaryInfo?) {
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        label(tr("Payday", "يوم المرتب"), 12)
        Spacer(GlanceModifier.height(4.dp))
        if (sal == null) {
            label(tr("Tap to set your salary", "دوس وحدد مرتبك"), 13, inkColor)
        } else if (sal.days <= 0) {
            value(tr("Today 🎉", "النهارده 🎉"), 22, goodColor)
            label("${Money.short(sal.amount)} ${s.prefs.baseCurrency}", 12)
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                value("${sal.days}", 34, goodColor)
                Spacer(GlanceModifier.width(4.dp))
                Text(tr("days", "يوم"), style = TextStyle(color = subColor, fontSize = 14.sp))
            }
            label(tr("until your salary", "لحد المرتب"), 12)
            label(sal.date.format(DateTimeFormatter.ofPattern("MMM d", Locale.US)), 11)
        }
    }
}

class SalaryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SalaryWidget()
}

// ============ Category limit ============

class CategoryLimitWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = Widgets.load(context)
        val c = s?.let { safe { WidgetData.closestLimit(context, it) } }
        provideContent {
            GlanceTheme {
                WidgetCard(s, openApp(context)) { if (s != null) CategoryLimitContent(s, c) }
            }
        }
    }
}

@Composable
private fun CategoryLimitContent(s: HomeState, c: CatSpend?) {
    if (c == null) {
        Column {
            label(tr("Category limit", "حد الفئة"), 12)
            Spacer(GlanceModifier.height(4.dp))
            label(tr("Set a monthly limit on a category", "حدد حد شهري لفئة"), 12, inkColor)
        }
        return
    }
    val size = LocalSize.current
    val frac = (c.spent / c.limit).toFloat().coerceIn(0f, 1f)
    val level = Budget.level(c.spent, c.limit)
    val left = c.limit - c.spent
    val ring = ringSizeFor(size, if (size.width >= 190.dp) 0 else 18)
    val name = symbol(c.name) + " " + app.pocketta.i18n.Tr.t(c.name, WidgetLang.lang)
    val leftText = if (left >= 0) tr("${Money.short(left)} left", "فاضل ${Money.short(left)}") else tr("${Money.short(-left)} over", "زيادة ${Money.short(-left)}")
    if (size.width >= 190.dp) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RingBox(frac, levelRing(level), ring) { value("${(c.spent / c.limit * 100).toInt()}%", 16) }
            Spacer(GlanceModifier.width(14.dp))
            Column {
                Text(name, maxLines = 1, style = TextStyle(color = inkColor, fontSize = 16.sp, fontWeight = FontWeight.Bold))
                value(leftText, 20, levelColor(level))
                label(tr("of ${Money.short(c.limit)} ${s.prefs.baseCurrency}", "من ${Money.short(c.limit)} ${s.prefs.baseCurrency}"), 11)
            }
        }
    } else {
        Column(modifier = GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            RingBox(frac, levelRing(level), ring) { value("${(c.spent / c.limit * 100).toInt()}%", 16) }
            Spacer(GlanceModifier.height(4.dp))
            Text(name, maxLines = 1, style = TextStyle(color = inkColor, fontSize = 11.sp, fontWeight = FontWeight.Bold))
        }
    }
}

class CategoryLimitWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CategoryLimitWidget()
}

// ============ Latest transactions ============

class RecentWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = Widgets.load(context)
        val list = s?.let { safe { WidgetData.recent(context, it, 5) } }.orEmpty()
        provideContent {
            GlanceTheme {
                WidgetCard(s, openApp(context)) { if (s != null) RecentContent(context, list) }
            }
        }
    }
}

@Composable
private fun RecentContent(context: Context, list: List<RecentTxn>) {
    val rows = ((LocalSize.current.height.value - 60) / 26).toInt().coerceIn(1, 5)
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Latest", "آخر حاجة"), maxLines = 1, modifier = GlanceModifier.defaultWeight(), style = TextStyle(color = subColor, fontSize = 12.sp))
            Box(
                modifier = GlanceModifier.size(28.dp).cornerRadius(10.dp).background(primaryTile).clickable(openApp(context, MainActivity.ACTION_ADD)),
                contentAlignment = Alignment.Center,
            ) {
                Text("+", style = TextStyle(color = onPrimaryTile, fontSize = 20.sp, fontWeight = FontWeight.Medium))
            }
        }
        Spacer(GlanceModifier.height(4.dp))
        if (list.isEmpty()) label(tr("Nothing yet", "لسه مفيش حاجة"), 13, inkColor)
        for (t in list.take(rows)) {
            Row(modifier = GlanceModifier.fillMaxWidth().height(26.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    app.pocketta.i18n.Tr.t(t.note, WidgetLang.lang), maxLines = 1, modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(color = inkColor, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                )
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    (if (t.income) "+" else "−") + Money.short(t.amount), maxLines = 1,
                    style = TextStyle(color = if (t.income) goodColor else inkColor, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}

class RecentWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RecentWidget()
}

// ============ Quick category buttons ============

class QuickCatsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = Widgets.load(context)
        val cats = s?.let { safe { WidgetData.quickCats(context, it, 5) } }.orEmpty()
        provideContent { GlanceTheme { QuickCatsContent(context, s, cats) } }
    }
}

@Composable
private fun QuickCatsContent(context: Context, s: HomeState?, cats: List<QuickCat>) {
    val size = LocalSize.current
    val n = ((size.width.value - 24f) / 68f).toInt().coerceIn(2, 5)
    WidgetCard(s, openApp(context, MainActivity.ACTION_ADD)) {
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            if (size.height >= 100.dp) {
                label(tr("Quick add", "إضافة سريعة"), 12)
                Spacer(GlanceModifier.height(6.dp))
            }
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                for ((i, c) in cats.take(n).withIndex()) {
                    if (i > 0) Spacer(GlanceModifier.width(6.dp))
                    Column(
                        modifier = GlanceModifier.defaultWeight().cornerRadius(16.dp).background(chipBg).padding(vertical = 8.dp)
                            .clickable(openApp(context, MainActivity.ACTION_ADD, c.id)),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(symbol(c.name), style = TextStyle(fontSize = 22.sp, color = inkColor))
                        Text(
                            app.pocketta.i18n.Tr.t(c.name, WidgetLang.lang), maxLines = 1,
                            style = TextStyle(color = inkColor, fontSize = 10.sp, textAlign = TextAlign.Center),
                        )
                    }
                }
            }
        }
    }
}

class QuickCatsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickCatsWidget()
}

// ============ Green-days streak ============

class StreakWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = Widgets.load(context)
        provideContent {
            GlanceTheme {
                WidgetCard(s, openApp(context)) { if (s != null) StreakContent(s) }
            }
        }
    }
}

@Composable
private fun StreakContent(s: HomeState) {
    val n = WidgetData.streak(s)
    Column(modifier = GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (n >= 3) "🔥" else "🌱", style = TextStyle(fontSize = 26.sp, color = inkColor))
        value("$n", 32, if (n > 0) goodColor else subColor)
        label(tr("days under budget", "يوم تحت الميزانية"), 11)
    }
}

class StreakWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StreakWidget()
}
