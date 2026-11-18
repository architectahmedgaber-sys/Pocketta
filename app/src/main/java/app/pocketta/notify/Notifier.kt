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
    fun overBudget(context: Context, over: Double, tomorrow: Double, currency: String, lang: String = Tr.EN) {
        if (!canPost(context)) return
        val n = NotificationCompat.Builder(context, CH_BUDGET)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(if (lang == Tr.AR) "عدّيت ميزانية النهارده بـ ${Money.fmt(over)} $currency" else "You're over today's budget by ${Money.fmt(over)} $currency")
            .setContentText(if (lang == Tr.AR) "مصروف بكرة هيقل لـ ${Money.fmt(tomorrow)} $currency" else "Tomorrow's allowance drops to ${Money.fmt(tomorrow)} $currency")
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

    /** Last day of the month: offers to move what is left into the savings goal with one tap. */
    @SuppressLint("MissingPermission")
    fun monthEnd(context: Context, amount: Double, goalName: String, currency: String, lang: String = Tr.EN) {
        if (!canPost(context)) return
        val ar = lang == Tr.AR
        val goal = goalName.ifBlank { if (ar) "هدف الادخار" else "your savings goal" }
        val n = NotificationCompat.Builder(context, CH_BUDGET)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(if (ar) "الشهر خلصان وفاضلك ${Money.short(amount)} $currency" else "The month ends today and you have ${Money.short(amount)} $currency left")
            .setContentText(if (ar) "تحب تحطها في $goal؟" else "Move it to $goal?")
            .setContentIntent(openApp(context))
            .addAction(0, if (ar) "حطها في الهدف" else "Move to goal", ActionReceiver.goalPending(context, amount, ID_MONTH_END))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(ID_MONTH_END, n)
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
