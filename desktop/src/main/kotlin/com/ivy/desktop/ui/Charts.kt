package com.ivy.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.ivy.desktop.reports.Axis
import com.ivy.desktop.reports.AxisScale
import com.ivy.desktop.reports.Format
import kotlinx.collections.immutable.ImmutableList
import kotlin.math.roundToInt

private val PlotHeight = 180.dp

/** Room on the left for the amounts */
private val AxisWidth = 48.dp

/** Room below the plot for the months or days */
private val LabelsHeight = 24.dp

/** Above the plot, so the top amount isn't cut in half */
private val TopRoom = 8.dp
private val TipGap = 16.dp
private val TipWidth = 280.dp
private val TipShape = RoundedCornerShape(12.dp)
private const val BarShare = 0.6f

/** However wide the window, a bar stays a bar */
private val MaxBarWidth = 24.dp
private val BarGap = 4.dp
private const val MaxGapShare = 0.08f
private val BarCorner = 4.dp
private const val FillAlpha = 0.18f

/** Half a slot: the middle of a bar */
private const val Half = 0.5f
private val Dash = floatArrayOf(12f, 10f)

/** One line on a [LineChart]. */
@Immutable
data class ChartLine(
    val values: ImmutableList<Double>,
    val color: Color,
    val dashed: Boolean,
    val filled: Boolean,
)

/** What a chart says about the month or day under the mouse. */
@Immutable
data class ChartTip(
    val title: String,
    val rows: ImmutableList<TipRow>,
)

/** A line of a [ChartTip]: a dot in the series' color ([Color.Unspecified] for none), a name and an amount. */
@Immutable
data class TipRow(
    val label: String,
    val value: String,
    val color: Color,
)

/**
 * Bars side by side for each label, one per series: for example income and spending for each month.
 * All series have one value per label, and [tips] has what to show for each label.
 */
@Composable
internal fun BarChart(
    series: ImmutableList<ImmutableList<Double>>,
    colors: ImmutableList<Color>,
    labels: ImmutableList<String>,
    tips: ImmutableList<ChartTip>,
    modifier: Modifier = Modifier,
) {
    val scale = remember(series) { Axis.scale(0.0, series.flatten().maxOrNull() ?: 0.0) }
    ChartFrame(
        scale = scale,
        count = labels.size,
        bars = true,
        labels = labels,
        tips = tips,
        modifier = modifier,
    ) { area, _ ->
        val slot = area.width / labels.size
        val count = series.size.coerceAtLeast(1)
        val gap = if (count > 1) minOf(BarGap.toPx(), slot * MaxGapShare) else 0f
        val bar = ((slot * BarShare - gap * (count - 1)) / count).coerceIn(1f, MaxBarWidth.toPx())
        val group = bar * count + gap * (count - 1)
        val corner = minOf(BarCorner.toPx(), bar / 2)
        labels.indices.forEach { index ->
            val left = area.left + slot * index + (slot - group) / 2
            series.forEachIndexed { s, values ->
                val x = left + (bar + gap) * s
                val top = yOf(values.getOrElse(index) { 0.0 }, scale, area)
                drawBar(colors.getOrElse(s) { Gray }, Rect(x, top, x + bar, area.bottom), corner)
            }
        }
    }
}

/** A bar standing on the bottom line: square at the bottom, a little rounded at the top. */
private fun DrawScope.drawBar(color: Color, rect: Rect, corner: Float) {
    if (rect.height <= 0f) return
    val radius = CornerRadius(minOf(corner, rect.height))
    val shape = RoundRect(
        rect = rect,
        topLeft = radius,
        topRight = radius,
        bottomRight = CornerRadius.Zero,
        bottomLeft = CornerRadius.Zero,
    )
    drawPath(Path().apply { addRoundRect(shape) }, color)
}

/**
 * Lines over the same days or months, the first one on top. [labels] has one label per point, empty
 * for the points that don't get one, and [tips] what to show for each point.
 */
