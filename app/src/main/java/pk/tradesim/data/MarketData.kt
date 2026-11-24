package pk.tradesim.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Random
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

enum class AssetClass(val label: String) {
    STOCK("Stocks"), GOLD("Gold"), TBILL("T-Bills"), INDEX("Index")
}

data class Instrument(
    val id: String,
    val name: String,
    val assetClass: AssetClass,
    val sector: String,
    val startPrice: Double = 0.0,
    val beta: Double = 1.0,
    val vol: Double = 0.015,
    val tenorTradingDays: Int = 0,
    val yieldSpread: Double = 0.0,
) {
    val unitLabel: String
        get() = when (assetClass) {
            AssetClass.STOCK -> "per share"
            AssetClass.GOLD -> "per tola"
            AssetClass.TBILL -> "per Rs 1,000 face value"
            AssetClass.INDEX -> "points"
        }
}

data class Bar(val open: Double, val high: Double, val low: Double, val close: Double)

/** A market phase. Stocks drift and swing according to it; the policy rate glides from rateFrom to rateTo. */
data class Regime(
    val from: Int, val to: Int,
    val drift: Double, val vol: Double,
    val rateFrom: Double, val rateTo: Double,
    val stormy: Boolean = false,
)

data class Scenario(val name: String, val description: String, val startDay: Int)

/**
 * Fully simulated ("dummy") market. Every company, price and rate is fictional and is generated
 * deterministically from [seed], so two phones with the same seed see exactly the same history.
 */
class MarketData(val seed: Long) {

    companion object {
        const val DAYS = 756                // ~3 years of trading days
        const val INDEX_ID = "SIM100"
        const val GOLD_ID = "GOLD"
        const val CIRCUIT = 0.075           // simulated daily price limit (+/- 7.5%)
        const val BILL_FACE = 1000.0        // face value of one T-bill unit
        val START_DATE: LocalDate = LocalDate.of(2030, 1, 1)

        val STOCKS = listOf(
            Instrument("ICBL", "Indus Crest Bank", AssetClass.STOCK, "Banking", 120.0, 0.9, 0.012),
            Instrument("SFNB", "Saffron National Bank", AssetClass.STOCK, "Banking", 46.0, 1.0, 0.014),
            Instrument("MGCL", "Margalla Cement", AssetClass.STOCK, "Cement", 85.0, 1.4, 0.018),
            Instrument("KRCM", "Kirthar Cement", AssetClass.STOCK, "Cement", 610.0, 1.3, 0.016),
            Instrument("POGC", "Potohar Oil & Gas", AssetClass.STOCK, "Oil & Gas", 115.0, 1.1, 0.015),
            Instrument("GCRL", "Gwadar Coast Refinery", AssetClass.STOCK, "Oil & Gas", 22.0, 1.6, 0.024),
            Instrument("SJFL", "Sutlej Fertilizer", AssetClass.STOCK, "Fertilizer", 140.0, 0.6, 0.010),
            Instrument("TBPC", "Thar Bright Power", AssetClass.STOCK, "Power", 32.0, 0.7, 0.012),
            Instrument("RVSY", "Ravi Systems", AssetClass.STOCK, "Technology", 55.0, 1.5, 0.022),
            Instrument("CLTX", "Chenab Loom Textiles", AssetClass.STOCK, "Textile", 18.0, 1.2, 0.020),
            Instrument("KKMT", "Karakoram Motors", AssetClass.STOCK, "Automobile", 380.0, 1.3, 0.017),
            Instrument("HLPH", "Hunza Life Pharma", AssetClass.STOCK, "Pharma", 210.0, 0.8, 0.013),
        )
        val GOLD = Instrument(GOLD_ID, "Gold", AssetClass.GOLD, "Commodity", 180_000.0, -0.2, 0.007)
        val INDEX = Instrument(INDEX_ID, "SIM-100 Index", AssetClass.INDEX, "Index", 50_000.0)
        val BILLS = listOf(
            Instrument("TB3M", "3-Month T-Bill", AssetClass.TBILL, "Government", tenorTradingDays = 63, yieldSpread = -0.30),
            Instrument("TB6M", "6-Month T-Bill", AssetClass.TBILL, "Government", tenorTradingDays = 126, yieldSpread = -0.15),
            Instrument("TB12M", "12-Month T-Bill", AssetClass.TBILL, "Government", tenorTradingDays = 252, yieldSpread = 0.0),
        )

        val REGIMES = listOf(
            Regime(0, 149, 0.0007, 0.009, 11.0, 12.0),
            Regime(150, 299, -0.0024, 0.017, 12.0, 22.0, stormy = true),
            Regime(300, 449, 0.0001, 0.011, 22.0, 21.0),
            Regime(450, 649, 0.0024, 0.012, 21.0, 13.0),
            Regime(650, DAYS - 1, 0.0002, 0.010, 13.0, 12.0),
        )

        val SCENARIOS = listOf(
            Scenario("Full journey", "All ~3 simulated years, from day one.", 0),
            Scenario("Turbulence ahead", "Start a few weeks before conditions change sharply.", 130),
            Scenario("After the fall", "Start after a long slump. What comes next?", 430),
        )

        private fun round2(v: Double) = (v * 100).roundToLong() / 100.0
    }

    val lastDay = DAYS - 1
    val tradables: List<Instrument> = STOCKS + GOLD + BILLS
    private val byId = (tradables + INDEX).associateBy { it.id }
    private val bars = HashMap<String, Array<Bar>>()
    private val yields = HashMap<String, DoubleArray>()
    val policyRate = DoubleArray(DAYS)
    val dates: List<LocalDate>

