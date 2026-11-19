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
                Row {
                    OutlinedButton(onClick = { encDialog = "key" }) { Text("New recovery key") }
                    OutlinedButton(onClick = { encDialog = "pass" }, modifier = Modifier.padding(start = 8.dp)) { Text("Change password") }
                }
            }
            TextButton(onClick = { confirmDel = true }) { Text("Delete my data from the cloud", color = MaterialTheme.colorScheme.error) }
        }
        Gap(8)
        Text("🔒 Your money data is encrypted on your phone before it is saved. Only your encryption password can open it, not even the developer can read it.", style = MaterialTheme.typography.bodySmall)
        if (s.signedIn && s.encrypted) Text("Encrypted on this device", style = MaterialTheme.typography.bodySmall)
    }
    if (encDialog.isNotEmpty()) EncryptionDialog(encDialog, onDone = { rec -> encDialog = ""; if (rec != null) newKey = rec; scope.launch { CloudSync.syncNow(repo) } }, onCancel = { encDialog = "" })
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

/** First time only: choose the encryption password and save the recovery key. */
@Composable
fun SyncSetupDialog() {
    val r by CloudSync.setup.collectAsState()
    val q = r ?: return
    val clip = LocalClipboardManager.current
    val ctx = LocalContext.current
    val saveKey = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            val ok = runCatching {
                ctx.contentResolver.openOutputStream(uri)?.use {
                    it.write(("Pocketta recovery key\n\n" + q.recovery + "\n\nKeep this file somewhere safe (not only on this phone). With it you can open your cloud backup if you forget your encryption password. Anyone who has it and your Google account can open your backup.\n").toByteArray(Charsets.UTF_8))
                } != null
            }.getOrDefault(false)
            Toast.makeText(ctx, if (ok) "Saved" else "Could not save the file", Toast.LENGTH_SHORT).show()
        }
    }
    var p1 by remember(q) { mutableStateOf("") }
    var p2 by remember(q) { mutableStateOf("") }
    var saved by remember(q) { mutableStateOf(false) }
    var err by remember(q) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { q.answer.complete(null) },
        title = { Text("Protect your data") },
        text = {
            Column {
                Text("Your data is locked on your phone before it goes to the cloud. Choose an encryption password. You will type it once on each new device.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(p1, { p1 = it; err = "" }, label = { Text("Encryption password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                OutlinedTextField(p2, { p2 = it; err = "" }, label = { Text("Repeat the password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                Text("Recovery key. Save it somewhere safe (a password manager or on paper):", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                Text(q.recovery, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Row {
                    TextButton(onClick = { clip.setText(AnnotatedString(q.recovery)) }) { Text("Copy recovery key") }
                    TextButton(onClick = { saveKey.launch("pocketta-recovery-key.txt") }) { Text("Download as a file") }
                }
                Row(Modifier.fillMaxWidth().clickable { saved = !saved }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = saved, onCheckedChange = { saved = it })
                    Text("I saved the recovery key", style = MaterialTheme.typography.bodySmall)
                }
                Text("If you forget the password and lose the recovery key, nobody can open your cloud data, not even us. Your data on this device stays safe.", style = MaterialTheme.typography.bodySmall)
                if (err.isNotEmpty()) Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    p1.length < 8 -> err = "Use at least 8 characters."
                    p1 != p2 -> err = "The passwords do not match."
                    !saved -> err = "Please confirm you saved the recovery key."
                    else -> q.answer.complete(p1)
                }
            }) { Text("Turn on encryption") }
        },
        dismissButton = { TextButton(onClick = { q.answer.complete(null) }) { Text("Cancel") } },
    )
}