@Composable
internal fun LineChart(
    lines: ImmutableList<ChartLine>,
    length: Int,
    labels: ImmutableList<String>,
    tips: ImmutableList<ChartTip>,
    modifier: Modifier = Modifier,
) {
    val scale = remember(lines) {
        val values = lines.flatMap { it.values }
        Axis.scale(values.minOrNull() ?: 0.0, values.maxOrNull() ?: 0.0)
    }
    val ring = MaterialTheme.colorScheme.surface
    ChartFrame(
        scale = scale,
        count = length,
        bars = false,
        labels = labels,
        tips = tips,
        modifier = modifier,
    ) { area, hovered ->
        lines.reversed().forEach { line -> drawChartLine(line, length, scale, area) }
        if (hovered != null) {
            lines.forEach { line ->
                line.values.getOrNull(hovered)?.let { value ->
                    val center = Offset(xOf(hovered, area, length, bars = false), yOf(value, scale, area))
                    drawCircle(ring, radius = 6.dp.toPx(), center = center)
                    drawCircle(line.color, radius = 4.dp.toPx(), center = center)
                }
            }
        }
    }
}

/**
 * What both charts have: amounts and lines across on the left, labels below, and a tip next to the
 * mouse. [bars] puts each value in the middle of its own slot; otherwise the points go from the left
 * edge to the right edge.
 */
@Composable
private fun ChartFrame(
    scale: AxisScale,
    count: Int,
    bars: Boolean,
    labels: ImmutableList<String>,
    tips: ImmutableList<ChartTip>,
    modifier: Modifier = Modifier,
    plot: DrawScope.(area: Rect, hovered: Int?) -> Unit,
) {
    val measurer = rememberTextMeasurer()
    val textStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val lineColor = MaterialTheme.colorScheme.surfaceVariant
    var hovered by remember { mutableStateOf<Int?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    val shown = hovered?.takeIf { it < count }
    Layout(
        modifier = modifier
            .fillMaxWidth()
            .height(PlotHeight + LabelsHeight + TopRoom),
        content = {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(count, bars) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val position = event.changes.firstOrNull()?.position ?: continue
                                pointer = position
                                hovered = if (event.type == PointerEventType.Exit) {
                                    null
                                } else {
                                    val area = plotArea(size.width.toFloat(), size.height.toFloat())
                                    indexAt(position.x, area, count, bars)
                                }
                            }
                        }
                    },
            ) {
                val area = plotArea(size.width, size.height)
                drawAxis(scale, area, measurer, textStyle, lineColor)
                if (shown != null) drawHover(shown, area, count, bars, lineColor)
                plot(area, shown)
                drawLabels(labels, area, count, bars, measurer, textStyle)
            }
            shown?.let(tips::getOrNull)?.let { tip -> ChartTooltip(tip) }
        },
    ) { measurables, constraints ->
        val chart = measurables[0].measure(constraints)
        val tip = measurables.getOrNull(1)?.measure(Constraints(maxWidth = TipWidth.roundToPx()))
        layout(chart.width, chart.height) {
            chart.place(0, 0)
            if (tip != null) {
                // beside the mouse, on the left when there's no room on the right
                val gap = TipGap.roundToPx()
                val x = pointer.x.roundToInt()
                val left = if (x + gap + tip.width <= chart.width) x + gap else x - gap - tip.width
                val top = (pointer.y.roundToInt() - tip.height / 2)
                    .coerceIn(0, (chart.height - tip.height).coerceAtLeast(0))
                tip.place(left.coerceAtLeast(0), top)
            }
        }
    }
}

@Composable
private fun ChartTooltip(tip: ChartTip) {
    Column(
        modifier = Modifier
            .width(IntrinsicSize.Max)
            .widthIn(max = TipWidth)
            .shadow(elevation = 8.dp, shape = TipShape)
            .background(MaterialTheme.colorScheme.surfaceVariant, TipShape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = tip.title, style = MaterialTheme.typography.labelLarge)
        tip.rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (row.color != Color.Unspecified) {
                    Dot(row.color)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = row.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 16.dp),
                )
                Text(text = row.value, style = NumberStyle.merge(MaterialTheme.typography.bodySmall))
            }
        }
    }
}

