package pk.tradesim.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

fun rs(v: Double): String = (if (v < -0.5) "-" else "") + "Rs " + String.format(Locale.US, "%,.0f", abs(v))
fun signedRs(v: Double): String = (if (v >= 0) "+" else "-") + "Rs " + String.format(Locale.US, "%,.0f", abs(v))
fun px(v: Double): String = String.format(Locale.US, "%,.2f", v)
fun pct(v: Double): String = String.format(Locale.US, "%+.2f%%", v)
fun qty(v: Long): String = String.format(Locale.US, "%,d", v)

private val dateFmt = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.US)
private val shortFmt = DateTimeFormatter.ofPattern("d MMM yy", Locale.US)
fun dateLong(d: LocalDate): String = d.format(dateFmt)
fun dateShort(d: LocalDate): String = d.format(shortFmt)
