package pk.tradesim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pk.tradesim.data.GameCodec
import pk.tradesim.data.MarketData
import pk.tradesim.engine.Engine
import pk.tradesim.engine.Fees
import pk.tradesim.engine.Side
import pk.tradesim.engine.TradeKind
import kotlin.math.abs

class EngineTest {
    private val md = MarketData(2030)

    @Test fun marketIsDeterministicAndSane() {
        val other = MarketData(2030)
        for (ins in md.tradables.filter { it.assetClass != pk.tradesim.data.AssetClass.TBILL }) {
            for (d in 0..md.lastDay) {
                val b = md.bar(ins.id, d)
                assertEquals(b, other.bar(ins.id, d))
                assertTrue("${ins.id} d$d", b.low <= b.open && b.low <= b.close && b.high >= b.open && b.high >= b.close && b.low > 0)
                if (d > 0) {
                    val prev = md.close(ins.id, d - 1)
                    assertTrue("circuit ${ins.id} d$d", abs(b.close / prev - 1) <= MarketData.CIRCUIT + 0.002)
                }
            }
        }
        assertTrue(MarketData(1).close("MGCL", 300) != md.close("MGCL", 300))
    }

    @Test fun buyAndSellChargeFeesAndTrackPnl() {
        var s = Engine.newGame(2030, 1_000_000.0, 0)
        val p0 = md.price("MGCL", 0)
        val out = Engine.buy(s, md, "MGCL", 1000)
        assertNull(out.error)
        s = out.state
        val cost = p0 * 1000 * (1 + Fees.rate(pk.tradesim.data.AssetClass.STOCK))
        assertEquals(1_000_000.0 - cost, s.cash, 0.01)
        assertEquals(1000L, s.holdings.single().qty)

        s = Engine.advance(s, md, 10)
        val p10 = md.close("MGCL", 10)
        s = Engine.sell(s, md, "MGCL", 1000).state
        val trade = s.trades.last()
        val expected = p10 * 1000 * (1 - Fees.rate(pk.tradesim.data.AssetClass.STOCK)) - cost
        assertEquals(expected, trade.realizedPnl!!, 0.01)
        assertTrue(s.holdings.isEmpty())
        assertEquals(11, s.equityCurve.size)
    }

    @Test fun cannotOverspendOrOversell() {
        val s = Engine.newGame(2030, 10_000.0, 0)
        assertNotNull(Engine.buy(s, md, "KRCM", 100).error)
        assertNotNull(Engine.sell(s, md, "KRCM", 1).error)
        val max = Engine.maxAffordable(md, s.cash, "KRCM", md.price("KRCM", 0))
        assertNull(Engine.buy(s, md, "KRCM", max).error)
        assertNotNull(Engine.buy(s, md, "KRCM", max + 1).error)
    }

    @Test fun tBillMaturesAtFace() {
        var s = Engine.newGame(2030, 1_000_000.0, 0)
        s = Engine.buy(s, md, "TB3M", 500).state
        assertTrue(md.price("TB3M", 0) < MarketData.BILL_FACE)
        s = Engine.advance(s, md, 63)
        assertTrue(s.holdings.isEmpty())
        val m = s.trades.last()
        assertEquals(TradeKind.MATURITY, m.kind)
        assertTrue("bill should earn interest", m.realizedPnl!! > 0)
    }

    @Test fun limitBuyFillsWhenPriceDips() {
        var s = Engine.newGame(2030, 1_000_000.0, 0)
        // find a day within 20 when low dips at least 1% under today's close
        val c0 = md.close("RVSY", 0)
        val minLow = (1..20).minOf { md.bar("RVSY", it).low }
        val limit = (c0 + minLow) / 2
        s = Engine.placeLimit(s, md, "RVSY", Side.BUY, 100, limit).state
        assertEquals(1, s.orders.size)
        s = Engine.advance(s, md, 20)
        assertTrue(s.orders.isEmpty())
        val fill = s.trades.single { it.kind == TradeKind.LIMIT }
        assertTrue(fill.price <= limit + 1e-9)
    }

    @Test fun saveAndLoadRoundTrip() {
        var s = Engine.newGame(77, 5_000_000.0, 130)
        s = Engine.buy(s, md, "GOLD", 3, "hedge").state
        s = Engine.placeLimit(s, md, "ICBL", Side.BUY, 100, 1.0).state
        s = Engine.advance(s, md, 5)
        assertEquals(s, GameCodec.decode(GameCodec.encode(s)))
    }
}
