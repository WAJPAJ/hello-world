package pk.tradesim.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pk.tradesim.engine.Engine
import pk.tradesim.engine.Fees
import pk.tradesim.util.dateLong
import java.util.Locale

private data class Tab(val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("Home", Icons.Filled.Home),
    Tab("Market", Icons.AutoMirrored.Filled.List),
    Tab("Portfolio", Icons.Filled.AccountCircle),
    Tab("Journal", Icons.Filled.Edit),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeSimApp(vm: GameViewModel) {
    TradeSimTheme {
        val state = vm.state
        val md = vm.market
        var showNewGame by rememberSaveable { mutableStateOf(false) }

        if (state == null || md == null || showNewGame) {
            if (showNewGame) BackHandler { showNewGame = false }
            NewGameScreen(
                onStart = { seed, cash, start -> vm.newGame(seed, cash, start); showNewGame = false },
                onCancel = if (state != null && showNewGame) ({ showNewGame = false }) else null,
            )
            return@TradeSimTheme
        }

        var tab by rememberSaveable { mutableIntStateOf(0) }
        var detailId by rememberSaveable { mutableStateOf<String?>(null) }
        var tradeReq by remember { mutableStateOf<TradeRequest?>(null) }
        var menuOpen by remember { mutableStateOf(false) }
        var showRules by remember { mutableStateOf(false) }
        var confirmNew by remember { mutableStateOf(false) }
        val snackbar = remember { SnackbarHostState() }
        val finished = state.currentDay >= md.lastDay

        LaunchedEffect(vm.message) {
            vm.message?.let {
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar(it)
                vm.message = null
            }
        }
        if (detailId != null) BackHandler { detailId = null }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    navigationIcon = {
                        if (detailId != null) IconButton(onClick = { detailId = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    title = {
                        Column {
                            Text("TradeSim PK", fontWeight = FontWeight.Bold, color = Teal)
                            Text(dateLong(md.dates[state.currentDay]), style = MaterialTheme.typography.bodySmall, color = Muted)
                        }
                    },
                    actions = {
                        IconButton(onClick = { vm.advance(1) }, enabled = !finished) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Next trading day")
                        }
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Rules & assumptions") }, onClick = { menuOpen = false; showRules = true })
                            DropdownMenuItem(text = { Text("New game") }, onClick = { menuOpen = false; confirmNew = true })
                        }
                    },
                )
            },
            bottomBar = {
                NavigationBar(containerColor = Soft) {
                    TABS.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = tab == i && detailId == null,
                            onClick = { tab = i; detailId = null },
                            icon = { Icon(t.icon, contentDescription = t.label) },
                            label = { Text(t.label) },
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding)) {
                val open: (String) -> Unit = { detailId = it }
                val id = detailId
                when {
                    id != null -> InstrumentScreen(state, md, id, onTrade = { tradeReq = it })
                    tab == 0 -> HomeScreen(state, md, onAdvance = vm::advance, onOpen = open)
                    tab == 1 -> MarketScreen(state, md, onOpen = open)
                    tab == 2 -> PortfolioScreen(state, md, onOpen = open, onTrade = { tradeReq = it }, onCancelOrder = vm::cancelOrder)
                    else -> JournalScreen(state, md)
                }
            }
        }

        tradeReq?.let { req ->
            TradeDialog(req, state, md, onDismiss = { tradeReq = null }, onSubmit = { vm.trade(req, it) })
        }

        if (confirmNew) {
            AlertDialog(
                onDismissRequest = { confirmNew = false },
                title = { Text("Start a new game?") },
                text = { Text("Your current portfolio and journal will be replaced.") },
                confirmButton = { TextButton(onClick = { confirmNew = false; detailId = null; showNewGame = true }) { Text("Continue") } },
                dismissButton = { TextButton(onClick = { confirmNew = false }) { Text("Cancel") } },
            )
        }

        if (showRules) {
            AlertDialog(
                onDismissRequest = { showRules = false },
                title = { Text("Rules & assumptions") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        RULES.forEach { Text("• $it", modifier = Modifier.padding(vertical = 3.dp)) }
                    }
                },
                confirmButton = { TextButton(onClick = { showRules = false }) { Text("Got it") } },
            )
        }
    }
}

private fun p(v: Double) = String.format(Locale.US, "%.2f%%", v * 100)

private val RULES = listOf(
    "Everything here is simulated: company names, prices, the SIM-100 index, gold and T-bill yields are generated, not real market data.",
    "Market orders fill at the current day's closing price.",
    "Limit orders stay open for ${Engine.ORDER_LIFETIME_DAYS} trading days. A buy fills if the day's low reaches your price; a sell fills if the day's high does.",
    "Stock prices can move at most ±7.5% a day (simulated price limit).",
    "Stock costs: ${p(Fees.STOCK_COMMISSION)} brokerage + ${p(Fees.STOCK_LEVIES)} levies & taxes per trade (illustrative).",
    "Gold trades in tolas with a ${p(Fees.GOLD_SPREAD)} dealer spread each way (illustrative).",
    "T-bills: buy at a discount, receive Rs 1,000 per unit at maturity. Fee ${p(Fees.BILL_FEE)}. Selling early uses the current market yield.",
    "Not modelled yet: capital gains tax, dividends, settlement delays, short selling and margin trading.",
    "These fees and rules are simplified on purpose. Real PSX, broker and tax rules differ and change over time.",
)
