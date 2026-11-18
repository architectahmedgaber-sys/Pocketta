package app.pocketta

import android.app.Application
import app.pocketta.data.Repo
import app.pocketta.notify.Notifier
import app.pocketta.work.DailyWorker
import app.pocketta.work.UpdateWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PockettaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val repo = Repo.get(this@PockettaApp)
            repo.ensureSeeded()
            val p = repo.prefs()
            DailyWorker.schedule(this@PockettaApp, p.remindHour)
            UpdateWorker.schedule(this@PockettaApp)
            if (p.onboarded) repo.ensureSalary()
            runCatching { app.pocketta.data.Online.refreshRates(repo) }
            runCatching { app.pocketta.data.Online.checkUpdate(repo) }
            if (p.onboarded) runCatching { repo.autoBackupIfDue() }
        }
    }
}
