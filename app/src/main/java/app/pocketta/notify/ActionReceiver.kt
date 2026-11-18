package app.pocketta.notify

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import app.pocketta.data.Repo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** "Mark paid" and "Skip" buttons on reminders, handled without opening the app. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_GOAL) {
            val amount = intent.getDoubleExtra(EXTRA_AMOUNT, 0.0)
            val notifId = intent.getIntExtra(EXTRA_NOTIF, 0)
            val job = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val repo = Repo.get(context)
                    val p = repo.prefs()
                    if (amount > 0 && p.goalAmount > 0) repo.set("goalSaved", p.goalSaved + amount)
                    NotificationManagerCompat.from(context).cancel(notifId)
                } finally {
                    job.finish()
                }
            }
            return
        }
        val kind = intent.getStringExtra(EXTRA_KIND) ?: return
        val id = intent.getLongExtra(EXTRA_ID, 0)
        val notif = intent.getIntExtra(EXTRA_NOTIF, 0)
        val action = intent.action
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = Repo.get(context)
                val dao = repo.dao
                when (kind) {
                    "BILL" -> dao.bill(id)?.let { if (action == ACTION_SKIP) repo.skipBill(it) else repo.payBill(it) }
                    "INSTALLMENT" -> dao.installment(id)?.let { repo.payInstallment(it) }
                    "CIRCLE" -> dao.circle(id)?.let { repo.payCircle(it) }
                    "LOAN" -> dao.loan(id)?.let { repo.loanPayment(it, it.remaining) }
                }
                NotificationManagerCompat.from(context).cancel(notif)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_PAID = "app.pocketta.PAID"
        const val ACTION_SKIP = "app.pocketta.SKIP"
        const val ACTION_GOAL = "app.pocketta.GOAL"
        private const val EXTRA_AMOUNT = "amount"
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_ID = "id"
        private const val EXTRA_NOTIF = "notif"

        fun goalPending(context: Context, amount: Double, notif: Int): PendingIntent {
            val i = Intent(context, ActionReceiver::class.java).setAction(ACTION_GOAL)
                .putExtra(EXTRA_AMOUNT, amount).putExtra(EXTRA_NOTIF, notif)
            return PendingIntent.getBroadcast(context, 900 + notif, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        fun pending(context: Context, action: String, kind: String, id: Long, notif: Int): PendingIntent {
            val i = Intent(context, ActionReceiver::class.java).setAction(action)
                .putExtra(EXTRA_KIND, kind).putExtra(EXTRA_ID, id).putExtra(EXTRA_NOTIF, notif)
            return PendingIntent.getBroadcast(
                context, notif * 2 + if (action == ACTION_SKIP) 1 else 0, i,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }
}
