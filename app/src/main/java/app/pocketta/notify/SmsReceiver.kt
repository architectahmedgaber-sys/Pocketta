package app.pocketta.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import app.pocketta.core.SmsParser
import app.pocketta.data.Repo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Reads incoming text messages on the phone only (nothing leaves it) and, when one looks like spending,
 * offers to add it as an expense. Nothing is saved until you tap. Off unless you turn it on and allow it.
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val body = parts.joinToString("") { it.messageBody.orEmpty() }
        if (body.isBlank()) return
        val job = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = Repo.get(context)
                val p = repo.prefs()
                if (!p.onboarded || !p.smsSuggest) return@launch
                val e = SmsParser.parse(body) ?: return@launch
                val key = body.hashCode()
                val seen = p.smsSeen.split(',').filter { it.isNotEmpty() }
                if (key.toString() in seen) return@launch
                repo.set("smsSeen", (seen + key.toString()).takeLast(20).joinToString(","))
                // A "ready to add" card on Today (saved only when tapped) and the same offer as a notification.
                repo.addSmsCard(app.pocketta.core.SmsCard(key, e.amount, e.currency, e.merchant, java.time.LocalDate.now().toEpochDay()))
                Notifier.smsExpense(context, key, e.amount, e.currency, e.merchant, p.lang)
            } finally {
                job.finish()
            }
        }
    }
}
