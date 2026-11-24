package pk.tradesim.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pk.tradesim.data.AssetClass
import pk.tradesim.data.MarketData
import pk.tradesim.engine.Engine
import pk.tradesim.engine.GameState
import pk.tradesim.engine.Side
import pk.tradesim.util.dateShort
import pk.tradesim.util.pct
import pk.tradesim.util.px
import pk.tradesim.util.qty
import pk.tradesim.util.rs
import pk.tradesim.util.signedRs

private val ClassColors = mapOf(
    AssetClass.STOCK to Teal,
    AssetClass.GOLD to Amber,
    AssetClass.TBILL to Color(0xFF5C6BC0),
)
private val CashColor = Color(0xFFB0BEC5)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PortfolioScreen(
    state: GameState, md: MarketData,
    onOpen: (String) -> Unit,
    onTrade: (TradeRequest) -> Unit,
    onCancelOrder: (Long) -> Unit,
) {
    val day = state.currentDay
    val stats = Engine.stats(state, md)
    val byClass = state.holdings.groupBy { md.instrument(it.instrumentId).assetClass }
        .mapValues { (_, hs) -> hs.sumOf { it.qty * Engine.holdingPrice(md, it, day) } }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionCard(title = "Summary") {
                LabelValue("Net worth", rs(stats.netWorth))
                LabelValue("Cash", rs(state.cash))
                LabelValue("Unrealised P&L", signedRs(stats.unrealizedPnl), pnlColor(stats.unrealizedPnl))
                LabelValue("Realised P&L", signedRs(stats.realizedPnl), pnlColor(stats.realizedPnl))
                Spacer(Modifier.height(10.dp))
                Text("Allocation", style = MaterialTheme.typography.labelLarge, color = Muted)
                Spacer(Modifier.height(6.dp))
                val parts = ClassColors.map { (ac, c) -> (byClass[ac] ?: 0.0) to c } + (state.cash to CashColor)
                AllocationBar(parts)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val total = stats.netWorth
                    ClassColors.forEach { (ac, c) ->
                        LegendDot(c, "${ac.label} ${String.format(java.util.Locale.US, "%.0f%%", (byClass[ac] ?: 0.0) / total * 100)}")
                    }
                    LegendDot(CashColor, "Cash ${String.format(java.util.Locale.US, "%.0f%%", state.cash / total * 100)}")
                }
            }
        }

        item {
            SectionCard(title = "Holdings") {
                if (state.holdings.isEmpty()) {
                    Text("Nothing yet. Open the Market tab to make your first trade.", color = Muted)
                }
                state.holdings.sortedByDescending { it.qty * Engine.holdingPrice(md, it, day) }.forEachIndexed { i, h ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.background)
                    val ins = md.instrument(h.instrumentId)
                    val p = Engine.holdingPrice(md, h, day)
                    val pnl = (p - h.avgCost) * h.qty
                    val isBill = ins.assetClass == AssetClass.TBILL
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            if (isBill) onTrade(TradeRequest(ins.id, Side.SELL, h.key)) else onOpen(ins.id)
                        }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(ins.id, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (isBill) "${qty(h.qty)} units · matures ${dateShort(md.dates[minOf(h.maturityDay, md.lastDay)])} · tap to sell"
                                else "${qty(h.qty)} @ ${px(h.avgCost)}",
                                color = Muted, style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(rs(p * h.qty), fontWeight = FontWeight.Medium)
                            Text("${signedRs(pnl)} (${pct((p / h.avgCost - 1) * 100)})",
                                color = pnlColor(pnl), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        if (state.orders.isNotEmpty()) {
            item {
                SectionCard(title = "Open limit orders") {
                    state.orders.forEach { o ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${o.side.name} ${qty(o.qty)} ${o.instrumentId} @ ${px(o.limitPrice)}",
                                    fontWeight = FontWeight.Medium, color = if (o.side == Side.BUY) Gain else Loss)
                                Text("Expires ${dateShort(md.dates[minOf(o.expiryDay, md.lastDay)])} · now ${px(md.close(o.instrumentId, day))}",
                                    color = Muted, style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { onCancelOrder(o.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Cancel order")
                            }
                        }
                    }
                }
            }
        }
    }
}
