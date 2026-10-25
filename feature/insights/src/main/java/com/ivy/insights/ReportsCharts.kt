package com.ivy.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.legacy.utils.format
import com.ivy.ui.R
import com.ivy.wallet.ui.theme.Green
import com.ivy.wallet.ui.theme.Ivy
import com.ivy.wallet.ui.theme.Red
import com.ivy.wallet.ui.theme.toComposeColor
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** Category donut. When a category is selected the other slices fade. */
@Composable
fun CategoryDonut(
    slices: ImmutableList<CategorySlice>,
    selectedKey: String?,
    centerLabel: String,
    centerValue: String,
    modifier: Modifier = Modifier,
) {
    val gray = UI.colors.gray
    val track = UI.colors.medium
    Box(modifier = modifier.size(DonutSize.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(DonutSize.dp)) {
            val stroke = size.minDimension * DonutStrokeFraction
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            if (slices.isEmpty()) {
                drawArc(track, 0f, FullCircle, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            }
            var start = StartAngle
            slices.forEach { slice ->
                val sweep = (slice.share * FullCircle).toFloat()
                val base = slice.color?.toComposeColor() ?: gray
                val faded = selectedKey != null && selectedKey != slice.key
                drawArc(
                    color = if (faded) base.copy(alpha = FadedAlpha) else base,
                    startAngle = start + SliceGap / 2,
                    sweepAngle = (sweep - SliceGap).coerceAtLeast(MinSweep),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke),
                )
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = centerLabel,
                maxLines = 1,
                style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.Bold)
            )
            Text(
                text = centerValue,
                style = UI.typo.nB1.style(fontWeight = FontWeight.ExtraBold)
            )
        }
    }
}

/**
 * Cumulative spending (or income) through the month, against last month as a dashed line —
 * shows at a glance whether this month is running ahead or behind.
 */
@Composable
fun PaceChart(
    current: ImmutableList<Double>,
    previous: ImmutableList<Double>,
    daysInMonth: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val max = maxOf(current.maxOrNull() ?: 0.0, previous.maxOrNull() ?: 0.0).takeIf { it > 0 } ?: 1.0
    val previousColor = UI.colors.gray
    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(ChartHeight.dp)
        ) {
            val step = size.width / (daysInMonth - 1).coerceAtLeast(1)
            fun y(value: Double): Float = (size.height - value / max * size.height).toFloat()
            fun line(values: List<Double>): Path = Path().apply {
                values.forEachIndexed { i, value ->
                    if (i == 0) moveTo(0f, y(value)) else lineTo(i * step, y(value))
                }
            }
            if (previous.size > 1) {
                drawPath(
                    line(previous),
                    color = previousColor,
                    style = Stroke(
                        width = LineWidth / 2,
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(DashOn, DashOff))
                    )
                )
            }
            if (current.size > 1) {
                drawPath(line(current), color = color, style = Stroke(width = LineWidth, cap = StrokeCap.Round))
            }
            current.lastOrNull()?.let { last ->
                drawCircle(color = color, radius = DotRadius, center = Offset((current.size - 1) * step, y(last)))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(text = "1", style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.SemiBold))
            Spacer(Modifier.weight(1f))
            Text(
                text = daysInMonth.toString(),
                style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.SemiBold)
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LegendItem(color = color, text = stringResource(R.string.this_month))
            LegendItem(color = previousColor, text = stringResource(R.string.last_month))
        }
    }
}

