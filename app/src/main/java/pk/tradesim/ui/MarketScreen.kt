package pk.tradesim.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pk.tradesim.data.AssetClass
import pk.tradesim.data.Instrument
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
import java.util.Locale

@Composable
fun MarketScreen(state: GameState, md: MarketData, onOpen: (String) -> Unit) {
    val day = state.currentDay
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            val ch = md.change(MarketData.INDEX_ID, day)
            SectionCard {
                Text("SIM-100 Index", color = Muted, style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(px(md.close(MarketData.INDEX_ID, day)), style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold)
                    Text("  ${pct(ch)}", color = pnlColor(ch), fontWeight = FontWeight.Medium)
                }
                val from = maxOf(0, day - 63)
                LineChart(listOf((from..day).map { md.close(MarketData.INDEX_ID, it) }), listOf(Teal),
                    Modifier.fillMaxWidth().height(110.dp))
                Text("Last 3 months · all companies and prices are fictional", color = Muted,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            SectionCard(title = "Stocks") {
                md.tradables.filter { it.assetClass == AssetClass.STOCK }.forEachIndexed { i, ins ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.background)
                    QuoteRow(ins, sub = ins.name + " · " + ins.sector, price = px(md.close(ins.id, day)),
                        change = md.change(ins.id, day), changeText = pct(md.change(ins.id, day))) { onOpen(ins.id) }
                }
            }
        }
        item {
            SectionCard(title = "Gold") {
                val g = MarketData.GOLD
                QuoteRow(g, sub = "Price per tola", price = rs(md.close(g.id, day)),
                    change = md.change(g.id, day), changeText = pct(md.change(g.id, day))) { onOpen(g.id) }
            }
        }
        item {
            SectionCard(title = "Treasury bills") {
                MarketData.BILLS.forEachIndexed { i, b ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.background)
                    val y = md.yieldPct(b.id, day)
                    val ch = md.change(b.id, day)
                    QuoteRow(b, sub = b.name, price = String.format(Locale.US, "%.2f%%", y), change = -ch,
                        changeText = String.format(Locale.US, "%+.2f pts", ch)) { onOpen(b.id) }
                }
                Text("Yield per year. Buy at a discount and get Rs 1,000 per unit at maturity.",
                    color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun QuoteRow(ins: Instrument, sub: String, price: String, change: Double, changeText: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(ins.id, fontWeight = FontWeight.SemiBold)
            Text(sub, color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(price, fontWeight = FontWeight.Medium)
            Text(changeText, color = pnlColor(change), style = MaterialTheme.typography.bodySmall)
        }
    }
}

private val RANGES = listOf("1M" to 21, "3M" to 63, "1Y" to 252, "All" to 10_000)

@Composable
fun InstrumentScreen(
    state: GameState, md: MarketData, instrumentId: String,
    onTrade: (TradeRequest) -> Unit,
) {
    val ins = md.instrument(instrumentId)
    val day = state.currentDay
    val isBill = ins.assetClass == AssetClass.TBILL
    var range by rememberSaveable { mutableIntStateOf(1) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionCard {
                Text("${ins.id} · ${ins.sector}", color = Muted, style = MaterialTheme.typography.labelLarge)
                Text(ins.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                if (isBill) {
                    val ch = md.change(ins.id, day)
                    Text(String.format(Locale.US, "%.2f%% yield", md.yieldPct(ins.id, day)),
                        style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(String.format(Locale.US, "%+.2f pts today", ch), color = pnlColor(-ch))
                    Text("Price today: ${px(md.price(ins.id, day))} ${ins.unitLabel}", color = Muted)
                } else {
                    val ch = md.change(ins.id, day)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(px(md.close(ins.id, day)), style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold)
                        Text("  ${pct(ch)}", color = pnlColor(ch), fontWeight = FontWeight.Medium)
                    }
                    Text(ins.unitLabel, color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            SectionCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RANGES.forEachIndexed { i, (label, _) ->
                        FilterChip(selected = range == i, onClick = { range = i }, label = { Text(label) })
                    }
                }
                val from = maxOf(0, day - RANGES[range].second)
                val pts = (from..day).map { if (isBill) md.yieldPct(ins.id, it) else md.close(ins.id, it) }
                LineChart(listOf(pts), listOf(if (pts.last() >= pts.first()) Gain else Loss),
                    Modifier.fillMaxWidth().height(200.dp))
                Text("${dateShort(md.dates[from])} → ${dateShort(md.dates[day])}" +
                    if (isBill) " · yield %" else "", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (!isBill) {
            item {
                SectionCard(title = "Today") {
                    val b = md.bar(ins.id, day)
                    val from = maxOf(0, day - 251)
                    LabelValue("Open", px(b.open))
                    LabelValue("High / Low", "${px(b.high)} / ${px(b.low)}")
                    LabelValue("52-week high", px((from..day).maxOf { md.bar(ins.id, it).high }))
                    LabelValue("52-week low", px((from..day).minOf { md.bar(ins.id, it).low }))
                }
            }
        } else {
            item {
                SectionCard(title = "How it works") {
                    Text("Pay ${px(md.price(ins.id, day))} today for each unit and receive Rs 1,000 on maturity, " +
                        "${ins.tenorTradingDays} trading days later. If yields rise while you hold it, its resale value falls " +
                        "(and the other way round). Sell early from the Portfolio tab.",
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        val positions = state.holdings.filter { it.instrumentId == ins.id }
        if (positions.isNotEmpty()) {
            item {
                SectionCard(title = "Your position") {
                    positions.forEach { h ->
                        val p = Engine.holdingPrice(md, h, day)
                        val pnl = (p - h.avgCost) * h.qty
                        LabelValue("Quantity", qty(h.qty) + if (h.maturityDay >= 0)
                            " · matures ${dateShort(md.dates[minOf(h.maturityDay, md.lastDay)])}" else "")
                        LabelValue("Avg cost (incl. fees)", px(h.avgCost))
                        LabelValue("Market value", rs(p * h.qty))
                        LabelValue("Unrealised P&L", "${signedRs(pnl)} (${pct((p / h.avgCost - 1) * 100)})", pnlColor(pnl))
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { onTrade(TradeRequest(ins.id, Side.BUY)) }, modifier = Modifier.weight(1f)) { Text("Buy") }
                if (!isBill) {
                    OutlinedButton(
                        onClick = { onTrade(TradeRequest(ins.id, Side.SELL, ins.id)) },
                        enabled = positions.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    ) { Text("Sell") }
                }
            }
        }
    }
}
