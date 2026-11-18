package app.pocketta.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pocketta.core.Circle
import app.pocketta.core.CircleStatus
import app.pocketta.core.Circles
import app.pocketta.core.Money
import app.pocketta.data.CircleEntity
import app.pocketta.data.Prefs
import app.pocketta.data.Repo
import kotlinx.coroutines.launch
import java.time.LocalDate

private data class GameyaRow(val entity: CircleEntity, val status: CircleStatus)

/** One card per running gam'eya: when it's your turn, how much you collect, how many installments are left. */
@Composable
fun GameyaCard(repo: Repo, prefs: Prefs) {
    val version by repo.version.collectAsState()
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf(emptyList<GameyaRow>()) }
    LaunchedEffect(version) {
        val today = LocalDate.now()
        rows = repo.dao.circles().map {
            val c = Circle(it.share, it.months, it.myTurn, it.paid, LocalDate.ofEpochDay(it.firstDue))
            GameyaRow(it, Circles.status(c, it.payoutReceived, today))
        }.filter { !it.status.finished }
    }
    val cur = prefs.baseCurrency
    for (r in rows) {
        val s = r.status
        val c = r.entity
        SectionCard(container = if (s.collectNow) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainer) {
            Label("Gam'eya")
            Text(c.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Gap(4)
            if (s.payoutReceived) {
                Text("You collected your turn", color = Good, fontWeight = FontWeight.SemiBold)
            } else if (s.collectNow) {
                Text("It's your turn! Collect " + Money.short(s.payout) + " " + cur, color = Good, fontWeight = FontWeight.Bold)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Your turn", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(s.payoutDate.format(dayYearFmt), fontWeight = FontWeight.Bold)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Days to your turn", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(s.daysToTurn.toString(), fontWeight = FontWeight.Bold)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("You collect", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(Money.short(s.payout) + " " + cur, fontWeight = FontWeight.Bold, color = Good)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Installments left", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(s.installmentsLeft.toString() + " / " + c.months, fontWeight = FontWeight.Bold)
            }
            val next = s.nextDue
            if (next != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Next installment", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(Money.short(c.share) + " " + cur + " · " + next.format(dayFmt), fontWeight = FontWeight.Bold)
                }
            }
            Progress(if (c.months > 0) c.paid.toFloat() / c.months else 0f)
            if (s.collectNow || next != null) {
                Gap(8)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (s.collectNow) Button(onClick = { scope.launch { repo.receivePayout(c) } }) { Text("Got my payout") }
                    if (next != null && !next.isAfter(LocalDate.now().plusDays(7))) {
                        OutlinedButton(onClick = { scope.launch { repo.payCircle(c) } }) { Text("Paid installment") }
                    }
                }
            }
        }
    }
}
