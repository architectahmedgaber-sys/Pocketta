package app.pocketta.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.pocketta.BuildConfig
import app.pocketta.data.Online
import app.pocketta.data.Prefs
import kotlinx.coroutines.launch

/** When the app opens and a newer version is out, say so and offer to download and install it. */
@Composable
fun UpdateDialog(prefs: Prefs) {
    val ready = prefs.updateVersion.isNotEmpty() && prefs.updateUrl.isNotEmpty() && Online.isNewer(prefs.updateVersion, BuildConfig.VERSION_NAME)
    var dismissed by rememberSaveable { mutableStateOf("") }
    var progress by remember { mutableStateOf<Float?>(null) }
    var status by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    if (!ready || dismissed == prefs.updateVersion) return
    AlertDialog(
        onDismissRequest = { if (progress == null) dismissed = prefs.updateVersion },
        title = { Text("Pocketta ${prefs.updateVersion} is ready") },
        text = {
            Column {
                Text("A new version is available. Your data stays as it is.")
                val p = progress
                if (p != null) {
                    Gap(10)
                    Progress(p)
                    Text("Downloading ${(p * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                }
                if (status.isNotEmpty()) {
                    Gap(8)
                    Text(status, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = progress == null, onClick = {
                if (Online.canInstall(context)) {
                    progress = 0f
                    status = ""
                    scope.launch {
                        val ok = Online.downloadAndInstall(context, Online.Release(prefs.updateVersion, prefs.updateUrl)) { progress = it }
                        progress = null
                        if (!ok) status = "Download failed. Try again from Settings."
                    }
                } else {
                    status = "Allow Pocketta to install updates, then tap Update again."
                }
            }) { Text("Update now") }
        },
        dismissButton = { TextButton(enabled = progress == null, onClick = { dismissed = prefs.updateVersion }) { Text("Later") } },
    )
}