    init {
        val d = ArrayList<LocalDate>(DAYS)
        var date = START_DATE
        while (d.size < DAYS) {
            if (date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY) d.add(date)
            date = date.plusDays(1)
        }
        dates = d
        generate()
    }

    fun instrument(id: String): Instrument = byId.getValue(id)
    fun bar(id: String, day: Int): Bar = bars.getValue(id)[day]
    fun close(id: String, day: Int): Double = bar(id, day).close
    fun yieldPct(id: String, day: Int): Double = yields.getValue(id)[day]

    /** Price of one T-bill unit with [remainingTradingDays] to maturity at [yieldPct] (simple discount). */
    fun billPricePerUnit(yieldPct: Double, remainingTradingDays: Int): Double {
        val calendarDays = remainingTradingDays * 365.0 / 252.0
        return BILL_FACE / (1 + yieldPct / 100.0 * calendarDays / 365.0)
    }

    /** Price you pay/see today: close for stocks & gold, fresh-issue price for T-bills. */
    fun price(id: String, day: Int): Double {
        val ins = instrument(id)
        return if (ins.assetClass == AssetClass.TBILL) billPricePerUnit(yieldPct(id, day), ins.tenorTradingDays)
        else close(id, day)
    }

    /** Day-on-day % change (for T-bills: change in yield, in percentage points). */
    fun change(id: String, day: Int): Double {
        val ins = instrument(id)
        if (ins.assetClass == AssetClass.TBILL) {
            return if (day == 0) 0.0 else yieldPct(id, day) - yieldPct(id, day - 1)
        }
        val prev = if (day == 0) bar(id, 0).open else close(id, day - 1)
        return (close(id, day) / prev - 1) * 100
    }

    private fun makeBar(p0: Double, rIn: Double, vol: Double, rnd: Random): Bar {
        val r = rIn.coerceIn(-CIRCUIT, CIRCUIT)
        val close = round2(max(0.5, p0 * (1 + r)))
        val open = round2(p0 * (1 + (0.35 * r + 0.003 * rnd.nextGaussian()).coerceIn(-CIRCUIT, CIRCUIT)))
        var hi = round2(min(max(open, close) * (1 + abs(rnd.nextGaussian()) * vol * 0.4), p0 * (1 + CIRCUIT)))
        var lo = round2(max(min(open, close) * (1 - abs(rnd.nextGaussian()) * vol * 0.4), p0 * (1 - CIRCUIT)))
        hi = max(hi, max(open, close))
        lo = min(lo, min(open, close))
        return Bar(open, hi, lo, close)
    }

    private fun generate() {
        val rnd = Random(seed)
        val sectors = STOCKS.map { it.sector }.distinct()
        val stockBars = STOCKS.map { ArrayList<Bar>(DAYS) }
        val prev = DoubleArray(STOCKS.size) { STOCKS[it].startPrice }
        val indexBars = ArrayList<Bar>(DAYS)
        val goldBars = ArrayList<Bar>(DAYS)
        var idx = INDEX.startPrice
        var gold = GOLD.startPrice
        val billNoise = DoubleArray(BILLS.size)
        BILLS.forEach { yields[it.id] = DoubleArray(DAYS) }

        // Market-wide daily shocks. Within each phase we remove most of their average, so each phase keeps
        // realistic day-to-day swings but its overall direction follows the phase (calm, storm, boom...).
        val z = DoubleArray(DAYS) { rnd.nextGaussian() }
        for (g in REGIMES) {
            val mean = (g.from..g.to).sumOf { z[it] } / (g.to - g.from + 1)
            for (d in g.from..g.to) z[d] -= 0.8 * mean
        }

        for (d in 0 until DAYS) {
            val reg = REGIMES.first { d in it.from..it.to }
            val shock = reg.vol * z[d]
            val m = reg.drift + shock
            val sectorShock = sectors.associateWith { rnd.nextGaussian() * 0.005 }

            var sumR = 0.0
            STOCKS.forEachIndexed { i, ins ->
                val r = ins.beta * m + sectorShock.getValue(ins.sector) + ins.vol * rnd.nextGaussian()
                val p0 = prev[i]
                val b = makeBar(p0, r, ins.vol, rnd)
                stockBars[i].add(b)
                sumR += b.close / p0 - 1
                prev[i] = b.close
            }
            val idxNew = idx * (1 + sumR / STOCKS.size)
            indexBars.add(Bar(round2(idx), round2(max(idx, idxNew)), round2(min(idx, idxNew)), round2(idxNew)))
            idx = idxNew

            val gr = 0.00035 + (if (reg.stormy) 0.0008 else 0.0) + GOLD.beta * shock + GOLD.vol * rnd.nextGaussian()
            val gb = makeBar(gold, gr, GOLD.vol, rnd)
            goldBars.add(gb)
            gold = gb.close

            val t = (d - reg.from).toDouble() / max(1, reg.to - reg.from)
            policyRate[d] = reg.rateFrom + (reg.rateTo - reg.rateFrom) * t
            BILLS.forEachIndexed { j, b ->
                billNoise[j] = 0.92 * billNoise[j] + 0.06 * rnd.nextGaussian()
                yields.getValue(b.id)[d] = round2(policyRate[d] + b.yieldSpread + billNoise[j])
            }
        }
        STOCKS.forEachIndexed { i, ins -> bars[ins.id] = stockBars[i].toTypedArray() }
        bars[INDEX_ID] = indexBars.toTypedArray()
        bars[GOLD_ID] = goldBars.toTypedArray()
    }
}
