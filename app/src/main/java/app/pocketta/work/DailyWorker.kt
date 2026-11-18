package app.pocketta.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.pocketta.data.Repo
import app.pocketta.notify.Notifier
import app.pocketta.widget.Widgets
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
                if (!u.due.isAfter(today.plusDays(remind.toLong()))) Notifier.due(applicationContext, u, today, p.baseCurrency)
            }
        }
        Widgets.updateAll(applicationContext)
        return Result.success()
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
