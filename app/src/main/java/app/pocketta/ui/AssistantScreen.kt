package app.pocketta.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.pocketta.ai.AiConfig
import app.pocketta.ai.AiError
import app.pocketta.ai.AiProvider
import app.pocketta.ai.AiSession
import app.pocketta.data.Prefs
import app.pocketta.data.Repo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch

private class Bubble(val role: String, val text: String)

/** Miqrish: a chat with the user's own AI key. It reads and changes Pocketta through tools, always asking before saving. */
@Composable
fun AssistantScreen(repo: Repo, nav: Nav, prefs: Prefs, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val cfg = remember { AiConfig(ctx) }
    val scope = rememberCoroutineScope()
    val bubbles = remember { mutableStateListOf<Bubble>() }
    var pending by remember { mutableStateOf<Pair<String, CompletableDeferred<Boolean>>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(cfg.key().isBlank()) }
    var input by remember { mutableStateOf("") }
    var provider by remember { mutableStateOf(cfg.provider) }
    val listState = rememberLazyListState()

    var mode by remember { mutableStateOf("chat") }
    var chatId by remember { mutableStateOf(0) }
    var confirmNew by remember { mutableStateOf(false) }
    val session = remember(chatId) {
        AiSession(
            repo, cfg,
            confirm = { what ->
                val d = CompletableDeferred<Boolean>()
                pending = what to d
                val ok = d.await()
                pending = null
                ok
            },
            show = { role, text -> bubbles.add(Bubble(role, text)) },
        )
    }
    LaunchedEffect(bubbles.size, busy) { if (bubbles.isNotEmpty()) listState.animateScrollToItem(bubbles.size - 1) }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || busy) return
        input = ""
        bubbles.add(Bubble("me", text))
        busy = true
        scope.launch {
            try {
                session.send(text)
            } catch (e: AiError) {
                bubbles.add(Bubble("error", e.friendly()))
            } catch (e: Exception) {
                bubbles.add(Bubble("error", "Could not reach the assistant. Check your internet and try again."))
            } finally {
                busy = false
            }
        }
    }

    Scaffold(modifier = modifier, contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0), topBar = {
        TabBar("Miqrish") {
            if (mode == "chat" && !settings && bubbles.isNotEmpty()) TextButton(onClick = { confirmNew = true }, enabled = !busy) { Text("＋ New chat") }
            if (mode == "chat") TextButton(onClick = { settings = !settings }) { Text(if (settings) "Chat" else "Settings") }
        }
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Choices(listOf("chat", "quick"), mode, { if (it == "chat") "Chat" else "AI quick entry" }, { mode = it })
            }
            if (mode == "quick") {
                QuickAiScreen(repo, nav, prefs, embedded = true, modifier = Modifier.weight(1f))
            } else if (settings) {
                AssistantSettings(cfg, repo, prefs.weeklyAiOn, provider, { provider = it }, Modifier.weight(1f)) { settings = false }
            } else {
                Column(Modifier.weight(1f).fillMaxSize().imePadding()) {
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (bubbles.isEmpty()) item {
                        SectionCard {
                            Text("Hi, I'm Miqrish, your money advisor.", style = MaterialTheme.typography.titleMedium)
                            Gap(6)
                            Text("Tell me things like: koshary 60 · I borrowed 1000 from Basem · I paid the installment · how much did I spend on food this month? I always ask before I save anything.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    items(bubbles.toList()) { b -> BubbleView(b) }
                    if (busy) item { CircularProgressIndicator(Modifier.padding(8.dp)) }
                }
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f), shape = FieldShape,
                        placeholder = { Text("Write to Miqrish") },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                    Button(onClick = { send() }, enabled = input.isNotBlank() && !busy) { Text("Send") }
                }
                }
            }
        }
    }

    if (confirmNew) {
        AlertDialog(
            onDismissRequest = { confirmNew = false },
            title = { Text("Start a new chat?") },
            text = { Text("This clears the current chat only. Your spending and what Miqrish learned about you stay saved.") },
            confirmButton = { TextButton(onClick = { bubbles.clear(); pending = null; chatId++; confirmNew = false }) { Text("Yes, start new") } },
            dismissButton = { TextButton(onClick = { confirmNew = false }) { Text("No") } },
        )
    }

    pending?.let { (what, d) ->
        AlertDialog(
            onDismissRequest = { d.complete(false) },
            title = { Text("Save this?") },
            text = { Text(what) },
            confirmButton = { TextButton(onClick = { d.complete(true) }) { Text("Yes, save") } },
            dismissButton = { TextButton(onClick = { d.complete(false) }) { Text("No") } },
        )
    }
}

