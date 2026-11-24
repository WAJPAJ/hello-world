package pk.tradesim.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pk.tradesim.data.MarketData

private val CAPITAL = listOf(1_000_000.0 to "Rs 1M", 5_000_000.0 to "Rs 5M", 10_000_000.0 to "Rs 10M")

@Composable
fun NewGameScreen(onStart: (seed: Long, cash: Double, startDay: Int) -> Unit, onCancel: (() -> Unit)?) {
    var capital by rememberSaveable { mutableIntStateOf(0) }
    var scenario by rememberSaveable { mutableIntStateOf(0) }
    var seedText by rememberSaveable { mutableStateOf("2030") }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("TradeSim PK", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Teal)
            Text("Practise trading stocks, gold and T-bills with virtual money on a simulated Pakistani-style market.",
                style = MaterialTheme.typography.bodyLarge)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Text(
                    "All companies, prices, rates and fees in this app are fictional and generated for practice only. " +
                        "Not investment advice.",
                    modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium,
                )
            }

            Text("Starting capital", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CAPITAL.forEachIndexed { i, (_, label) ->
                    FilterChip(selected = capital == i, onClick = { capital = i }, label = { Text(label) })
                }
            }

            Text("Scenario", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            MarketData.SCENARIOS.forEachIndexed { i, sc ->
                Card(
                    Modifier.fillMaxWidth().clickable { scenario = i },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Soft),
                    border = if (scenario == i) BorderStroke(2.dp, Teal) else null,
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = scenario == i, onClick = { scenario = i })
                        Column {
                            Text(sc.name, fontWeight = FontWeight.SemiBold)
                            Text(sc.description, color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            Text("Market number", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = seedText,
                onValueChange = { v -> seedText = v.filter { it.isDigit() }.take(9) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = { Text("Friends who pick the same market number and scenario get exactly the same price history, so you can compare results.") },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    onStart(seedText.toLongOrNull() ?: 2030L, CAPITAL[capital].first, MarketData.SCENARIOS[scenario].startDay)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Start simulation") }
            if (onCancel != null) {
                TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Back to current game") }
            }
        }
    }
}
