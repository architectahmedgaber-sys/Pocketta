package app.pocketta.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * "Saved · Undo" for 5 seconds after saving, editing, deleting or paying, instead of asking "are you sure?".
 * Only the latest action can be undone. [undo] must put everything back (the money record and what it changed).
 */
object UndoBus {
    class Item(val id: Long, val text: String, val undo: suspend () -> Unit)

    private val _item = MutableStateFlow<Item?>(null)
    val item: StateFlow<Item?> = _item
    private var counter = 0L

    fun show(text: String, undo: suspend () -> Unit) { _item.value = Item(++counter, text, undo) }
    fun clear(id: Long) { if (_item.value?.id == id) _item.value = null }
}

@Composable
fun UndoHost(modifier: Modifier = Modifier) {
    val item by UndoBus.item.collectAsState()
    val it = item ?: return
    val scope = rememberCoroutineScope()
    LaunchedEffect(it.id) { delay(5000); UndoBus.clear(it.id) }
    Surface(
        modifier = modifier.padding(horizontal = 24.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        shadowElevation = 6.dp,
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(it.text + " · ", color = MaterialTheme.colorScheme.inverseOnSurface)
            TextButton(onClick = { UndoBus.clear(it.id); scope.launch { runCatching { it.undo() } } }) {
                Text("Undo", color = MaterialTheme.colorScheme.inversePrimary)
            }
        }
    }
}
