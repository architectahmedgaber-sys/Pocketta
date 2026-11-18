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
            runCatching { gameyaTurns(repo, today, p.baseCurrency, p.lang) }
        }
        runCatching { Online.refreshRates(repo) }
        runCatching { Online.checkUpdate(repo, force = true) }
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
        if (p.onboarded && p.monthEndReminder) runCatching { monthEndMessage(repo) }
        if (p.onboarded) runCatching { priceRiseAlert(repo) }
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
            if (w.spent > 0) {
                val pct = if (w.previous > 0) ((w.spent - w.previous) / w.previous * 100).toInt() else null
                val (first, second) = app.pocketta.core.Planning.weekSentences(w.spent, pct, w.topCategory, w.daysUnder, p.baseCurrency)
                lines += Tr.t(first, p.lang) + " " + Tr.t(second, p.lang)
            }
        }
        Notifier.ratesLine(p, p.lang)?.let { lines += it }
        Notifier.morning(applicationContext, title, if (lines.isEmpty()) null else lines.joinToString("\n"))
    }

    /** On the last day of the month: if money is left and a savings goal isn't reached, suggest moving it there. */
    private suspend fun monthEndMessage(repo: Repo) {
        val s = repo.home()
        val p = s.prefs
        if (s.budget.daysLeft != 1) return
        val amount = app.pocketta.core.Savings.suggestMove(s.budget.left, p.goalAmount, p.goalSaved)
        if (amount > 0) Notifier.monthEnd(applicationContext, amount, p.goalName, p.baseCurrency, p.lang)
    }

    /** Once per month and category: spending in a category is running 20% or more above your 3-month average. */
    private suspend fun priceRiseAlert(repo: Repo) {
        val d = repo.planData()
        val top = d.rises.firstOrNull() ?: return
        val key = d.home.cycle.start.toEpochDay().toString() + ":" + top.name
        val p = d.home.prefs
        if (p.riseAlerted == key) return
        Notifier.priceRise(applicationContext, top.name, top.percent, p.lang)
        repo.set("riseAlerted", key)
    }

    /** A reminder the day before your turn in a gam'eya and again on the day, until you mark the payout received. */
    private suspend fun gameyaTurns(repo: Repo, today: LocalDate, currency: String, lang: String) {
        for (c in repo.dao.circles()) {
            if (c.payoutReceived) continue
            val circle = app.pocketta.core.Circle(c.share, c.months, c.myTurn, c.paid, LocalDate.ofEpochDay(c.firstDue))
            val s = app.pocketta.core.Circles.status(circle, false, today)
            if (s.daysToTurn in -3..1) Notifier.gameyaTurn(applicationContext, c.id, c.name, s.payout, s.daysToTurn, currency, lang)
        }
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
