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
            TextButton(onClick = { confirmDel = true }) { Text("Delete my data from the cloud", color = MaterialTheme.colorScheme.error) }
        }
        Gap(8)
        Text("🔒 Your money data is private. It is saved only on your own account and nobody else can open it.", style = MaterialTheme.typography.bodySmall)
    }
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
