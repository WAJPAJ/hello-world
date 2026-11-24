package pk.tradesim.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pk.tradesim.data.MarketData
import pk.tradesim.engine.Engine
import pk.tradesim.engine.GameState
import pk.tradesim.engine.Side
import pk.tradesim.engine.TradeKind
import pk.tradesim.util.dateShort
import pk.tradesim.util.pct
import pk.tradesim.util.px
import pk.tradesim.util.qty
import pk.tradesim.util.rs
import pk.tradesim.util.signedRs
import java.util.Locale

@Composable
fun JournalScreen(state: GameState, md: MarketData) {
    val s = Engine.stats(state, md)
    val vsIndex = s.totalReturnPct - s.indexReturnPct

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Scorecard", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Your return", pct(s.totalReturnPct), Modifier.weight(1f), pnlColor(s.totalReturnPct))
                    StatTile("vs SIM-100", String.format(Locale.US, "%+.2f pts", vsIndex), Modifier.weight(1f), pnlColor(vsIndex))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Worst drop from peak", String.format(Locale.US, "-%.1f%%", s.maxDrawdownPct), Modifier.weight(1f))
                    StatTile("Win rate", s.winRatePct?.let { String.format(Locale.US, "%.0f%%", it) } ?: "–", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Trades", "${s.totalTrades}", Modifier.weight(1f))
                    StatTile("Fees paid", rs(s.feesPaid), Modifier.weight(1f))
                }
                Text(
                    "Win rate counts sales made at a profit (after fees). T-bills held to maturity are excluded. " +
                        "Worst drop from peak shows how deep your portfolio fell before recovering.",
                    color = Muted, style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        val b = s.bestTrade
        val w = s.worstTrade
        if (b != null && w != null) {
            item {
                SectionCard(title = if (s.closedTrades > 1) "Best and worst sale" else "Your only sale so far") {
                    LabelValue("${if (s.closedTrades > 1) "Best: " else ""}${b.instrumentId} on ${dateShort(md.dates[b.day])}",
                        signedRs(b.realizedPnl!!), pnlColor(b.realizedPnl))
                    if (s.closedTrades > 1) {
                        LabelValue("Worst: ${w.instrumentId} on ${dateShort(md.dates[w.day])}", signedRs(w.realizedPnl!!), pnlColor(w.realizedPnl))
                    }
                }
            }
        }
        item {
            Text("Trade history", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp))
        }
        if (state.trades.isEmpty()) {
            item { Text("No trades yet. Each trade you make, with your reason, will appear here.", color = Muted) }
        }
        items(state.trades.asReversed(), key = { it.id }) { t ->
            SectionCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    val label = when (t.kind) {
                        TradeKind.MATURITY -> "MATURED"
                        TradeKind.LIMIT -> "${t.side.name} (limit)"
                        TradeKind.MARKET -> t.side.name
                    }
                    Text("$label ${t.instrumentId}", fontWeight = FontWeight.SemiBold,
                        color = if (t.side == Side.BUY) Gain else Loss)
                    Text(dateShort(md.dates[t.day]), color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                Text("${qty(t.qty)} @ ${px(t.price)} · fees ${rs(t.fees)}", style = MaterialTheme.typography.bodyMedium)
                t.realizedPnl?.let {
                    Text("Result: ${signedRs(it)}", color = pnlColor(it), fontWeight = FontWeight.Medium)
                }
                if (t.note.isNotBlank()) {
                    Text("“${t.note}”", fontStyle = FontStyle.Italic, color = Muted, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