private fun Density.plotArea(width: Float, height: Float): Rect = Rect(
    left = AxisWidth.toPx(),
    top = TopRoom.toPx(),
    right = width,
    bottom = height - LabelsHeight.toPx(),
)

/** Where the [index]th value goes across: the middle of its slot for bars, or its point on a line. */
private fun xOf(index: Int, area: Rect, count: Int, bars: Boolean): Float = when {
    bars -> area.left + area.width / count * (index + Half)
    count > 1 -> area.left + area.width / (count - 1) * index
    else -> area.left
}

private fun yOf(value: Double, scale: AxisScale, area: Rect): Float =
    area.bottom - ((value - scale.min) / (scale.max - scale.min)).toFloat() * area.height

/** The bar or point under the mouse, or null when it's over the amounts on the left. */
private fun indexAt(x: Float, area: Rect, count: Int, bars: Boolean): Int? {
    if (count == 0 || x < area.left) return null
    val index = if (bars) {
        ((x - area.left) / (area.width / count)).toInt()
    } else {
        ((x - area.left) / (area.width / (count - 1).coerceAtLeast(1))).roundToInt()
    }
    return index.coerceIn(0, count - 1)
}

/** The amounts on the left, each with a faint line across; 0 gets a stronger one. */
private fun DrawScope.drawAxis(
    scale: AxisScale,
    area: Rect,
    measurer: TextMeasurer,
    style: TextStyle,
    lineColor: Color,
) {
    Axis.ticks(scale).forEach { tick ->
        val y = yOf(tick, scale, area)
        drawLine(
            color = lineColor,
            start = Offset(area.left, y),
            end = Offset(area.right, y),
            strokeWidth = if (tick == 0.0) 2.dp.toPx() else 1.dp.toPx(),
        )
        val text = measurer.measure(Format.compact(tick), style)
        drawText(
            textLayoutResult = text,
            topLeft = Offset(area.left - 8.dp.toPx() - text.size.width, y - text.size.height / 2f),
        )
    }
}

/** A band behind the hovered bars, or a line down from the hovered point. */
private fun DrawScope.drawHover(index: Int, area: Rect, count: Int, bars: Boolean, color: Color) {
    val x = xOf(index, area, count, bars)
    if (bars) {
        val slot = area.width / count
        drawRoundRect(
            color = color,
            topLeft = Offset(x - slot / 2, area.top),
            size = Size(slot, area.height),
            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
        )
    } else {
        drawLine(color, Offset(x, area.top), Offset(x, area.bottom), strokeWidth = 2.dp.toPx())
    }
}

/** The months or days below the plot, each under its bar or point, kept inside the chart. */
private fun DrawScope.drawLabels(
    labels: ImmutableList<String>,
    area: Rect,
    count: Int,
    bars: Boolean,
    measurer: TextMeasurer,
    style: TextStyle,
) {
    labels.forEachIndexed { index, label ->
        if (label.isEmpty() || index >= count) return@forEachIndexed
        val text = measurer.measure(label, style)
        val x = (xOf(index, area, count, bars) - text.size.width / 2f)
            .coerceIn(0f, (size.width - text.size.width).coerceAtLeast(0f))
        drawText(textLayoutResult = text, topLeft = Offset(x, area.bottom + 6.dp.toPx()))
    }
}

private fun DrawScope.drawChartLine(line: ChartLine, length: Int, scale: AxisScale, area: Rect) {
    if (line.values.isEmpty()) return
    val path = Path()
    line.values.forEachIndexed { index, value ->
        val point = Offset(xOf(index, area, length, bars = false), yOf(value, scale, area))
        if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    if (line.filled) {
        val zero = yOf(0.0, scale, area)
        val fill = Path().apply {
            addPath(path)
            lineTo(xOf(line.values.size - 1, area, length, bars = false), zero)
            lineTo(area.left, zero)
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
