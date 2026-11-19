package app.pocketta.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Checkbox
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import android.widget.Toast
import app.pocketta.data.CloudSync
import app.pocketta.data.Repo
import app.pocketta.data.SyncSide
import app.pocketta.data.UnlockAns
import kotlinx.coroutines.launch

private val syncFmt = java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a", java.util.Locale.US)
private fun fmt(t: Long) = if (t <= 0) "" else java.time.Instant.ofEpochMilli(t).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime().format(syncFmt)

private fun ago(t: Long): String {
    val m = (System.currentTimeMillis() - t) / 60000
    return when {
        t <= 0 -> "not yet"
        m < 1 -> "just now"
        m < 60 -> "$m min ago"
        m < 1440 -> "${m / 60} h ago"
        else -> fmt(t)
    }
}

/** Settings card: sign in, status, sync now, sign out, delete my cloud data. */
@Composable
fun SyncCard(repo: Repo) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by CloudSync.ui.collectAsState()
    var confirmOut by remember { mutableStateOf(false) }
    var confirmDel by remember { mutableStateOf(false) }
    var encDialog by remember { mutableStateOf("") }
    var newKey by remember { mutableStateOf<String?>(null) }
    SectionCard {
        Label("Sync and backup")
        if (!s.signedIn) {
            Text("Optional. Pocketta works exactly the same without an account. Sign in to keep your phone and laptop in sync and to have an automatic backup.", style = MaterialTheme.typography.bodySmall)
            Gap(8)
            Button(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, CloudSync.signInUri())) } }) { Text("Sign in with Google") }
            if (s.error.isNotEmpty()) Text(s.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        } else {
            val line = when {
                s.busy -> "Syncing..."
                s.error == "offline" -> "No internet. It will sync when you are back online."
                s.error.isNotEmpty() -> s.error
                s.status == "paused" -> "Paused. Tap Sync now to choose."
                else -> "Sync is on"
            }
            Text(line, fontWeight = FontWeight.SemiBold)
            Text(s.email, style = MaterialTheme.typography.bodySmall)
            Text("Last sync: " + ago(s.lastSyncAt), style = MaterialTheme.typography.bodySmall)
            Gap(8)
            Row {
                Button(onClick = { scope.launch { CloudSync.syncNow(repo) } }) { Text("Sync now") }
                OutlinedButton(onClick = { confirmOut = true }, modifier = Modifier.padding(start = 8.dp)) { Text("Sign out") }
            }
            Text("If you sign out, your data stays on this device and sync stops.", style = MaterialTheme.typography.bodySmall)
            if (s.encrypted) {
                Gap(4)
                Text("Encryption", fontWeight = FontWeight.SemiBold)
                OutlinedButton(onClick = { encDialog = "key" }) { Text("New encryption key") }
            }
            TextButton(onClick = { confirmDel = true }) { Text("Delete my data from the cloud", color = MaterialTheme.colorScheme.error) }
        }
        Gap(8)
        Text("🔒 Your money data is encrypted on your phone before it is saved. Only your encryption key can open it, not even the developer can read it.", style = MaterialTheme.typography.bodySmall)
        if (s.signedIn && s.encrypted) Text("Encrypted on this device", style = MaterialTheme.typography.bodySmall)
    }
    if (encDialog.isNotEmpty()) EncryptionDialog(onDone = { rec -> encDialog = ""; if (rec != null) newKey = rec; scope.launch { CloudSync.syncNow(repo) } }, onCancel = { encDialog = "" })
    newKey?.let { NewKeyDialog(it) { newKey = null } }
    if (confirmOut) ConfirmDialog(
        "Sign out?", "Your data stays on this device. Sync stops until you sign in again.", "Sign out",
        onConfirm = { confirmOut = false; CloudSync.signOut() }, onDismiss = { confirmOut = false },
    )
    if (confirmDel) ConfirmDialog(
        "Delete your data from the cloud?", "This removes the copy in the cloud and its safety copy. Your data on this device is not touched.", "Delete from cloud",
        onConfirm = {
            confirmDel = false
            scope.launch {
                val ok = runCatching { CloudSync.deleteCloud() }.isSuccess
                Toast.makeText(context, if (ok) "Deleted from the cloud" else "Could not delete. Check your internet.", Toast.LENGTH_SHORT).show()
            }
        },
        onDismiss = { confirmDel = false },
    )
}

