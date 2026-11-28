package app.pocketta.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pocketta.core.Countries
import app.pocketta.i18n.Tr

/** A searchable list of countries (and the dollar / euro). [selected] and [onPick] use [Countries.Country.code]. */
@Composable
fun CountryPicker(selected: String, onPick: (Countries.Country) -> Unit) {
    val ar = LocalLang.current == Tr.AR
    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()
    val shown = Countries.choices.filter {
        q.isEmpty() || it.ar.contains(q) || it.en.lowercase().contains(q) || it.currency.lowercase().contains(q)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TextInput("Search your country", query, { query = it })
        for (c in shown) {
            val on = c.code == selected
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(if (on) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onPick(c) }.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.Text(c.flag, style = MaterialTheme.typography.titleLarge)
                androidx.compose.material3.Text(
                    if (ar) c.ar else c.en, Modifier.weight(1f).padding(horizontal = 12.dp),
                    fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                )
                androidx.compose.material3.Text(
                    c.currency + if (c.decimals == 3) " · 3" else if (c.decimals == 0) " · 0" else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
