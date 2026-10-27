package com.ivy.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList

private val ChartHeight = 180.dp
private const val BarShare = 0.6f
private const val FillAlpha = 0.18f
private val Dash = floatArrayOf(12f, 10f)

/** One line on a [LineChart]. */
@Immutable
data class ChartLine(
    val values: ImmutableList<Double>,
    val color: Color,
    val dashed: Boolean,
    val filled: Boolean,
)

/**
 * Bars side by side for each label, one per series: for example income and spending for each month.
 * All series have one value per label.
 */
@Composable
internal fun BarChart(
    series: ImmutableList<ImmutableList<Double>>,
    colors: ImmutableList<Color>,
    labels: ImmutableList<String>,
    modifier: Modifier = Modifier,
) {
    val max = series.flatten().maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val baseline = MaterialTheme.colorScheme.surfaceVariant
    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(ChartHeight),
        ) {
            if (labels.isEmpty()) return@Canvas
            val slot = size.width / labels.size
            val bar = slot * BarShare / series.size.coerceAtLeast(1)
            val start = (slot - bar * series.size) / 2
            val radius = CornerRadius(bar / 2, bar / 2)
            labels.indices.forEach { index ->
                series.forEachIndexed { s, values ->
                    val height = (values.getOrElse(index) { 0.0 } / max).toFloat() * size.height
                    drawRoundRect(
                        color = colors.getOrElse(s) { Gray },
                        topLeft = Offset(slot * index + start + bar * s, size.height - height),
                        size = Size(bar, height),
                        cornerRadius = radius,
                    )
                }
            }
            drawLine(baseline, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 2.dp.toPx())
        }
        Spacer(Modifier.height(8.dp))
        AxisLabels(labels, spread = false)
    }
}

/** Lines over the same days or months, the first one on top. */
@Composable
internal fun LineChart(
    lines: ImmutableList<ChartLine>,
    length: Int,
    labels: ImmutableList<String>,
    modifier: Modifier = Modifier,
) {
    val max = lines.flatMap { it.values }.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val min = lines.flatMap { it.values }.minOrNull()?.coerceAtMost(0.0) ?: 0.0
    val baseline = MaterialTheme.colorScheme.surfaceVariant
    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(ChartHeight),
        ) {
            drawLine(baseline, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 2.dp.toPx())
            lines.reversed().forEach { line -> drawChartLine(line, length, min, max) }
        }
        Spacer(Modifier.height(8.dp))
        AxisLabels(labels, spread = true)
    }
}

private fun DrawScope.drawChartLine(line: ChartLine, length: Int, min: Double, max: Double) {
    if (line.values.isEmpty()) return
    val step = if (length > 1) size.width / (length - 1) else size.width
    fun y(value: Double): Float = size.height - ((value - min) / (max - min)).toFloat() * size.height
    val path = Path()
    line.values.forEachIndexed { index, value ->
        if (index == 0) path.moveTo(0f, y(value)) else path.lineTo(step * index, y(value))
    }
    if (line.filled) {
        val fill = Path().apply {
            addPath(path)
            lineTo(step * (line.values.size - 1), size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(
            path = fill,
            brush = Brush.verticalGradient(listOf(line.color.copy(alpha = FillAlpha), Color.Transparent)),
        )
    }
    drawPath(
        path = path,
        color = line.color,
        style = Stroke(
            width = 3.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = if (line.dashed) PathEffect.dashPathEffect(Dash) else null,
        ),
    )
}

/**
 * Labels under a chart: centered under each bar, or [spread] from the left edge to the right edge
 * for the points of a line.
 */
@Composable
private fun AxisLabels(labels: ImmutableList<String>, spread: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (spread) Arrangement.SpaceBetween else Arrangement.Start,
    ) {
        labels.forEach { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = if (spread) Modifier else Modifier.weight(1f),
            )
        }
    }
}

/** What each color on a chart means. */
@Composable
internal fun Legend(
    items: ImmutableList<Pair<String, Color>>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { (name, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(color)
                Spacer(Modifier.width(6.dp))
                Text(text = name, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
