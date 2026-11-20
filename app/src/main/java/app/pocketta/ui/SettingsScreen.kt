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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import app.pocketta.BuildConfig
import app.pocketta.data.Online
import app.pocketta.i18n.Tr
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
    var goalName by remember { mutableStateOf(prefs.goalName) }
    var goalAmount by remember { mutableStateOf(numText(prefs.goalAmount)) }
    var goalSaved by remember { mutableStateOf(numText(prefs.goalSaved)) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) scope.launch {
            val ok = runCatching { repo.exportTo(uri) }.isSuccess
            if (ok) repo.markBackedUp()
            Toast.makeText(context, Tr.t(if (ok) "Backup saved" else "Couldn't save the backup", prefs.lang), Toast.LENGTH_SHORT).show()
        }
    }
    val webLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = runCatching { repo.exportJsonTo(uri) }.isSuccess
            Toast.makeText(context, Tr.t(if (ok) "Backup saved" else "Couldn't save the backup", prefs.lang), Toast.LENGTH_SHORT).show()
        }
    }
    val autoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            repo.set("backupUri", uri.toString())
            if (prefs.autoBackup == "OFF") repo.set("autoBackup", "DAILY")
            val ok = repo.autoBackupIfDue(force = true)
            Toast.makeText(context, Tr.t(if (ok) "Backup saved. It will update by itself." else "Couldn't save the backup", prefs.lang), Toast.LENGTH_SHORT).show()
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
                TextButton(onClick = { nav.open(Route.Notifications) }) { Text("Notifications: switch each one on or off") }
            }
            SectionCard {
                Label("Look")
                Gap(4)
                Text("Language", style = MaterialTheme.typography.labelLarge)
                Choices(listOf("EN", "AR"), prefs.lang, { if (it == "AR") "العربية" else "English" }, { v ->
                    scope.launch { repo.set("lang", v) }
                })
            }
            SectionCard {
                Label("Add screen")
                Text("Show more boxes when you add something, so you can see where money goes and where it comes from.", style = MaterialTheme.typography.bodySmall)
                Toggle("Need or want?", prefs.addNeed) { v -> scope.launch { repo.set("addNeed", v) } }
                Toggle("Paid how? (cash, card, wallet)", prefs.addHow) { v -> scope.launch { repo.set("addHow", v) } }
                Toggle("Where, or who it came from", prefs.addWhere) { v -> scope.launch { repo.set("addWhere", v) } }
            }
            SectionCard {
                Label("Savings goal")
                Text("Set a goal (Umra, car, wedding...) and watch the ring fill up.", style = MaterialTheme.typography.bodySmall)
                Gap(8)
                TextInput("Goal name", goalName, { goalName = it })
                Gap(8)
                NumberField("Goal amount", goalAmount, { goalAmount = it }, suffix = prefs.baseCurrency)
                Gap(8)
                NumberField("Saved so far", goalSaved, { goalSaved = it }, suffix = prefs.baseCurrency)
                TextButton(onClick = {
                    scope.launch {
                        repo.set("goalName", goalName.trim())
                        repo.set("goalAmount", parseNumber(goalAmount) ?: 0.0)
                        repo.set("goalSaved", parseNumber(goalSaved) ?: 0.0)
                    }
                }) { Text("Save") }
            }
            SectionCard {
                Label("Privacy")
                Toggle("Lock with fingerprint", prefs.lock) { v ->
                    authenticate(if (v) "Turn on the lock" else "Turn off the lock") { scope.launch { repo.set("lock", v) } }
                }
            }
            SyncCard(repo)
            SectionCard {
                Label("Backup")
                Text(
                    "Everything is on this phone only. Pick a backup file once (Google Drive works) and Pocketta keeps it up to date by itself.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Gap(8)
                Text(
                    if (prefs.lastBackup > 0) "Last backup: " + java.time.Instant.ofEpochMilli(prefs.lastBackup).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime().format(backupFmt)
                    else "No backup yet",
                    fontWeight = FontWeight.SemiBold,
                    color = if (prefs.lastBackup == 0L || System.currentTimeMillis() - prefs.lastBackup > 7 * 86_400_000L) MaterialTheme.colorScheme.error else Good,
                )
                Gap(8)
                Text("Automatic backup", style = MaterialTheme.typography.labelLarge)
                Choices(listOf("OFF", "DAILY", "WEEKLY"), prefs.autoBackup, { when (it) { "DAILY" -> "Every day"; "WEEKLY" -> "Every week"; else -> "Off" } }, { v ->
                    scope.launch { repo.set("autoBackup", v) }
                    if (v != "OFF" && prefs.backupUri.isEmpty()) autoLauncher.launch("pocketta-backup.db")
                })
                if (prefs.backupUri.isNotEmpty()) {
                    Text("Saving to: " + (Uri.parse(prefs.backupUri).lastPathSegment ?: "your file"), style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { autoLauncher.launch("pocketta-backup.db") }) { Text(if (prefs.backupUri.isEmpty()) "Choose backup file" else "Change file") }
                    if (prefs.backupUri.isNotEmpty()) {
                        OutlinedButton(onClick = {
                            scope.launch {
                                val ok = repo.autoBackupIfDue(force = true)
                                Toast.makeText(context, Tr.t(if (ok) "Backup saved" else "Couldn't save. Choose the file again.", prefs.lang), Toast.LENGTH_SHORT).show()
                            }
                        }) { Text("Back up now") }
                    }
                }
                Toggle("Remind me when there's no recent backup", prefs.backupReminder) { v -> scope.launch { repo.set("backupReminder", v) } }
                Gap(4)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { exportLauncher.launch("pocketta-backup-${LocalDate.now()}.db") }) { Text("Save a copy") }
                    TextButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) { Text("Restore a backup") }
                }
                Text("To move data to or from the web app, use the web file (.json). Restore accepts both kinds of file.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { webLauncher.launch("pocketta-web-backup-${LocalDate.now()}.json") }) { Text("Save a copy for the web app") }
            }
            SectionCard {
                Label("Updates")
                Text("Pocketta ${BuildConfig.VERSION_NAME}")
                UpdateBox(repo)
            }
            PrivacyCard(prefs.lang)
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
                        Toast.makeText(context, Tr.t("That file isn't a Pocketta backup", prefs.lang), Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismiss = { confirmRestore = null },
        )
    }
}

