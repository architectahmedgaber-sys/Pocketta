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
    fun due(context: Context, u: Upcoming, today: LocalDate, currency: String) {
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
            .setContentTitle("${u.title} $whenText")
            .setContentText("${Money.fmt(u.amount)} $currency · ${u.subtitle}")
            .setContentIntent(openApp(context, MainActivity.ACTION_BILLS))
            .setAutoCancel(true)
        val label = if (u.kind == "LOAN") (if (u.incoming) "Got it back" else "Paid back") else "Mark paid"
        b.addAction(0, label, ActionReceiver.pending(context, ActionReceiver.ACTION_PAID, u.kind, u.id, id))
        if (u.kind == "BILL") b.addAction(0, "Skip this time", ActionReceiver.pending(context, ActionReceiver.ACTION_SKIP, u.kind, u.id, id))
        NotificationManagerCompat.from(context).notify(id, b.build())
    }

    @SuppressLint("MissingPermission")
    fun update(context: Context, version: String) {
        if (!canPost(context)) return
        val n = NotificationCompat.Builder(context, CH_APP)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Pocketta $version is ready")
            .setContentText("Tap to update. Your data stays as it is.")
            .setContentIntent(openApp(context, MainActivity.ACTION_UPDATE))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(ID_UPDATE, n)
    }

    @SuppressLint("MissingPermission")
    fun backupReminder(context: Context, days: Long) {
        if (!canPost(context)) return
        val text = if (days > 365) "You haven't saved a backup yet." else "Your last backup was $days days ago."
        val n = NotificationCompat.Builder(context, CH_APP)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Back up your money data")
            .setContentText("$text Turn on auto backup in Settings.")
            .setContentIntent(openApp(context, MainActivity.ACTION_SETTINGS))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(ID_BACKUP, n)
    }

    @SuppressLint("MissingPermission")
    fun overBudget(context: Context, over: Double, tomorrow: Double, currency: String) {
        if (!canPost(context)) return
        val n = NotificationCompat.Builder(context, CH_BUDGET)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("You're over today's budget by ${Money.fmt(over)} $currency")
            .setContentText("Tomorrow's allowance drops to ${Money.fmt(tomorrow)} $currency")
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(ID_BUDGET, n)
    }
}
