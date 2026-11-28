package app.pocketta.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pocketta.ai.AiConfig
import app.pocketta.data.CloudSync
import app.pocketta.data.Prefs
import app.pocketta.data.Repo
import app.pocketta.i18n.Tr
import kotlinx.coroutines.launch
import java.time.LocalDate

/** More tab, last card: wipe everything and begin again. Two steps: what goes (with a backup button), then type a word. */
@Composable
fun ResetAllCard(repo: Repo, prefs: Prefs) {
    var step by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val ar = prefs.lang == "AR"
    val word = if (ar) "امسح" else "DELETE"
    var typed by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) scope.launch {
            val ok = runCatching { repo.exportTo(uri) }.isSuccess
            if (ok) repo.markBackedUp()
            Toast.makeText(context, Tr.t(if (ok) "Backup saved" else "Couldn't save the backup", prefs.lang), Toast.LENGTH_SHORT).show()
        }
    }

    SectionCard(container = MaterialTheme.colorScheme.errorContainer, onClick = { typed = ""; step = 1 }) {
        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(60.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) { Text("🗑️", fontSize = 28.sp) }
            Column(Modifier.weight(1f)) {
                Text("Start over", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                Text("Delete all my data and begin from zero", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            }
            Text("›", fontSize = 28.sp, color = MaterialTheme.colorScheme.outline)
        }
    }

    if (step == 1) AlertDialog(
        onDismissRequest = { step = 0 },
        title = { Text("Start over?") },
        text = {
            Column {
                Text("This deletes everything on this phone:")
                Column(Modifier.padding(vertical = 8.dp)) {
                    for (line in listOf("Spending, income and wallets", "Bills, installments, gam'eya and loans", "Goals, piggy bank and tags", "Miqrish notes")) Text("• " + line.let { Tr.t(it, prefs.lang) }, style = MaterialTheme.typography.bodyMedium)
                    if (CloudSync.signedIn()) Text("• " + Tr.t("Your cloud copy", prefs.lang), style = MaterialTheme.typography.bodyMedium)
                }
                Text("This cannot be undone.", fontWeight = FontWeight.Medium)
                Text("Your AI key and the look you chose stay.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = { backupLauncher.launch("pocketta-backup-${LocalDate.now()}.db") }, modifier = Modifier.padding(top = 12.dp)) { Text("💾 Save a backup first") }
            }
        },
        confirmButton = { TextButton(onClick = { step = 2 }) { Text("Continue", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { step = 0 }) { Text("Cancel") } },
    )

    if (step == 2) {
        val ok = typed.trim().uppercase() == "DELETE" || typed.trim() == "امسح"
        AlertDialog(
            onDismissRequest = { if (!busy) step = 0 },
            title = { Text("Last check", color = MaterialTheme.colorScheme.error) },
            text = {
                Column {
                    Text(if (ar) "اكتب امسح للتأكيد." else "Type DELETE to confirm.")
                    OutlinedTextField(value = typed, onValueChange = { typed = it }, singleLine = true, modifier = Modifier.padding(top = 10.dp))
                }
            },
            confirmButton = {
                Button(
                    enabled = ok && !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        busy = true
                        scope.launch {
                            if (CloudSync.signedIn()) {
                                // Delete the cloud copy first, otherwise sync would bring the old data back.
                                runCatching { CloudSync.deleteCloud() }
                                CloudSync.signOut()
                            }
                            AiConfig(context).clearNotes()
                            repo.resetAll()
                            busy = false
                            step = 0
                            Toast.makeText(context, Tr.t("Everything was deleted", prefs.lang), Toast.LENGTH_SHORT).show()
                        }
                    },
                ) { Text("Delete everything") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { step = 0 }) { Text("Cancel") } },
        )
    }
}
