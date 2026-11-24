package pk.tradesim.engine

import pk.tradesim.data.AssetClass
import pk.tradesim.data.MarketData
import pk.tradesim.util.px
import pk.tradesim.util.qty
import pk.tradesim.util.rs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

enum class Side { BUY, SELL }
enum class TradeKind { MARKET, LIMIT, MATURITY }

/** A position. Stocks/gold are keyed by symbol; each T-bill purchase is its own lot ("TB3M#120"). */
data class Holding(
    val key: String,
    val instrumentId: String,
    val qty: Long,
    val avgCost: Double,        // per unit, including fees
    val buyDay: Int,
    val maturityDay: Int = -1,  // T-bills only
)

data class LimitOrder(
    val id: Long,
    val instrumentId: String,
    val side: Side,
    val qty: Long,
    val limitPrice: Double,
    val placedDay: Int,
    val expiryDay: Int,
    val note: String,
)

data class Trade(
    val id: Long,
    val day: Int,
    val instrumentId: String,
    val side: Side,
    val kind: TradeKind,
    val qty: Long,
    val price: Double,
    val fees: Double,
    val realizedPnl: Double?,
    val note: String,
)

data class GameState(
    val seed: Long,
    val startDay: Int,
    val currentDay: Int,
    val startingCash: Double,
    val cash: Double,
    val holdings: List<Holding>,
    val orders: List<LimitOrder>,
    val trades: List<Trade>,
    val equityCurve: List<Double>,  // net worth per day, index 0 = startDay
    val nextId: Long,
    val events: List<String>,       // what happened during the last time jump
)

data class Quote(val price: Double, val qty: Long, val gross: Double, val fees: Double, val net: Double)

data class Outcome(val state: GameState, val error: String? = null)

data class Stats(
    val netWorth: Double,
    val totalReturnPct: Double,
    val indexReturnPct: Double,
    val maxDrawdownPct: Double,
    val winRatePct: Double?,
    val closedTrades: Int,
    val totalTrades: Int,
    val feesPaid: Double,
    val realizedPnl: Double,
    val unrealizedPnl: Double,
    val bestTrade: Trade?,
    val worstTrade: Trade?,
)

/** Simulated trading costs. Illustrative only, not actual PSX / broker / tax rates. */
object Fees {
    const val STOCK_COMMISSION = 0.0015  // 0.15% brokerage
    const val STOCK_LEVIES = 0.0003      // 0.03% exchange/regulatory levies & taxes (approx.)
    const val GOLD_SPREAD = 0.005        // 0.5% dealer spread each way
    const val BILL_FEE = 0.0005          // 0.05% bank/custody fee

    fun rate(ac: AssetClass): Double = when (ac) {
        AssetClass.STOCK -> STOCK_COMMISSION + STOCK_LEVIES
        AssetClass.GOLD -> GOLD_SPREAD
        AssetClass.TBILL -> BILL_FEE
        AssetClass.INDEX -> 0.0
    }
}

object Engine {
    const val ORDER_LIFETIME_DAYS = 20

    fun newGame(seed: Long, startingCash: Double, startDay: Int) = GameState(
        seed = seed, startDay = startDay, currentDay = startDay,
        startingCash = startingCash, cash = startingCash,
        holdings = emptyList(), orders = emptyList(), trades = emptyList(),
        equityCurve = listOf(startingCash), nextId = 1,
        events = listOf("Simulation started with ${rs(startingCash)} in cash."),
    )

    fun quote(md: MarketData, instrumentId: String, side: Side, qty: Long, price: Double): Quote {
        val gross = price * qty
        val fees = gross * Fees.rate(md.instrument(instrumentId).assetClass)
        return Quote(price, qty, gross, fees, if (side == Side.BUY) gross + fees else gross - fees)
    }

    fun maxAffordable(md: MarketData, cash: Double, instrumentId: String, price: Double): Long {
        if (price <= 0) return 0
        var n = floor(cash / (price * (1 + Fees.rate(md.instrument(instrumentId).assetClass)))).toLong()
        while (n > 0 && quote(md, instrumentId, Side.BUY, n, price).net > cash) n--
        return max(0, n)
    }

    fun reservedSells(state: GameState, instrumentId: String): Long =
        state.orders.filter { it.side == Side.SELL && it.instrumentId == instrumentId }.sumOf { it.qty }

    fun holdingPrice(md: MarketData, h: Holding, day: Int): Double {
        val ins = md.instrument(h.instrumentId)
        return if (ins.assetClass == AssetClass.TBILL) {
            if (day >= h.maturityDay) MarketData.BILL_FACE
            else md.billPricePerUnit(md.yieldPct(h.instrumentId, day), h.maturityDay - day)
        } else md.close(h.instrumentId, day)
    }

