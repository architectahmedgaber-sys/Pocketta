package app.pocketta.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.pocketta.data.Online
import app.pocketta.data.Repo
import app.pocketta.notify.Notifier
import java.util.concurrent.TimeUnit

/** Every few hours, when there is internet: looks for a newer Pocketta and shows one notification per new version. */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = Repo.get(applicationContext)
        val p = repo.prefs()
        if (!p.onboarded) return Result.success()
        val r = runCatching { Online.checkUpdate(repo, force = true) }.getOrNull()
        if (r != null && p.updateNotified != r.version && Notifier.canPost(applicationContext)) {
            Notifier.update(applicationContext, r.version, p.lang)
            repo.set("updateNotified", r.version)
        }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<UpdateWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("update-check", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
