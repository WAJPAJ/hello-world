package pk.tradesim.data

import org.json.JSONArray
import org.json.JSONObject
import pk.tradesim.engine.GameState
import pk.tradesim.engine.Holding
import pk.tradesim.engine.LimitOrder
import pk.tradesim.engine.Side
import pk.tradesim.engine.Trade
import pk.tradesim.engine.TradeKind
import java.io.File

/** Saves the game as a small JSON file on the phone. Prices are not stored: they regenerate from the seed. */
class GameStore(dir: File) {
    private val file = File(dir, "game.json")

    fun load(): GameState? = try {
        if (file.exists()) GameCodec.decode(file.readText()) else null
    } catch (e: Exception) {
        null
    }

    fun save(state: GameState) {
        val tmp = File(file.parentFile, "game.json.tmp")
        tmp.writeText(GameCodec.encode(state))
        tmp.renameTo(file)
    }
}

object GameCodec {
    fun encode(s: GameState): String = JSONObject().apply {
        put("v", 1)
        put("seed", s.seed)
        put("startDay", s.startDay)
        put("currentDay", s.currentDay)
        put("startingCash", s.startingCash)
        put("cash", s.cash)
        put("nextId", s.nextId)
        put("holdings", JSONArray(s.holdings.map {
            JSONObject().put("key", it.key).put("ins", it.instrumentId).put("qty", it.qty)
                .put("avg", it.avgCost).put("buy", it.buyDay).put("mat", it.maturityDay)
        }))
        put("orders", JSONArray(s.orders.map {
            JSONObject().put("id", it.id).put("ins", it.instrumentId).put("side", it.side.name).put("qty", it.qty)
                .put("lim", it.limitPrice).put("placed", it.placedDay).put("exp", it.expiryDay).put("note", it.note)
        }))
        put("trades", JSONArray(s.trades.map {
            JSONObject().put("id", it.id).put("day", it.day).put("ins", it.instrumentId).put("side", it.side.name)
                .put("kind", it.kind.name).put("qty", it.qty).put("px", it.price).put("fees", it.fees)
                .put("pnl", it.realizedPnl ?: JSONObject.NULL).put("note", it.note)
        }))
        put("equity", JSONArray(s.equityCurve))
        put("events", JSONArray(s.events))
    }.toString()

    fun decode(text: String): GameState {
        val o = JSONObject(text)
        fun <T> arr(name: String, f: (JSONObject) -> T): List<T> {
            val a = o.getJSONArray(name)
            return (0 until a.length()).map { f(a.getJSONObject(it)) }
        }
        val eq = o.getJSONArray("equity")
        val ev = o.optJSONArray("events") ?: JSONArray()
        return GameState(
            seed = o.getLong("seed"),
            startDay = o.getInt("startDay"),
            currentDay = o.getInt("currentDay"),
            startingCash = o.getDouble("startingCash"),
            cash = o.getDouble("cash"),
            holdings = arr("holdings") {
                Holding(it.getString("key"), it.getString("ins"), it.getLong("qty"), it.getDouble("avg"),
                    it.getInt("buy"), it.getInt("mat"))
            },
            orders = arr("orders") {
                LimitOrder(it.getLong("id"), it.getString("ins"), Side.valueOf(it.getString("side")), it.getLong("qty"),
                    it.getDouble("lim"), it.getInt("placed"), it.getInt("exp"), it.optString("note"))
            },
            trades = arr("trades") {
                Trade(it.getLong("id"), it.getInt("day"), it.getString("ins"), Side.valueOf(it.getString("side")),
                    TradeKind.valueOf(it.getString("kind")), it.getLong("qty"), it.getDouble("px"), it.getDouble("fees"),
                    if (it.isNull("pnl")) null else it.getDouble("pnl"), it.optString("note"))
            },
            equityCurve = (0 until eq.length()).map { eq.getDouble(it) },
            nextId = o.getLong("nextId"),
            events = (0 until ev.length()).map { ev.getString(it) },
        )
    }
}
