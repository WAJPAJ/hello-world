package pk.tradesim.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pk.tradesim.data.AssetClass
import pk.tradesim.data.MarketData
import pk.tradesim.engine.Engine
import pk.tradesim.engine.GameState
import pk.tradesim.engine.Side
import pk.tradesim.util.px
import pk.tradesim.util.qty
import pk.tradesim.util.rs
import java.util.Locale

@Composable
fun TradeDialog(
    req: TradeRequest,
    state: GameState,
    md: MarketData,
    onDismiss: () -> Unit,
    onSubmit: (TradeInput) -> String?,
) {
    val ins = md.instrument(req.instrumentId)
    val day = state.currentDay
    val isBuy = req.side == Side.BUY
    val holding = state.holdings.find { it.key == (req.holdingKey ?: req.instrumentId) }
    val marketPrice = if (!isBuy && holding != null) Engine.holdingPrice(md, holding, day) else md.price(ins.id, day)
    val canLimit = ins.assetClass == AssetClass.STOCK || ins.assetClass == AssetClass.GOLD

    var qtyText by remember { mutableStateOf("") }
    var isLimit by remember { mutableStateOf(false) }
    var limitText by remember { mutableStateOf(String.format(Locale.US, "%.2f", marketPrice)) }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val q = qtyText.toLongOrNull() ?: 0L
    val price = if (isLimit) limitText.toDoubleOrNull() ?: 0.0 else marketPrice
    val quote = Engine.quote(md, ins.id, req.side, q, price)
    val maxQty = if (isBuy) Engine.maxAffordable(md, state.cash, ins.id, price)
    else (holding?.qty ?: 0L) - if (holding != null && holding.maturityDay < 0) Engine.reservedSells(state, ins.id) else 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${if (isBuy) "Buy" else "Sell"} ${ins.id}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(ins.name, fontWeight = FontWeight.Medium)
                Text("Market: ${px(marketPrice)} ${ins.unitLabel}", color = Muted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                if (canLimit) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !isLimit, onClick = { isLimit = false }, label = { Text("Market") })
                        FilterChip(selected = isLimit, onClick = { isLimit = true }, label = { Text("Limit") })
                    }
                }
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { v -> qtyText = v.filter { it.isDigit() }.take(12); error = null },
                    label = { Text(if (ins.assetClass == AssetClass.TBILL) "Units (Rs 1,000 face each)" else "Quantity") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    trailingIcon = {
                        TextButton(onClick = { qtyText = maxOf(0L, maxQty).toString() }) { Text("Max") }
                    },
                    supportingText = { Text("Max: ${qty(maxOf(0L, maxQty))}") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (isLimit) {
                    OutlinedTextField(
                        value = limitText,
                        onValueChange = { v -> limitText = v.filter { it.isDigit() || it == '.' }.take(12); error = null },
                        label = { Text("Limit price") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        supportingText = {
                            Text(if (isBuy) "Fills if the price falls to this level within ${Engine.ORDER_LIFETIME_DAYS} trading days."
                            else "Fills if the price rises to this level within ${Engine.ORDER_LIFETIME_DAYS} trading days.")
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(4.dp))
                LabelValue("Value", rs(quote.gross))
                LabelValue(if (ins.assetClass == AssetClass.GOLD) "Dealer spread" else "Fees & levies (est.)", rs(quote.fees))
                LabelValue(if (isBuy) "You pay" else "You receive", rs(quote.net))
                if (!isLimit) {
                    LabelValue("Cash after", rs(state.cash + if (isBuy) -quote.net else quote.net))
                }
                if (!isBuy && holding != null && q > 0) {
                    val pnl = quote.net - holding.avgCost * q
                    LabelValue("Profit / loss on this sale", rs(pnl), pnlColor(pnl))
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(280) },
                    label = { Text("Why this trade? (journal)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                error?.let { Text(it, color = Loss, style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = {
            Button(
                enabled = q > 0 && price > 0,
                onClick = {
                    val err = onSubmit(TradeInput(q, if (isLimit) price else null, note.trim()))
                    if (err == null) onDismiss() else error = err
                },
            ) { Text(if (isLimit) "Place order" else if (isBuy) "Confirm buy" else "Confirm sell") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
