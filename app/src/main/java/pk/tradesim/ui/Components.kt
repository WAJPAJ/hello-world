package pk.tradesim.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

val Teal = Color(0xFF00897B)
val Amber = Color(0xFFFFA000)
val Gain = Color(0xFF2E7D32)
val Loss = Color(0xFFC62828)
val Muted = Color(0xFF6B7280)
val Soft = Color(0xFFF1F5F4)

val SimColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB2DFDB),
    secondary = Amber,
    secondaryContainer = Color(0xFFFFECB3),
    background = Color.White,
    surface = Color.White,
    surfaceVariant = Soft,
    surfaceContainer = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color.White,
)

@Composable
fun TradeSimTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = SimColors, content = content)

fun pnlColor(v: Double): Color = if (v >= 0) Gain else Loss

@Composable
fun SectionCard(title: String? = null, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Soft),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
            }
            content()
        }
    }
}

@Composable
fun LabelValue(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Muted, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = valueColor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End)
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Color.Unspecified) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Soft), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(label, color = Muted, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            Text(value, color = valueColor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = Muted)
    }
}

/** Minimal multi-series line chart. Optional dashed horizontal baseline. */
@Composable
fun LineChart(
    series: List<List<Double>>,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    baseline: Double? = null,
) {
    val all = series.flatten() + listOfNotNull(baseline)
    if (all.size < 2 || series.all { it.size < 2 }) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("Move time forward to draw the chart", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        return
    }
    val lo = all.min()
    val hi = all.max()
    val span = if (hi - lo < 1e-9) 1.0 else hi - lo
    Column(modifier) {
        Text(String.format(java.util.Locale.US, "%,.2f", hi), style = MaterialTheme.typography.labelSmall, color = Muted)
        Canvas(Modifier.fillMaxWidth().weight(1f).padding(vertical = 4.dp)) {
            fun y(v: Double) = (size.height * (1 - (v - lo) / span)).toFloat()
            if (baseline != null) {
                drawLine(Muted.copy(alpha = 0.5f), Offset(0f, y(baseline)), Offset(size.width, y(baseline)),
                    strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
            }
            series.forEachIndexed { si, pts ->
                if (pts.size < 2) return@forEachIndexed
                val step = size.width / (pts.size - 1)
                val path = Path()
                pts.forEachIndexed { i, v -> if (i == 0) path.moveTo(0f, y(v)) else path.lineTo(i * step, y(v)) }
                drawPath(path, colors[si % colors.size], style = Stroke(width = 5f))
            }
        }
        Text(String.format(java.util.Locale.US, "%,.2f", lo), style = MaterialTheme.typography.labelSmall, color = Muted)
    }
}

/** Horizontal stacked bar for allocation. */
@Composable
fun AllocationBar(parts: List<Pair<Double, Color>>, modifier: Modifier = Modifier) {
    val total = parts.sumOf { it.first }
    Row(modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(9.dp)).background(Color(0xFFE5E7EB))) {
        if (total > 0) parts.filter { it.first > 0 }.forEach { (v, c) ->
            Box(Modifier.weight((v / total).toFloat().coerceAtLeast(0.001f)).height(18.dp).background(c))
        }
    }
}