    fun netWorth(state: GameState, md: MarketData, day: Int = state.currentDay): Double =
        state.cash + state.holdings.sumOf { it.qty * holdingPrice(md, it, day) }

    // ---- Orders -------------------------------------------------------------------------------

    fun buy(state: GameState, md: MarketData, instrumentId: String, qty: Long, note: String = ""): Outcome =
        executeBuy(state, md, instrumentId, qty, md.price(instrumentId, state.currentDay), TradeKind.MARKET, note, state.currentDay)

    fun sell(state: GameState, md: MarketData, holdingKey: String, qty: Long, note: String = ""): Outcome {
        val h = state.holdings.find { it.key == holdingKey } ?: return Outcome(state, "You don't own this.")
        return executeSell(state, md, holdingKey, qty, holdingPrice(md, h, state.currentDay), TradeKind.MARKET, note, state.currentDay)
    }

    fun placeLimit(
        state: GameState, md: MarketData, instrumentId: String, side: Side,
        qty: Long, limitPrice: Double, note: String = "",
    ): Outcome {
        val ins = md.instrument(instrumentId)
        if (ins.assetClass != AssetClass.STOCK && ins.assetClass != AssetClass.GOLD)
            return Outcome(state, "Limit orders are available for stocks and gold only.")
        if (qty <= 0) return Outcome(state, "Enter a quantity above zero.")
        if (limitPrice <= 0) return Outcome(state, "Enter a valid limit price.")
        if (side == Side.SELL) {
            val owned = state.holdings.find { it.key == instrumentId }?.qty ?: 0
            val available = owned - reservedSells(state, instrumentId)
            if (qty > available) return Outcome(state, "You can sell at most ${qty(available)} (others are already in open sell orders).")
        } else {
            val need = quote(md, instrumentId, Side.BUY, qty, limitPrice).net
            if (need > state.cash) return Outcome(state, "Not enough cash: this order needs about ${rs(need)}.")
        }
        val order = LimitOrder(
            state.nextId, instrumentId, side, qty, limitPrice,
            state.currentDay, state.currentDay + ORDER_LIFETIME_DAYS, note,
        )
        return Outcome(state.copy(orders = state.orders + order, nextId = state.nextId + 1))
    }

    fun cancelOrder(state: GameState, orderId: Long): GameState =
        state.copy(orders = state.orders.filterNot { it.id == orderId })

    private fun executeBuy(
        state: GameState, md: MarketData, instrumentId: String, qty: Long,
        price: Double, kind: TradeKind, note: String, day: Int,
    ): Outcome {
        if (qty <= 0) return Outcome(state, "Enter a quantity above zero.")
        val ins = md.instrument(instrumentId)
        if (ins.assetClass == AssetClass.INDEX) return Outcome(state, "The index can't be traded directly.")
        val q = quote(md, instrumentId, Side.BUY, qty, price)
        if (q.net > state.cash + 1e-6) return Outcome(state, "Not enough cash: need ${rs(q.net)}, you have ${rs(state.cash)}.")

        val isBill = ins.assetClass == AssetClass.TBILL
        val key = if (isBill) "$instrumentId#$day" else instrumentId
        val holdings = state.holdings.toMutableList()
        val i = holdings.indexOfFirst { it.key == key }
        if (i >= 0) {
            val old = holdings[i]
            val newQty = old.qty + qty
            holdings[i] = old.copy(qty = newQty, avgCost = (old.avgCost * old.qty + q.net) / newQty)
        } else {
            holdings += Holding(
                key, instrumentId, qty, q.net / qty, day,
                if (isBill) day + ins.tenorTradingDays else -1,
            )
        }
        val trade = Trade(state.nextId, day, instrumentId, Side.BUY, kind, qty, price, q.fees, null, note)
        return Outcome(state.copy(
            cash = state.cash - q.net, holdings = holdings,
            trades = state.trades + trade, nextId = state.nextId + 1,
        ))
    }

    private fun executeSell(
        state: GameState, md: MarketData, holdingKey: String, qty: Long,
        price: Double, kind: TradeKind, note: String, day: Int,
    ): Outcome {
        val h = state.holdings.find { it.key == holdingKey } ?: return Outcome(state, "You don't own this.")
        if (qty <= 0) return Outcome(state, "Enter a quantity above zero.")
        val reserved = if (kind == TradeKind.MARKET && h.maturityDay < 0) reservedSells(state, h.instrumentId) else 0
        val available = h.qty - reserved
        if (qty > available) {
            val extra = if (reserved > 0) " (${qty(reserved)} are reserved by open sell orders)" else ""
            return Outcome(state, "You can sell at most ${qty(available)}$extra.")
        }
        val q = quote(md, h.instrumentId, Side.SELL, qty, price)
        val realized = q.net - h.avgCost * qty
        val holdings = state.holdings.mapNotNull {
            if (it.key != holdingKey) it else if (it.qty == qty) null else it.copy(qty = it.qty - qty)
        }
        val trade = Trade(state.nextId, day, h.instrumentId, Side.SELL, kind, qty, price, q.fees, realized, note)
        return Outcome(state.copy(
            cash = state.cash + q.net, holdings = holdings,
            trades = state.trades + trade, nextId = state.nextId + 1,
        ))
    }

