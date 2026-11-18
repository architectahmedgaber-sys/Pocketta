package app.pocketta.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.pocketta.BuildConfig
import app.pocketta.data.Prefs
import app.pocketta.data.Repo
import app.pocketta.work.DailyWorker
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun SettingsScreen(repo: Repo, nav: Nav, prefs: Prefs, authenticate: (String, () -> Unit) -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var start by remember { mutableStateOf(prefs.cycleStart.toString()) }
    var hour by remember { mutableStateOf(prefs.remindHour.toString()) }
    var confirmRestore by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) scope.launch {
            val ok = runCatching { repo.exportTo(uri) }.isSuccess
            Toast.makeText(context, if (ok) "Backup saved" else "Couldn't save the backup", Toast.LENGTH_SHORT).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> confirmRestore = uri }

    Scaffold(topBar = { BackBar("Settings", { nav.back() }) }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard {
                Label("Month")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NumberField("My month starts on day", start, { start = it }, Modifier.weight(1f), decimal = false)
                    TextButton(onClick = { scope.launch { repo.set("cycleStart", (start.toIntOrNull() ?: 1).coerceIn(1, 31)) } }) { Text("Save") }
                }
            }
            SectionCard {
                Label("Reminders")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NumberField("Daily reminder hour (0-23)", hour, { hour = it }, Modifier.weight(1f), decimal = false)
                    TextButton(onClick = {
                        val h = (hour.toIntOrNull() ?: 9).coerceIn(0, 23)
                        scope.launch { repo.set("remindHour", h) }
                        DailyWorker.schedule(context, h, replace = true)
                    }) { Text("Save") }
                }
                Text("Bills, installments and gam'eya shares remind you the days before you choose on each one.", style = MaterialTheme.typography.bodySmall)
                Toggle("Tell me when I go over today's budget", prefs.budgetAlert) { v -> scope.launch { repo.set("budgetAlert", v) } }
            }
            SectionCard {
                Label("Privacy")
                Toggle("Lock with fingerprint", prefs.lock) { v ->
                    authenticate(if (v) "Turn on the lock" else "Turn off the lock") { scope.launch { repo.set("lock", v) } }
                }
            }
            SectionCard {
                Label("Backup")
                Text("Everything is on this phone only. Save a backup file (to Drive, WhatsApp to yourself, or anywhere) and restore it on a new phone.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { exportLauncher.launch("pocketta-backup-${LocalDate.now()}.db") }) { Text("Save backup") }
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) { Text("Restore") }
                }
                Text("Android also backs the app up to your Google account by itself.", style = MaterialTheme.typography.bodySmall)
            }
            SectionCard {
                Label("About")
                Text("Pocketta ${BuildConfig.VERSION_NAME}")
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${BuildConfig.GITHUB_REPO}/releases/latest")))
                    }
                }) { Text("Check for updates") }
            }
        }
    }
    confirmRestore?.let { uri ->
        ConfirmDialog(
            "Restore this backup?",
            "Everything in the app now will be replaced by the backup. The app will restart.",
            "Restore",
            onConfirm = {
                confirmRestore = null
                scope.launch {
                    if (repo.importFrom(uri)) {
                        val i = context.packageManager.getLaunchIntentForPackage(context.packageName)
                        if (i != null) context.startActivity(Intent.makeRestartActivityTask(i.component))
                        Runtime.getRuntime().exit(0)
                    } else {
                        Toast.makeText(context, "That file isn't a Pocketta backup", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismiss = { confirmRestore = null },
        )
    }
}

@Composable
private fun Toggle(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