@Composable
private fun BubbleView(b: Bubble) {
    val mine = b.role == "me"
    val bg = when (b.role) {
        "me" -> MaterialTheme.colorScheme.primary
        "done" -> MaterialTheme.colorScheme.tertiaryContainer
        "error" -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val fg = when (b.role) {
        "me" -> MaterialTheme.colorScheme.onPrimary
        "done" -> MaterialTheme.colorScheme.onTertiaryContainer
        "error" -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Box(Modifier.clip(RoundedCornerShape(16.dp)).background(bg).padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text((if (b.role == "done") "✓ " else "") + b.text, color = fg)
        }
    }
}

@Composable
private fun AssistantSettings(cfg: AiConfig, repo: Repo, weeklyOn: Boolean, provider: AiProvider, onProvider: (AiProvider) -> Unit, modifier: Modifier, onDone: () -> Unit) {
    var key by remember(provider) { mutableStateOf(cfg.key(provider)) }
    var model by remember(provider) { mutableStateOf(cfg.model(provider)) }
    var notesVersion by remember { mutableStateOf(0) }
    var weeklyState by remember { mutableStateOf(weeklyOn) }
    var status by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionCard {
            Label("Choose the company")
            Gap(6)
            Choices(AiProvider.entries.toList(), provider, { it.label }, { cfg.provider = it; onProvider(it) })
            Gap(8)
            Text("Get a key at " + provider.keyHelp + ". Gemini has a free plan that is enough for personal use.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Gap(10)
            OutlinedTextField(
                value = key, onValueChange = { key = it }, label = { Text("Your API key") }, singleLine = true, shape = FieldShape,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
            )
            Gap(8)
            OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Model") }, singleLine = true, shape = FieldShape, modifier = Modifier.fillMaxWidth())
            Gap(10)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        cfg.setKey(provider, key); cfg.setModel(provider, model)
                        // Check the key right away, so a wrong key is explained here and not in the middle of a chat.
                        checking = true; status = "Checking the key..."
                        scope.launch {
                            try {
                                app.pocketta.ai.AiClient.pickModel(provider, key.trim())
                                status = ""; onDone()
                            } catch (e: AiError) {
                                status = e.friendly()
                            } catch (e: Exception) {
                                status = "Could not reach the company. Check your internet."
                            }
                            checking = false
                        }
                    },
                    enabled = key.isNotBlank() && !checking,
                ) { Text("Save and chat") }
                if (cfg.key(provider).isNotBlank()) OutlinedButton(onClick = { cfg.removeKey(provider); key = "" }) { Text("Remove key") }
            }
            if (status.isNotEmpty()) { Gap(8); Text(status, color = MaterialTheme.colorScheme.error) }
            Gap(8)
            Text("The key stays on this phone only. It is not in your backups and it is sent only to the company you picked. Your chat is sent to that company, and any cost is on your own account there.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SectionCard {
            Label("Friday summary")
            Gap(6)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Let Miqrish write my Friday summary (uses a little of your key's balance)", Modifier.weight(1f))
                androidx.compose.material3.Switch(checked = weeklyState, onCheckedChange = { v -> weeklyState = v; scope.launch { repo.set("weeklyAiOn", v) } })
            }
        }
        SectionCard {
            Label("What Miqrish learned about you")
            Gap(6)
            val notes = remember(notesVersion) { cfg.notes() }
            if (notes.isEmpty()) Text("Nothing yet. When you correct it or tell it a habit, it remembers here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else {
                for (n in notes) Text("• $n", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { cfg.clearNotes(); notesVersion++ }) { Text("Forget everything") }
            }
        }
    }
}
