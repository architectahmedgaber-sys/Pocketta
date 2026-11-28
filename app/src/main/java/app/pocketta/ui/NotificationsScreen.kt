package app.pocketta.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pocketta.data.Prefs
import app.pocketta.data.Repo
import app.pocketta.notify.Notifier
import kotlinx.coroutines.launch

/** Every notification the app can send, each with its own switch, and a button that proves they reach this phone. */
@Composable
fun NotificationsScreen(repo: Repo, nav: Nav, prefs: Prefs) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var allowed by remember { mutableStateOf(Notifier.canPost(context)) }
    var sentTest by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> allowed = granted || Notifier.canPost(context) }
    val askSms = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> scope.launch { repo.set("smsSuggest", granted) } }

    fun openAppNotificationSettings() {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    Scaffold(topBar = { BackBar("Notifications", { nav.back() }) }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(container = if (allowed) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.errorContainer) {
                if (allowed) {
                    Text("Notifications are allowed on this phone", color = Good, fontWeight = FontWeight.Medium)
                } else {
                    Text("Notifications are blocked, so nothing below can reach you", fontWeight = FontWeight.Medium)
                    Gap(6)
                    Button(onClick = {
                        if (Build.VERSION.SDK_INT >= 33) ask.launch(Manifest.permission.POST_NOTIFICATIONS) else openAppNotificationSettings()
                    }) { Text("Allow notifications") }
                    OutlinedButton(onClick = { openAppNotificationSettings() }) { Text("Open the phone's notification settings") }
                }
                Gap(8)
                Button(onClick = {
                    Notifier.test(context, prefs.lang)
                    sentTest = true
                }, enabled = allowed) { Text("Send test notification") }
                if (sentTest && allowed) {
                    Gap(4)
                    Text("Sent. Pull down the top of your screen to see it.", style = MaterialTheme.typography.bodySmall)
                }
            }
            SectionCard {
                Label("Choose what you get")
                Gap(4)
                SwitchRow("Bills, installments and loans due", "The days before the due date that you chose on each one", prefs.notifyDue) { v -> scope.launch { repo.set("notifyDue", v) } }
                SwitchRow("Gam'eya", "Your installments, and the day before and on your turn", prefs.notifyGameya) { v -> scope.launch { repo.set("notifyGameya", v) } }
                SwitchRow("Going over today's budget", "Once a day, right when you pass it", prefs.budgetAlert) { v -> scope.launch { repo.set("budgetAlert", v) } }
                SwitchRow("Morning message", "Today's number, the dollar and gold, and your week on Friday", prefs.morning) { v -> scope.launch { repo.set("morning", v) } }
                SwitchRow("End of the month", "Offers to put what's left in your piggy bank", prefs.monthEndReminder) { v -> scope.launch { repo.set("monthEndReminder", v) } }
                SwitchRow("Category over its limit", "Once a month per category, only after you pass the limit you set", prefs.limitAlert) { v -> scope.launch { repo.set("limitAlert", v) } }
                SwitchRow("Prices are rising", "When a category runs 20% above your usual", prefs.notifyRise) { v -> scope.launch { repo.set("notifyRise", v) } }
                SwitchRow("New version of Pocketta", "Checked every few hours, once per version", prefs.notifyUpdate) { v -> scope.launch { repo.set("notifyUpdate", v) } }
                SwitchRow("No recent backup", "Once a week while you have no recent backup", prefs.backupReminder) { v -> scope.launch { repo.set("backupReminder", v) } }
            }
            SectionCard {
                Label("Bank messages")
                Gap(4)
                SwitchRow("Suggest expenses from bank messages", "When a bank text looks like spending, a notification offers to add it. Read on this phone only, never sent anywhere. You confirm every one.", prefs.smsSuggest) { v ->
                    if (!v) scope.launch { repo.set("smsSuggest", false) }
                    else if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED) scope.launch { repo.set("smsSuggest", true) }
                    else askSms.launch(Manifest.permission.RECEIVE_SMS)
                }
            }
            SectionCard {
                Label("If a notification comes late")
                Gap(4)
                Text(
                    "Reminders are checked once a day at the hour you chose in Settings. Phones with strict battery saving can delay them. Let Pocketta run in the background.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }) { Text("Open battery settings") }
            }
            Gap(24)
        }
    }
}

@Composable
private fun SwitchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