private val backupFmt = java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a", java.util.Locale.US)

/** Checks for a new version, then downloads it and opens the installer. */
@Composable
fun UpdateBox(repo: Repo) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var status by remember { mutableStateOf("") }
    var release by remember { mutableStateOf<Online.Release?>(null) }
    var progress by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(Unit) { release = Online.checkUpdate(repo) }
    val r = release
    if (r != null) {
        Text("Version ${r.version} is ready", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        val p = progress
        if (p != null) {
            Progress(p)
            Text("Downloading ${(p * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
        } else {
            Button(onClick = {
                if (Online.canInstall(context)) {
                    progress = 0f
                    scope.launch {
                        val ok = Online.downloadAndInstall(context, r) { progress = it }
                        progress = null
                        if (!ok) status = "Download failed. Try again."
                    }
                } else {
                    status = "Allow Pocketta to install updates, then tap Update again."
                }
            }) { Text("Update now") }
        }
    } else {
        TextButton(onClick = {
            status = "Checking..."
            scope.launch {
                release = Online.checkUpdate(repo, force = true)
                status = if (release == null) "You have the latest version." else ""
            }
        }) { Text("Check for updates") }
    }
    if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun Toggle(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Settings card: opens the privacy policy in the app language only. */
@Composable
private fun PrivacyCard(lang: String) {
    var open by remember { mutableStateOf(false) }
    SectionCard {
        Label("Privacy")
        TextButton(onClick = { open = true }) { Text("Privacy policy") }
    }
    if (open) {
        val doc = if (lang == "AR") PrivacyText.AR else PrivacyText.EN
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(doc.title) },
            text = {
                androidx.compose.foundation.lazy.LazyColumn {
                    item { Text(doc.updated, style = MaterialTheme.typography.bodySmall) }
                    items(doc.blocks.size) { i ->
                        val (kind, t) = doc.blocks[i]
                        when (kind) {
                            "h" -> Text(t, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
                            "l" -> Text("• " + t, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                            else -> Text(t, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } },
        )
    }
}
