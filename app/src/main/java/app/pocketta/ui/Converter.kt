package app.pocketta.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pocketta.core.Money
import app.pocketta.data.Prefs

/** One thing you can convert: a currency, or a gram of gold of one karat. */
private data class ConvUnit(val key: String, val label: String, val valueInBase: Double)

private val goldKarats = listOf("24", "21", "18")
private val preferredOrder = listOf("USD", "EUR", "SAR", "AED", "KWD", "GBP", "QAR")

private fun units(prefs: Prefs): List<ConvUnit> {
    val list = ArrayList<ConvUnit>()
    list += ConvUnit(prefs.baseCurrency, prefs.baseCurrency, 1.0)
    val codes = (preferredOrder + prefs.autoCurrencies + prefs.rates.keys.sorted() + prefs.onlineRates.keys.sorted())
        .distinct()
        .filter { it != prefs.baseCurrency && (prefs.rates.containsKey(it) || (prefs.autoRates && prefs.onlineRates.containsKey(it))) }
    for (c in codes) list += ConvUnit(c, c, prefs.rateOf(c))
    for (k in goldKarats) prefs.gold[k]?.let { list += ConvUnit("GOLD$k", "Gold ${k}k (gram)", it) }
    return list
}

/** Type an amount, pick what it is and what you want it in, and see the result with today's rates. */
@Composable
fun ConverterCard(prefs: Prefs) {
    val all = units(prefs)
    var amount by rememberSaveable { mutableStateOf("1") }
    var fromKey by rememberSaveable { mutableStateOf(all.firstOrNull { it.key == "USD" }?.key ?: all.getOrNull(1)?.key ?: prefs.baseCurrency) }
    var toKey by rememberSaveable { mutableStateOf(prefs.baseCurrency) }
    SectionCard {
        Text("Convert money and gold", fontWeight = FontWeight.SemiBold)
        if (all.size < 2) {
            Text("Turn on today's rates below, or add your own rate, to convert.", style = MaterialTheme.typography.bodySmall)
            return@SectionCard
        }
        val from = all.firstOrNull { it.key == fromKey } ?: all[0]
        val to = all.firstOrNull { it.key == toKey } ?: all[0]
        Gap(8)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Amount", amount, { amount = it }, Modifier.weight(1f))
            UnitPicker(all, from, { fromKey = it.key }, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.Center) {
            FilledTonalIconButton(onClick = { val t = fromKey; fromKey = toKey; toKey = t }) { Text("⇅") }
        }
        val a = parseNumber(amount) ?: 0.0
        val result = if (to.valueInBase > 0) a * from.valueInBase / to.valueInBase else 0.0
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Big(fmtResult(result), size = 26)
            }
            UnitPicker(all, to, { toKey = it.key }, Modifier.weight(1f))
        }
        Gap(4)
        val one = if (to.valueInBase > 0) from.valueInBase / to.valueInBase else 0.0
        Text("1 ${from.label} = ${fmtResult(one)} ${to.label}", style = MaterialTheme.typography.bodySmall)
        if (prefs.ratesUpdated.isNotEmpty()) Text("Rates of ${prefs.ratesUpdated.take(10)}", style = MaterialTheme.typography.bodySmall)
    }
}

private fun fmtResult(v: Double): String = when {
    v == 0.0 -> "0"
    v < 0.01 -> String.format(java.util.Locale.US, "%.6f", v).trimEnd('0').trimEnd('.')
    v < 10 -> String.format(java.util.Locale.US, "%.4f", v).trimEnd('0').trimEnd('.')
    else -> Money.fmt(v)
}

@Composable
private fun UnitPicker(all: List<ConvUnit>, selected: ConvUnit, onPick: (ConvUnit) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text(selected.label + "  ▾", maxLines = 1) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (u in all) DropdownMenuItem(text = { Text(u.label) }, onClick = { onPick(u); open = false })
        }
    }
}
