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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pk.tradesim.data.AssetClass
import pk.tradesim.data.MarketData
import pk.tradesim.engine.Engine
import pk.tradesim.engine.GameState
import pk.tradesim.util.dateLong
import pk.tradesim.util.pct
import pk.tradesim.util.px
import pk.tradesim.util.rs
import pk.tradesim.util.signedRs

@Composable
fun HomeScreen(state: GameState, md: MarketData, onAdvance: (Int) -> Unit, onOpen: (String) -> Unit) {
    val stats = Engine.stats(state, md)
    val finished = state.currentDay >= md.lastDay
    val day = state.currentDay

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionCard {
                Text("Net worth", color = Muted, style = MaterialTheme.typography.labelLarge)
                Text(rs(stats.netWorth), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                val gain = stats.netWorth - state.startingCash
                Text("${signedRs(gain)}  (${pct(stats.totalReturnPct)}) since start",
                    color = pnlColor(gain), fontWeight = FontWeight.Medium)
                val curve = state.equityCurve
                if (curve.size >= 2) {
                    val d = curve.last() - curve[curve.size - 2]
                    Text("Today: ${signedRs(d)}", color = pnlColor(d), style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(8.dp))
                LabelValue("Cash", rs(state.cash))
                LabelValue("Invested", rs(stats.netWorth - state.cash))
            }
        }

        item {
            SectionCard(title = "Time machine") {
                val total = md.lastDay - state.startDay
                val done = day - state.startDay
                Text(dateLong(md.dates[day]), fontWeight = FontWeight.Medium)
                Text("Trading day $done of $total", color = Muted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { if (total == 0) 1f else done.toFloat() / total },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                if (finished) {
                    Text("Simulation complete. Check your Journal for the final scorecard, or start a new game from the menu.",
                        color = Teal, fontWeight = FontWeight.Medium)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val pad = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        Button(onClick = { onAdvance(1) }, modifier = Modifier.weight(1f), contentPadding = pad) {
                            Text("+1 day", maxLines = 1, softWrap = false)
                        }
                        FilledTonalButton(onClick = { onAdvance(5) }, modifier = Modifier.weight(1f), contentPadding = pad) {
                            Text("+1 week", maxLines = 1, softWrap = false)
                        }
                        FilledTonalButton(onClick = { onAdvance(21) }, modifier = Modifier.weight(1f), contentPadding = pad) {
                            Text("+1 month", maxLines = 1, softWrap = false)
                        }
                    }
                    Text("Trades fill at today's closing price. Prices for future days are hidden until you get there.",
                        color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }

        item {
            SectionCard(title = "You vs the market") {
                val you = state.equityCurve.map { it / state.startingCash * 100 }
                val base = md.close(MarketData.INDEX_ID, state.startDay)
                val index = (state.startDay..day).map { md.close(MarketData.INDEX_ID, it) / base * 100 }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LegendDot(Teal, "You ${pct(stats.totalReturnPct)}")
                    LegendDot(Amber, "SIM-100 ${pct(stats.indexReturnPct)}")
                }
                Spacer(Modifier.height(8.dp))
                LineChart(listOf(index, you), listOf(Amber, Teal), Modifier.fillMaxWidth().height(180.dp), baseline = 100.0)
                Text("Both lines start at 100.", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }

        if (state.events.isNotEmpty()) {
            item {
                SectionCard(title = "What just happened") {
                    state.events.takeLast(8).forEach {
                        Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            }
        }

        item {
            SectionCard(title = "Today's movers") {
                val movers = md.tradables.filter { it.assetClass == AssetClass.STOCK }
                    .map { it to md.change(it.id, day) }.sortedByDescending { it.second }
                val list = movers.take(3) + movers.takeLast(3)
                list.forEach { (ins, ch) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpen(ins.id) }.padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(ins.id, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(64.dp))
                        Text(ins.name, color = Muted, modifier = Modifier.weight(1f))
                        Text(px(md.close(ins.id, day)), modifier = Modifier.padding(horizontal = 8.dp))
                        Text(pct(ch), color = pnlColor(ch), fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        item { Column { Spacer(Modifier.height(8.dp)) } }
    }
}
