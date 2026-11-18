package app.pocketta.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.pocketta.data.Online
import app.pocketta.data.Repo
import app.pocketta.notify.Notifier
import app.pocketta.widget.Widgets
import app.pocketta.core.Money
import app.pocketta.i18n.Tr
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Once a day: records the salary, sends payment reminders and refreshes widgets (the day changed). */
class DailyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = Repo.get(applicationContext)
        val p = repo.prefs()
        if (p.onboarded) {
            repo.ensureSalary()
            val today = LocalDate.now()
            val items = repo.upcoming(p, today.plusDays(15))
            for (u in items) {
                val remind = remindDays(repo, u.kind, u.id)
                if (!u.due.isAfter(today.plusDays(remind.toLong()))) Notifier.due(applicationContext, u, today, p.baseCurrency, p.lang)
            }
        }
        runCatching { Online.refreshRates(repo) }
        runCatching { Online.checkUpdate(repo, force = true)?.let { Notifier.update(applicationContext, it.version, p.lang) } }
        if (p.onboarded) {
            runCatching { repo.autoBackupIfDue() }
            val fresh = repo.prefs()
            if (fresh.backupReminder && fresh.autoBackup == "OFF") {
                val since = if (fresh.lastBackup > 0) fresh.lastBackup
                else applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0).firstInstallTime
                val days = (System.currentTimeMillis() - since) / 86_400_000L
                // Remind once a week while there's no recent backup.
                if (days >= 7 && days % 7 == 0L) Notifier.backupReminder(applicationContext, if (fresh.lastBackup > 0) days else 999, fresh.lang)
            }
        }
        if (p.onboarded && p.morning) runCatching { morningMessage(repo) }
        Widgets.updateAll(applicationContext)
        return Result.success()
    }

    /** "You can spend 250 EGP today", with the dollar and gold underneath (and the week's total on Friday). */
    private suspend fun morningMessage(repo: Repo) {
        val s = repo.home()
        val p = s.prefs
        val b = s.budget
        if (b.budget <= 0 && s.salaryExpected <= 0) return
        val ar = p.lang == Tr.AR
        val amount = Money.short(kotlin.math.abs(b.dailyAllowance))
        val title = when {
            b.dailyAllowance < 0 -> if (ar) "ميزانية النهارده سالب (${amount} ${p.baseCurrency})" else "Today's budget is negative ($amount ${p.baseCurrency})"
            ar -> "تقدر تصرف $amount ${p.baseCurrency} النهارده"
            else -> "You can spend $amount ${p.baseCurrency} today"
        }
        val lines = ArrayList<String>()
        if (s.today.dayOfWeek == DayOfWeek.FRIDAY) {
            val w = repo.week(s.today, s.dayReference)
            if (w.spent > 0) lines += (if (ar) "أسبوعك: صرفت " else "Your week: spent ") + Money.short(w.spent) + " " + p.baseCurrency
        }
        Notifier.ratesLine(p, p.lang)?.let { lines += it }
        Notifier.morning(applicationContext, title, if (lines.isEmpty()) null else lines.joinToString("\n"))
    }

    private suspend fun remindDays(repo: Repo, kind: String, id: Long): Int = when (kind) {
        "BILL" -> repo.dao.bill(id)?.remindDays
        "INSTALLMENT" -> repo.dao.installment(id)?.remindDays
        "CIRCLE" -> repo.dao.circle(id)?.remindDays
        else -> 1
    } ?: 1

    companion object {
        fun schedule(context: Context, hour: Int, replace: Boolean = false) {
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(hour.coerceIn(0, 23), 0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val delay = Duration.between(now, next).toMinutes()
            val req = PeriodicWorkRequestBuilder<DailyWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delay, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "daily",
                if (replace) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP,
                req,
            )
        }
    }
}
