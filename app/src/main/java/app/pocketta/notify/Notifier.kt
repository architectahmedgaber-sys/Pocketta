package app.pocketta.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.pocketta.MainActivity
import app.pocketta.R
import app.pocketta.core.Money
import app.pocketta.data.Prefs
import app.pocketta.i18n.Tr
import app.pocketta.data.Upcoming
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

object Notifier {
    const val CH_DUE = "due"
    const val CH_BUDGET = "budget"
    private const val ID_BUDGET = 5
    private const val ID_UPDATE = 6
    private const val ID_BACKUP = 7
    const val CH_APP = "app"
    /** New versions of Pocketta. Its own channel with normal importance so the notification is actually seen. */
    const val CH_UPDATE = "update"
    const val CH_MORNING = "morning"
    private const val ID_MORNING = 8
    private const val ID_MONTH_END = 9
    private const val ID_PRICE_RISE = 10
    private const val ID_TEST = 11
    private const val ID_LIMIT_BASE = 1000
    private const val ID_SMS_BASE = 100000

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_DUE, "Payment reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Bills, installments, circle shares and loans that are due"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_BUDGET, "Budget alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "When you go over today's allowance"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_MORNING, "Morning message", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Today's number, the dollar and gold, every morning"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_UPDATE, "New versions", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "When a new version of Pocketta is ready to install"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_APP, "Updates and backups", NotificationManager.IMPORTANCE_LOW).apply {
                description = "New versions of Pocketta and backup reminders"
            }
        )
    }

    fun canPost(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(context: Context, action: String? = null): PendingIntent = PendingIntent.getActivity(
        context, action.hashCode(),
        Intent(context, MainActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun notificationId(u: Upcoming): Int = (u.kind.hashCode() * 31 + u.id.toInt()) and 0x7fffffff

    @SuppressLint("MissingPermission")
    fun due(context: Context, u: Upcoming, today: LocalDate, currency: String, lang: String = Tr.EN) {
        if (!canPost(context)) return
        val days = ChronoUnit.DAYS.between(today, u.due)
        val whenText = when {
            days < 0 -> "was due ${u.due.format(DateTimeFormatter.ofPattern("MMM d", Locale.US))}"
            days == 0L -> "due today"
            days == 1L -> "due tomorrow"
            else -> "due in $days days"
        }
        val id = notificationId(u)
        val b = NotificationCompat.Builder(context, CH_DUE)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("${u.title} ${Tr.t(whenText, lang)}")
            .setContentText("${Money.fmt(u.amount)} $currency · ${Tr.t(u.subtitle, lang)}")
            .setContentIntent(openApp(context, MainActivity.ACTION_BILLS))
            .setAutoCancel(true)
        val label = Tr.t(if (u.kind == "LOAN") (if (u.incoming) "Got it back" else "Paid back") else "Mark paid", lang)
        b.addAction(0, label, ActionReceiver.pending(context, ActionReceiver.ACTION_PAID, u.kind, u.id, id))
        if (u.kind == "BILL") b.addAction(0, Tr.t("Skip this time", lang), ActionReceiver.pending(context, ActionReceiver.ACTION_SKIP, u.kind, u.id, id))
        NotificationManagerCompat.from(context).notify(id, b.build())
    }

    /** One reminder for several payments of the same company or type, with one button to pay them all. */
    @SuppressLint("MissingPermission")
    fun dueGroup(context: Context, title: String, items: List<Upcoming>, today: LocalDate, currency: String, lang: String = Tr.EN) {
        if (!canPost(context) || items.isEmpty()) return
        val first = items.minOf { it.due }
        val days = ChronoUnit.DAYS.between(today, first)
        val whenText = when {
            days < 0 -> "late"
            days == 0L -> "due today"
            days == 1L -> "due tomorrow"
            else -> "due in $days days"
        }
        val id = (("group:" + title).hashCode()) and 0x7fffffff
        val total = items.sumOf { it.amount }
        val head = Tr.t(title, lang) + ": " + Tr.t("${items.size} payments", lang) + " " + Tr.t(whenText, lang)
        val body = items.joinToString(" · ") { it.title }
        val b = NotificationCompat.Builder(context, CH_DUE)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(head)
            .setContentText("${Money.fmt(total)} $currency · $body")
            .setStyle(NotificationCompat.BigTextStyle().bigText("${Money.fmt(total)} $currency\n$body"))
            .setContentIntent(openApp(context, MainActivity.ACTION_BILLS))
            .setAutoCancel(true)
        val ids = items.joinToString(",") { it.kind + ":" + it.id }
        b.addAction(0, Tr.t("Pay all", lang), ActionReceiver.groupPending(context, ids, id))
        NotificationManagerCompat.from(context).notify(id, b.build())
    }

    @SuppressLint("MissingPermission")
    fun update(context: Context, version: String, lang: String = Tr.EN) {
        if (!canPost(context)) return
        val n = NotificationCompat.Builder(context, CH_UPDATE)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(Tr.t("Pocketta $version is ready", lang))
            .setContentText(Tr.t("Tap to update. Your data stays as it is.", lang))
            .setContentIntent(openApp(context, MainActivity.ACTION_UPDATE))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(ID_UPDATE, n)
    }

    @SuppressLint("MissingPermission")
    fun backupReminder(context: Context, days: Long, lang: String = Tr.EN) {
        if (!canPost(context)) return
        val text = if (lang == Tr.AR) (if (days > 365) "لسه معملتش نسخة احتياطية." else "آخر نسخة احتياطية كانت من $days يوم.")
        else if (days > 365) "You haven't saved a backup yet." else "Your last backup was $days days ago."
        val n = NotificationCompat.Builder(context, CH_APP)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(Tr.t("Back up your money data", lang))
            .setContentText(text + (if (lang == Tr.AR) " فعّل النسخ التلقائي من الإعدادات." else " Turn on auto backup in Settings."))
            .setContentIntent(openApp(context, MainActivity.ACTION_SETTINGS))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(ID_BACKUP, n)
    }

    @SuppressLint("MissingPermission")
    fun overBudget(context: Context, over: Double, currency: String, lang: String = Tr.EN) {
        if (!canPost(context)) return
        val n = NotificationCompat.Builder(context, CH_BUDGET)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(if (lang == Tr.AR) "عدّيت المبلغ المرن بـ ${Money.fmt(over)} $currency" else "You're over the flexible amount by ${Money.fmt(over)} $currency")
            .setContentText(if (lang == Tr.AR) "فلوسك المرنة للشهر ده خلصت" else "This month's flexible money is used up")
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(ID_BUDGET, n)
    }

    /** The dollar and gold with an arrow for up or down since yesterday, e.g. "Dollar 49.85 ▲ · Gold 21 4,200 ▼". */
    fun ratesLine(p: Prefs, lang: String): String? {
        fun arrow(ch: Double?): String {
            if (ch == null || kotlin.math.abs(ch) < 0.0005) return ""
            return if (ch > 0) " ▲" else " ▼"
        }
        val parts = ArrayList<String>()
        (p.rates["USD"] ?: p.onlineRates["USD"])?.let {
            parts += Tr.t("Dollar", lang) + " " + Money.fmt(it) + (if (p.rates.containsKey("USD")) "" else arrow(p.rateChange("USD")))
        }
        p.gold["21"]?.let { parts += Tr.t("Gold", lang) + " 21 " + Money.fmt(it) + arrow(p.goldChange("21")) }
        return if (parts.isEmpty()) null else parts.joinToString(" · ")
    }

    /** Last day of the month: offers to put what is left into the piggy bank (and the savings goal, when one is open) with one tap. */
    @SuppressLint("MissingPermission")
    fun monthEnd(context: Context, left: Double, goalMove: Double, goalName: String, currency: String, lang: String = Tr.EN) {
        if (!canPost(context)) return
        val ar = lang == Tr.AR
        val goal = goalName.ifBlank { if (ar) "هدف الادخار" else "your savings goal" }
        val b = NotificationCompat.Builder(context, CH_BUDGET)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(if (ar) "الشهر خلصان وفاضلك ${Money.short(left)} $currency" else "The month ends today and you have ${Money.short(left)} $currency left")
            .setContentText(if (ar) "تحب تحطها في الحصالة؟" else "Put it in your piggy bank?")
            .setContentIntent(openApp(context))
            .addAction(0, if (ar) "حطها في الحصالة" else "Put in piggy bank", ActionReceiver.piggyPending(context, left, ID_MONTH_END))
            .setAutoCancel(true)
        if (goalMove > 0) b.addAction(0, if (ar) "حطها في $goal" else "Move to $goal", ActionReceiver.goalPending(context, goalMove, ID_MONTH_END))
        NotificationManagerCompat.from(context).notify(ID_MONTH_END, b.build())
    }

    /** A category went over its monthly limit. */
    @SuppressLint("MissingPermission")
    fun categoryLimit(context: Context, id: Long, name: String, spent: Double, limit: Double, currency: String, lang: String = Tr.EN) {
        if (!canPost(context)) return
        val ar = lang == Tr.AR
        val n = NotificationCompat.Builder(context, CH_BUDGET)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(if (ar) "عديت ميزانية ${Tr.t(name, lang)} الشهرية" else "You're over your $name limit")
            .setContentText(
                if (ar) "صرفت ${Money.short(spent)} من ${Money.short(limit)} $currency"
                else "Spent ${Money.short(spent)} of ${Money.short(limit)} $currency this month"
            )
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(ID_LIMIT_BASE + id.toInt(), n)
    }

    /** A bank text message that looks like spending: tap to open Add with the amount filled in. Nothing is saved until you do. */
    @SuppressLint("MissingPermission")
    fun smsExpense(context: Context, key: Int, amount: Double, currency: String, merchant: String, lang: String = Tr.EN) {
        if (!canPost(context)) return
        val ar = lang == Tr.AR
        val what = if (merchant.isBlank()) "" else " · $merchant"
        val open = PendingIntent.getActivity(
            context, key,
            Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_ADD)
                .putExtra(MainActivity.EXTRA_AMOUNT, amount).putExtra(MainActivity.EXTRA_NOTE, merchant).putExtra(MainActivity.EXTRA_CURRENCY, currency)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CH_BUDGET)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(if (ar) "سجّل مصروف ${Money.fmt(amount)} $currency؟" else "Add an expense of ${Money.fmt(amount)} $currency?")
            .setContentText((if (ar) "من رسالة البنك" else "From a bank message") + what)
            .setContentIntent(open)
            .addAction(0, if (ar) "سجّل" else "Add", open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(ID_SMS_BASE + (key and 0xffff), n)
    }

    /** The day before and on the day of your turn in a gam'eya: how much you collect, with a one-tap "Got it". */
    @SuppressLint("MissingPermission")
    fun gameyaTurn(context: Context, circleId: Long, name: String, payout: Double, daysLeft: Int, currency: String, lang: String = Tr.EN) {
        if (!canPost(context)) return
        val ar = lang == Tr.AR
        val title = when {
            ar && daysLeft <= 0 -> "دورك في جمعية $name النهارده!"
            ar -> "دورك في جمعية $name بكرة"
            daysLeft <= 0 -> "It's your turn in $name today!"
            else -> "Your turn in $name is tomorrow"
        }
        val id = ("PAYOUT".hashCode() * 31 + circleId.toInt()) and 0x7fffffff
        val n = NotificationCompat.Builder(context, CH_DUE)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText((if (ar) "هتقبض " else "You collect ") + Money.short(payout) + " " + currency)
            .setContentIntent(openApp(context, MainActivity.ACTION_BILLS))
            .addAction(0, if (ar) "استلمت" else "Got it", ActionReceiver.pending(context, ActionReceiver.ACTION_PAID, "PAYOUT", circleId, id))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, n)
    }

    @SuppressLint("MissingPermission")
    fun priceRise(context: Context, category: String, percent: Int, lang: String = Tr.EN) {
        if (!canPost(context)) return
        val ar = lang == Tr.AR
        val n = NotificationCompat.Builder(context, CH_BUDGET)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(if (ar) "${Tr.t(category, lang)} طالع $percent% عن المعتاد" else "$category is up $percent% on your usual")
            .setContentText(if (ar) "بالمعدل ده، مقارنة بمتوسط آخر 3 شهور" else "At this pace, compared with your last 3 months")
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(ID_PRICE_RISE, n)
    }

    /** The "Send test notification" button: proves that notifications reach this phone. */
    @SuppressLint("MissingPermission")
    fun test(context: Context, lang: String = Tr.EN) {
        if (!canPost(context)) return
        val n = NotificationCompat.Builder(context, CH_DUE)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(Tr.t("Test notification", lang))
            .setContentText(Tr.t("Notifications work on this phone.", lang))
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(ID_TEST, n)
    }

    @SuppressLint("MissingPermission")
    fun morning(context: Context, title: String, text: String?) {
        if (!canPost(context)) return
        val b = NotificationCompat.Builder(context, CH_MORNING)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
        if (text != null) b.setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
        NotificationManagerCompat.from(context).notify(ID_MORNING, b.build())
    }
}