    // ---- Time machine -------------------------------------------------------------------------

    /** Move forward [days] trading days: mature T-bills, fill/expire limit orders, record net worth. */
    fun advance(state: GameState, md: MarketData, days: Int): GameState {
        var s = state
        val events = mutableListOf<String>()
        val target = min(state.currentDay + days, md.lastDay)
        for (d in state.currentDay + 1..target) {
            s = s.copy(currentDay = d)

            for (h in s.holdings.filter { it.maturityDay in 0..d }) {
                val proceeds = h.qty * MarketData.BILL_FACE
                val trade = Trade(
                    s.nextId, d, h.instrumentId, Side.SELL, TradeKind.MATURITY, h.qty,
                    MarketData.BILL_FACE, 0.0, proceeds - h.avgCost * h.qty, "Held to maturity",
                )
                s = s.copy(
                    cash = s.cash + proceeds, holdings = s.holdings - h,
                    trades = s.trades + trade, nextId = s.nextId + 1,
                )
                events += "${md.instrument(h.instrumentId).name} matured: ${rs(proceeds)} paid to cash."
            }

            val remaining = mutableListOf<LimitOrder>()
            for (o in s.orders.sortedBy { it.id }) {
                val bar = md.bar(o.instrumentId, d)
                val hit = if (o.side == Side.BUY) bar.low <= o.limitPrice else bar.high >= o.limitPrice
                if (hit) {
                    val fill = if (o.side == Side.BUY) min(bar.open, o.limitPrice) else max(bar.open, o.limitPrice)
                    val out = if (o.side == Side.BUY)
                        executeBuy(s, md, o.instrumentId, o.qty, fill, TradeKind.LIMIT, o.note, d)
                    else executeSell(s, md, o.instrumentId, o.qty, fill, TradeKind.LIMIT, o.note, d)
                    if (out.error == null) {
                        s = out.state
                        events += "Limit ${o.side.name.lowercase()} filled: ${qty(o.qty)} ${o.instrumentId} @ ${px(fill)}."
                    } else {
                        events += "Limit order #${o.id} (${o.instrumentId}) cancelled: ${out.error}"
                    }
                } else if (d >= o.expiryDay) {
                    events += "Limit order #${o.id} (${o.instrumentId}) expired unfilled."
                } else {
                    remaining += o
                }
            }
            s = s.copy(orders = remaining)
            s = s.copy(equityCurve = s.equityCurve + netWorth(s, md, d))
        }
        return s.copy(events = events.takeLast(30))
    }

    // ---- Analytics ----------------------------------------------------------------------------

    fun stats(state: GameState, md: MarketData): Stats {
        val nw = netWorth(state, md)
        var peak = Double.MIN_VALUE
        var maxDd = 0.0
        for (v in state.equityCurve) {
            peak = max(peak, v)
            maxDd = max(maxDd, (peak - v) / peak * 100)
        }
        val closed = state.trades.filter { it.side == Side.SELL && it.kind != TradeKind.MATURITY && it.realizedPnl != null }
        val unrealized = state.holdings.sumOf { it.qty * holdingPrice(md, it, state.currentDay) - it.qty * it.avgCost }
        return Stats(
            netWorth = nw,
            totalReturnPct = (nw / state.startingCash - 1) * 100,
            indexReturnPct = (md.close(MarketData.INDEX_ID, state.currentDay) / md.close(MarketData.INDEX_ID, state.startDay) - 1) * 100,
            maxDrawdownPct = maxDd,
            winRatePct = if (closed.isEmpty()) null else closed.count { it.realizedPnl!! > 0 } * 100.0 / closed.size,
            closedTrades = closed.size,
            totalTrades = state.trades.count { it.kind != TradeKind.MATURITY },
            feesPaid = state.trades.sumOf { it.fees },
            realizedPnl = state.trades.sumOf { it.realizedPnl ?: 0.0 },
            unrealizedPnl = unrealized,
            bestTrade = closed.maxByOrNull { it.realizedPnl!! },
            worstTrade = closed.minByOrNull { it.realizedPnl!! },
        )
    }
}