/** One bar per month of the selected year. */
@Composable
fun YearBars(
    values: ImmutableList<Double>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val max = values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(ChartHeight.dp)
        ) {
            val slot = size.width / values.size.coerceAtLeast(1)
            val barWidth = slot * BarFraction
            values.forEachIndexed { i, value ->
                val height = (value / max * size.height).toFloat()
                drawRoundRect(
                    color = color,
                    topLeft = Offset(i * slot + (slot - barWidth) / 2, size.height - height),
                    size = Size(barWidth, height),
                    cornerRadius = CornerRadius(barWidth / 2, barWidth / 2),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        MonthLabels(labels = narrowNames(Month.values().map { YearMonth.of(LabelYear, it) }))
    }
}

/** Grouped green (income) / red (spending) bars for each month. */
@Composable
fun IncomeSpendingBars(months: ImmutableList<MonthTotals>, modifier: Modifier = Modifier) {
    val max = months.maxOfOrNull { maxOf(it.income, it.expense) }?.takeIf { it > 0 } ?: 1.0
    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(ChartHeight.dp)
        ) {
            val slot = size.width / months.size.coerceAtLeast(1)
            val barWidth = slot * BarFraction / 2
            val radius = CornerRadius(barWidth / 2, barWidth / 2)
            months.forEachIndexed { i, month ->
                val left = i * slot + slot * (1 - BarFraction) / 2
                val incomeHeight = (month.income / max * size.height).toFloat()
                val expenseHeight = (month.expense / max * size.height).toFloat()
                drawRoundRect(Green, Offset(left, size.height - incomeHeight), Size(barWidth, incomeHeight), radius)
                drawRoundRect(
                    Red,
                    Offset(left + barWidth, size.height - expenseHeight),
                    Size(barWidth, expenseHeight),
                    radius
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        MonthLabels(labels = narrowNames(months.map { it.month }))
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LegendItem(color = Green, text = stringResource(R.string.income))
            LegendItem(color = Red, text = stringResource(R.string.expenses))
        }
    }
}

/** A bar split into parts drawn bottom-up, e.g. a utility bill: electricity, water, gas, the rest. */
@Immutable
data class StackedBar(val month: YearMonth, val parts: ImmutableList<Double>)

/** Stacked bars, oldest left; [colors] and [legend] follow the order of the parts. */
@Composable
fun StackedBars(
    bars: ImmutableList<StackedBar>,
    colors: ImmutableList<Color>,
    legend: ImmutableList<String>,
    modifier: Modifier = Modifier,
) {
    val max = bars.maxOfOrNull { it.parts.sum() }?.takeIf { it > 0 } ?: 1.0
    // a few bars shouldn't turn into a few very wide ones: empty slots on the left instead
    val slots = maxOf(bars.size, MinStackedSlots)
    val empty = slots - bars.size
    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(ChartHeight.dp)
        ) {
            val slot = size.width / slots
            val barWidth = slot * BarFraction
            bars.forEachIndexed { i, bar ->
                val left = (empty + i) * slot + (slot - barWidth) / 2
                val height = (bar.parts.sum() / max * size.height).toFloat()
                val shape = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = left,
                            top = size.height - height,
                            right = left + barWidth,
                            bottom = size.height,
                            cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
                        )
                    )
                }
                clipPath(shape) {
                    var bottom = size.height
                    bar.parts.forEachIndexed { part, value ->
                        val partHeight = (value / max * size.height).toFloat()
                        drawRect(
                            color = colors[part % colors.size],
                            topLeft = Offset(left, bottom - partHeight),
                            size = Size(barWidth, partHeight)
                        )
                        bottom -= partHeight
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        MonthLabels(labels = (List(empty) { "" } + narrowNames(bars.map { it.month })).toImmutableList())
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            legend.forEachIndexed { i, text -> LegendItem(color = colors[i % colors.size], text = text) }
        }
    }
}

/** Total balance at the end of each month. */
@Composable
fun NetWorthLine(points: ImmutableList<NetWorthPoint>, currency: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        if (points.isEmpty()) return@Column
        val values = points.map { it.value }
        val min = values.min()
        val max = values.max()
        val range = (max - min).takeIf { it > 0 } ?: 1.0
        val gridColor = UI.colors.medium

        Text(
            text = "${values.last().format(currency)} $currency",
            style = UI.typo.nB1.style(
                color = if (values.last() >= 0) UI.colors.pureInverse else Red,
                fontWeight = FontWeight.ExtraBold
            )
        )
        Spacer(Modifier.height(12.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(ChartHeight.dp)
        ) {
            val step = if (points.size > 1) size.width / (points.size - 1) else 0f
            fun y(value: Double): Float = (size.height - (value - min) / range * size.height).toFloat()

            if (min < 0 && max > 0) {
                drawLine(gridColor, Offset(0f, y(0.0)), Offset(size.width, y(0.0)), strokeWidth = 2f)
            }
            val path = Path()
            values.forEachIndexed { i, value ->
                val point = Offset(i * step, y(value))
                if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
            }
            drawPath(path, color = Ivy, style = Stroke(width = LineWidth, cap = StrokeCap.Round))
            values.forEachIndexed { i, value ->
                drawCircle(color = Ivy, radius = DotRadius, center = Offset(i * step, y(value)))
            }
        }
        Spacer(Modifier.height(6.dp))
        MonthLabels(labels = narrowNames(points.map { it.month }))
    }
}

@Composable
fun ShareBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .background(UI.colors.medium, UI.shapes.rFull)
    ) {
        if (fraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(color, UI.shapes.rFull)
            )
        }
    }
}

@Composable
private fun MonthLabels(labels: ImmutableList<String>) {
    Row(modifier = Modifier.fillMaxWidth()) {
        labels.forEach {
            Text(
                modifier = Modifier.weight(1f),
                text = it,
                style = UI.typo.c.style(
                    color = UI.colors.gray,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            )
        }
    }
}

@Composable
private fun LegendItem(color: Color, text: String) {
    Row(
        modifier = Modifier.padding(end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, UI.shapes.rFull)
        )
        Text(
            modifier = Modifier.padding(start = 6.dp),
            text = text,
            style = UI.typo.c.style(fontWeight = FontWeight.SemiBold)
        )
    }
}

private fun narrowNames(months: List<YearMonth>): ImmutableList<String> =
    months.map { it.month.getDisplayName(TextStyle.NARROW, Locale.getDefault()) }.toImmutableList()

// any year works: only the month names are shown
private const val LabelYear = 2000
private const val DonutSize = 180
private const val DonutStrokeFraction = 0.16f
private const val FullCircle = 360f
private const val StartAngle = -90f
private const val SliceGap = 2f
private const val MinSweep = 0.5f
private const val ChartHeight = 140
private const val BarFraction = 0.7f
private const val MinStackedSlots = 6
private const val LineWidth = 6f
private const val DotRadius = 7f
private const val FadedAlpha = 0.25f
private const val DashOn = 12f
private const val DashOff = 10f
