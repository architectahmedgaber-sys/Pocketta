package app.pocketta.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pocketta.core.QuickText
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

val FieldShape = RoundedCornerShape(14.dp)

val dayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)
val dayYearFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
val monthFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM", Locale.US)
val monthYearFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.US)

fun parseNumber(s: String): Double? = QuickText.normalizeDigits(s).replace(" ", "").toDoubleOrNull()

fun numText(v: Double): String = if (v == 0.0) "" else if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

@Composable
fun BackBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
        actions = actions,
    )
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainer,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val m = modifier.fillMaxWidth()
    if (onClick != null) {
        Card(onClick = onClick, modifier = m, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = container)) {
            Column(Modifier.padding(16.dp), content = content)
        }
    } else {
        Card(modifier = m, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = container)) {
            Column(Modifier.padding(16.dp), content = content)
        }
    }
}

@Composable
fun Label(text: String, modifier: Modifier = Modifier) = Text(
    tr(text).uppercase(), modifier = modifier, style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.8.sp,
)

@Composable
fun Big(text: String, color: Color = Color.Unspecified, size: Int = 28) =
    Text(text, fontSize = size.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)

@Composable
fun Pill(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.16f)).padding(horizontal = 8.dp, vertical = 2.dp)
    ) { Text(text, color = color, style = MaterialTheme.typography.labelMedium, maxLines = 1) }
}

@Composable
fun Initial(text: String, color: Color = MaterialTheme.colorScheme.primary, size: Int = 40) {
    val emoji = Emoji.of(text)
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.32f).dp)).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        val raw = text.trim()
        if (emoji != null) Text(emoji, fontSize = (size * 0.48f).sp)
        else if (raw.isNotEmpty() && !raw[0].isLetterOrDigit() && raw.length <= 8) Text(raw, fontSize = (size * 0.48f).sp)
        else Text(text.trim().take(1).uppercase().ifEmpty { "•" }, color = color, fontWeight = FontWeight.Bold, fontSize = (size * 0.4f).sp)
    }
}

@Composable
fun ListRow(
    title: String,
    subtitle: String? = null,
    icon: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) Initial(icon)
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        trailing()
    }
}

@Composable
fun Progress(fraction: Float, color: Color = MaterialTheme.colorScheme.primary, modifier: Modifier = Modifier, track: Color = MaterialTheme.colorScheme.surfaceVariant) {
    LinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp)),
        color = color,
        trackColor = track,
        strokeCap = StrokeCap.Round,
    )
}

@Composable
fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, decimal: Boolean = true, suffix: String? = null) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        suffix = suffix?.let { { Text(it) } },
        shape = FieldShape,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun TextInput(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        shape = FieldShape, modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun <T> Choices(options: List<T>, selected: T?, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (o in options) FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) })
    }
}

@Composable
fun DateField(label: String, date: LocalDate?, onChange: (LocalDate?) -> Unit, modifier: Modifier = Modifier, allowClear: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth().clip(FieldShape).border(1.dp, MaterialTheme.colorScheme.outline, FieldShape)
            .clickable { open = true }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(date?.format(dayYearFmt) ?: "Not set", style = MaterialTheme.typography.bodyLarge)
        }
        if (allowClear && date != null) TextButton(onClick = { onChange(null) }) { Text("Clear") }
        else Text("📅", fontSize = 18.sp)
    }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = (date ?: LocalDate.now()).toEpochDay() * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(LocalDate.ofEpochDay(Math.floorDiv(it, 86_400_000L))) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
}

/** A bottom sheet with a form: title, fields, then Save (and Delete when editing). */
@Composable
fun FormDialog(
    title: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean = true,
    onDelete: (() -> Unit)? = null,
    saveLabel: String = "Save",
    content: @Composable ColumnScope.() -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title.ifBlank { "Edit" }, style = MaterialTheme.typography.headlineSmall)
            content()
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) {
                    TextButton(onClick = { confirmDelete = true }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(onClick = onSave, enabled = saveEnabled, modifier = Modifier.padding(start = 8.dp)) { Text(saveLabel) }
            }
        }
    }
    if (confirmDelete && onDelete != null) {
        ConfirmDialog("Delete \"$title\"?", "This can't be undone.", "Delete", onConfirm = { confirmDelete = false; onDelete() }, onDismiss = { confirmDelete = false })
    }
}

/** A titled group of fields inside a form. */
@Composable
fun FieldGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun Gap(h: Int = 12) = Spacer(Modifier.height(h.dp))

@Composable
fun Divider() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