/** This account's cloud data is encrypted and this phone does not have the key yet. */
@Composable
fun SyncUnlockDialog() {
    val r by CloudSync.unlock.collectAsState()
    val q = r ?: return
    var mode by remember(q) { mutableStateOf("pass") }
    var secret by remember(q) { mutableStateOf("") }
    var np1 by remember(q) { mutableStateOf("") }
    var np2 by remember(q) { mutableStateOf("") }
    var err by remember(q) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { q.answer.complete(null) },
        title = { Text(when (mode) { "pass" -> "Enter your encryption password"; "rec" -> "Use your recovery key"; else -> "Start again without the old data" }) },
        text = {
            Column {
                if (mode == "pass") {
                    Text("Your cloud data is encrypted. Type the password you chose when you turned on sync.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(secret, { secret = it; err = "" }, label = { Text("Encryption password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    if (q.bad) Text("Wrong password. Try again.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { mode = "rec"; secret = ""; err = "" }) { Text("I forgot the password") }
                } else if (mode == "rec") {
                    Text("Type your recovery key and choose a new password.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(secret, { secret = it; err = "" }, label = { Text("Recovery key") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    OutlinedTextField(np1, { np1 = it; err = "" }, label = { Text("New password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                    OutlinedTextField(np2, { np2 = it; err = "" }, label = { Text("Repeat the password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                    if (q.bad) Text("That recovery key does not work. Try again.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    if (err.isNotEmpty()) Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { mode = "wipe"; err = "" }) { Text("I lost the recovery key too") }
                } else {
                    Text("Without the password or the recovery key the cloud copy cannot be opened. You can delete it and start fresh. Your data on this device is not touched.", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            when (mode) {
                "pass" -> TextButton(onClick = { if (secret.isNotEmpty()) q.answer.complete(UnlockAns("pass", secret)) }) { Text("Unlock") }
                "rec" -> TextButton(onClick = {
                    when {
                        secret.isBlank() -> err = "Type the recovery key."
                        np1.length < 8 -> err = "Use at least 8 characters."
                        np1 != np2 -> err = "The passwords do not match."
                        else -> q.answer.complete(UnlockAns("rec", secret, np1))
                    }
                }) { Text("Unlock") }
                else -> TextButton(onClick = { q.answer.complete(UnlockAns("pass", wipe = true)) }) { Text("Delete the cloud copy", color = MaterialTheme.colorScheme.error) }
            }
        },
        dismissButton = { TextButton(onClick = { q.answer.complete(null) }) { Text("Cancel") } },
    )
}

/** Settings > Encryption: make a new recovery key, or change the encryption password. Asks for the current password first. */
@Composable
private fun EncryptionDialog(kind: String, onDone: (String?) -> Unit, onCancel: () -> Unit) {
    val scope = rememberCoroutineScope()
    var cur by remember { mutableStateOf("") }
    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onCancel() },
        title = { Text(if (kind == "key") "New recovery key" else "Change encryption password") },
        text = {
            Column {
                if (kind == "key") Text("The app makes a new recovery key for you. The old one stops working. Type your encryption password to continue.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(cur, { cur = it; err = "" }, label = { Text(if (kind == "key") "Encryption password" else "Current password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                if (kind == "pass") {
                    OutlinedTextField(p1, { p1 = it; err = "" }, label = { Text("New password (8 or more)") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                    OutlinedTextField(p2, { p2 = it; err = "" }, label = { Text("Type it again") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                    Text("Your other devices keep working. A new device will ask for the new password.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                }
                if (err.isNotEmpty()) Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                when {
                    cur.isEmpty() -> err = "Type your current password"
                    kind == "pass" && p1.length < 8 -> err = "Use at least 8 characters"
                    kind == "pass" && p1 != p2 -> err = "The passwords do not match."
                    else -> {
                        busy = true
                        scope.launch {
                            if (kind == "key") {
                                val rec = CloudSync.newRecoveryKey(cur)
                                if (rec == null) { err = "Wrong password"; busy = false } else onDone(rec)
                            } else {
                                if (CloudSync.changePassword(cur, p1)) onDone(null) else { err = "Wrong password"; busy = false }
                            }
                        }
                    }
                }
            }) { Text(if (kind == "key") "Make a new key" else "Change password") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onCancel) { Text("Cancel") } },
    )
}

/** Shows the new recovery key once, with copy and download. */
@Composable
private fun NewKeyDialog(key: String, onClose: () -> Unit) {
    val clip = LocalClipboardManager.current
    val ctx = LocalContext.current
    val saveKey = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            val ok = runCatching {
                ctx.contentResolver.openOutputStream(uri)?.use {
                    it.write(("Pocketta recovery key\n\n" + key + "\n\nKeep this file somewhere safe (not only on this phone). With it you can open your cloud backup if you forget your encryption password. Anyone who has it and your Google account can open your backup.\n").toByteArray(Charsets.UTF_8))
                } != null
            }.getOrDefault(false)
            Toast.makeText(ctx, if (ok) "Saved" else "Could not save the file", Toast.LENGTH_SHORT).show()
        }
    }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Your new recovery key") },
        text = {
            Column {
                Text(key, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Row {
                    TextButton(onClick = { clip.setText(AnnotatedString(key)) }) { Text("Copy recovery key") }
                    TextButton(onClick = { saveKey.launch("pocketta-recovery-key.txt") }) { Text("Download as a file") }
                }
                Text("Save it now. It is shown only once. The old recovery key no longer works.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Done") } },
    )
}