@Composable
private fun SideText(x: SyncSide, extra: String) {
    Text("${x.records} records · ${x.commitments} commitments", style = MaterialTheme.typography.bodySmall)
    if (extra.isNotEmpty()) Text(extra, style = MaterialTheme.typography.bodySmall)
}

/** Asked only when needed: first upload, or two different copies of the same account. Nothing is merged. */
@Composable
fun SyncDialog() {
    val ch by CloudSync.choice.collectAsState()
    val c = ch ?: return
    var pick by remember(c) { mutableStateOf("local") }
    AlertDialog(
        onDismissRequest = { c.answer.complete(null) },
        title = { Text(if (c.firstUpload) "First sync" else "Your data is different") },
        text = {
            Column {
                if (c.firstUpload || c.cloud == null) {
                    Text("Your data is on this device and nothing is in the cloud yet.")
                    SideText(c.local, "")
                    Text("It is uploaded to your own private space.", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("This account already has data in the cloud, and this device has different data. Pick the one you want to keep. Nothing is merged.")
                    Row(Modifier.fillMaxWidth().clickable { pick = "local" }.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = pick == "local", onClick = { pick = "local" })
                        Column {
                            Text("Keep this device", fontWeight = FontWeight.SemiBold)
                            SideText(c.local, if (c.local.at > 0) "Changed " + fmt(c.local.at) else "")
                            Text("Replaces what is in the cloud", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Row(Modifier.fillMaxWidth().clickable { pick = "cloud" }.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = pick == "cloud", onClick = { pick = "cloud" })
                        Column {
                            Text("Keep the cloud copy", fontWeight = FontWeight.SemiBold)
                            SideText(c.cloud, "Saved " + fmt(c.cloud.at) + (if (c.cloud.device.isNotEmpty()) " from " + c.cloud.device else ""))
                            Text("Replaces what is on this device", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text("Before anything is replaced, a safety copy of that side is kept.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = { c.answer.complete(if (c.firstUpload || c.cloud == null) "local" else pick) }) { Text(if (c.firstUpload || c.cloud == null) "Upload my data" else "Continue") } },
        dismissButton = { TextButton(onClick = { c.answer.complete(null) }) { Text("Cancel") } },
    )
}

private const val KEY_FILE_TEXT = "Pocketta encryption key\n\n%s\n\nKeep this file somewhere safe (not only on this phone). A new device asks for this key once, to open your cloud backup. Anyone who has it and your Google account can open your backup. If you lose it, nobody can open your cloud backup.\n"

/** Copy and download buttons for a key. [onSaved] is called after the key was copied or saved to a file. */
@Composable
private fun KeyButtons(key: String, onSaved: () -> Unit) {
    val clip = LocalClipboardManager.current
    val ctx = LocalContext.current
    val saveKey = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            val ok = runCatching {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(KEY_FILE_TEXT.format(key).toByteArray(Charsets.UTF_8)) } != null
            }.getOrDefault(false)
            if (ok) onSaved()
            Toast.makeText(ctx, if (ok) "Saved" else "Could not save the file", Toast.LENGTH_SHORT).show()
        }
    }
    Row {
        TextButton(onClick = { clip.setText(AnnotatedString(key)); onSaved() }) { Text("Copy key") }
        TextButton(onClick = { saveKey.launch("pocketta-encryption-key.txt") }) { Text("Download as a file") }
    }
}

/** First time only: the app made the encryption key. Copy it or save it as a file, then continue. */
@Composable
fun SyncSetupDialog() {
    val r by CloudSync.setup.collectAsState()
    val q = r ?: return
    var saved by remember(q) { mutableStateOf(false) }
    var err by remember(q) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { q.answer.complete(null) },
        title = { Text("Lock your data") },
        text = {
            Column {
                Text("Your data is locked on your phone before it goes to the cloud. The app made your encryption key for you. Nobody can read your data without it, not even us.", style = MaterialTheme.typography.bodySmall)
                Text(q.recovery, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                KeyButtons(q.recovery) { saved = true; err = "" }
                Text("Copy it or download it as a file, and keep it somewhere safe. A new device needs it once. If you lose it, your cloud copy cannot be opened. Your data on this device stays safe.", style = MaterialTheme.typography.bodySmall)
                if (err.isNotEmpty()) Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = { if (saved) q.answer.complete(q.recovery) else err = "Copy or download the key first." }) { Text("Lock and continue") } },
        dismissButton = { TextButton(onClick = { q.answer.complete(null) }) { Text("Cancel") } },
    )
}

private val KEY_RE = Regex("[A-Za-z0-9]{5}(?:-[A-Za-z0-9]{5}){3}")

/** This account's cloud data is encrypted and this phone does not have the key yet: paste it or pick the key file. */
@Composable
fun SyncUnlockDialog() {
    val r by CloudSync.unlock.collectAsState()
    val q = r ?: return
    val ctx = LocalContext.current
    var key by remember(q) { mutableStateOf("") }
    var wipe by remember(q) { mutableStateOf(false) }
    var msg by remember(q) { mutableStateOf("") }
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull() ?: ""
            val m = KEY_RE.find(text)
            if (m != null) { key = m.value; msg = "" } else msg = "No key found in that file"
        }
    }
    AlertDialog(
        onDismissRequest = { q.answer.complete(null) },
        title = { Text(if (wipe) "Delete the cloud copy?" else "Open your data") },
        text = {
            Column {
                if (!wipe) {
                    Text("This account's data is locked. Paste your encryption key, or choose the key file you downloaded. If you set a password in an older version, type that instead. You will not be asked again on this phone.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(key, { key = it; msg = "" }, label = { Text("Encryption key") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    TextButton(onClick = { pick.launch(arrayOf("text/plain", "*/*")) }) { Text("Choose the key file") }
                    if (q.bad) Text("That did not work. Try again.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    if (msg.isNotEmpty()) Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { wipe = true }) { Text("I lost the key") }
                } else {
                    Text("Without the key the cloud copy cannot be opened. It is deleted for good. The data on this phone stays and is uploaded again with a new key.", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            if (!wipe) TextButton(onClick = { if (key.isBlank()) msg = "Paste your key or choose the file" else q.answer.complete(UnlockAns("auto", key.trim())) }) { Text("Open") }
            else TextButton(onClick = { q.answer.complete(UnlockAns("auto", wipe = true)) }) { Text("Delete the cloud copy", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = { q.answer.complete(null) }) { Text("Cancel") } },
    )
}

/** Settings > Encryption: the app makes a new encryption key. Asks for the current key first. */
@Composable
private fun EncryptionDialog(onDone: (String?) -> Unit, onCancel: () -> Unit) {
    val scope = rememberCoroutineScope()
    var cur by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onCancel() },
        title = { Text("New encryption key") },
        text = {
            Column {
                Text("The app makes a new encryption key for you. The old key stops working for new devices. Type your current key to continue.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(cur, { cur = it; err = "" }, label = { Text("Current encryption key") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                if (err.isNotEmpty()) Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                if (cur.isBlank()) { err = "Type your current key"; return@TextButton }
                busy = true
                scope.launch {
                    val rec = CloudSync.newRecoveryKey(cur.trim())
                    if (rec == null) { err = "That key does not work"; busy = false } else onDone(rec)
                }
            }) { Text("Make a new key") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onCancel) { Text("Cancel") } },
    )
}

/** Shows the new key once, with copy and download. */
@Composable
private fun NewKeyDialog(key: String, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Your new encryption key") },
        text = {
            Column {
                Text(key, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                KeyButtons(key) {}
                Text("Save it now. It is shown only once. The old key no longer works for new devices.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Done") } },
    )
}
