package pk.tradesim.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import pk.tradesim.data.GameStore
import pk.tradesim.data.MarketData
import pk.tradesim.engine.Engine
import pk.tradesim.engine.GameState
import pk.tradesim.engine.Side
import pk.tradesim.util.qty

data class TradeRequest(val instrumentId: String, val side: Side, val holdingKey: String? = null)
data class TradeInput(val qty: Long, val limitPrice: Double?, val note: String)

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val store = GameStore(app.filesDir)

    var state by mutableStateOf<GameState?>(null)
        private set
    var market by mutableStateOf<MarketData?>(null)
        private set
    var message by mutableStateOf<String?>(null)

    init {
        store.load()?.let {
            market = MarketData(it.seed)
            state = it
        }
    }

    private fun update(s: GameState) {
        state = s
        store.save(s)
    }

    fun newGame(seed: Long, cash: Double, startDay: Int) {
        val md = market?.takeIf { it.seed == seed } ?: MarketData(seed)
        market = md
        update(Engine.newGame(seed, cash, startDay))
    }

    fun advance(days: Int) {
        val s = state ?: return
        val md = market ?: return
        if (s.currentDay >= md.lastDay) return
        val ns = Engine.advance(s, md, days)
        update(ns)
        if (ns.events.isNotEmpty()) {
            message = ns.events.last() + if (ns.events.size > 1) "  (+${ns.events.size - 1} more on Home)" else ""
        }
    }

    /** Returns an error message, or null on success. */
    fun trade(req: TradeRequest, input: TradeInput): String? {
        val s = state ?: return "No game in progress."
        val md = market ?: return "No market loaded."
        val out = when {
            input.limitPrice != null ->
                Engine.placeLimit(s, md, req.instrumentId, req.side, input.qty, input.limitPrice, input.note)
            req.side == Side.BUY -> Engine.buy(s, md, req.instrumentId, input.qty, input.note)
            else -> Engine.sell(s, md, req.holdingKey ?: req.instrumentId, input.qty, input.note)
        }
        if (out.error != null) return out.error
        update(out.state)
        message = if (input.limitPrice != null) "Limit order placed (good for ${Engine.ORDER_LIFETIME_DAYS} trading days)."
        else "${if (req.side == Side.BUY) "Bought" else "Sold"} ${qty(input.qty)} ${req.instrumentId}."
        return null
    }

    fun cancelOrder(id: Long) {
        val s = state ?: return
        update(Engine.cancelOrder(s, id))
        message = "Order #$id cancelled."
    }
}
