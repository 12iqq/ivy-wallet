package com.ivy.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.legacy.utils.format
import com.ivy.legacy.utils.onScreenStart
import com.ivy.navigation.ReportScreen
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.ui.R
import com.ivy.wallet.ui.theme.Green
import com.ivy.wallet.ui.theme.Ivy
import com.ivy.wallet.ui.theme.Red
import com.ivy.wallet.ui.theme.components.IvyOutlinedButton
import com.ivy.wallet.ui.theme.components.IvyToolbar
import com.ivy.wallet.ui.theme.toComposeColor
import kotlinx.collections.immutable.ImmutableList
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun BoxWithConstraintsScope.InsightsScreenImpl() {
    val viewModel: InsightsViewModel = screenScopedViewModel()
    val state = viewModel.uiState()
    onScreenStart { viewModel.onEvent(InsightsEvent.Refresh) }
    InsightsUi(state = state)
}

@Composable
private fun InsightsUi(state: InsightsState) {
    val nav = navigation()
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        item {
            IvyToolbar(onBack = { nav.onBackPressed() }) {
                Spacer(Modifier.weight(1f))
                IvyOutlinedButton(
                    text = stringResource(R.string.detailed_report),
                    iconStart = R.drawable.ic_statistics_xs,
                ) { nav.navigateTo(ReportScreen) }
                Spacer(Modifier.width(24.dp))
            }
            Text(
                modifier = Modifier.padding(start = 32.dp, top = 8.dp),
                text = stringResource(R.string.insights),
                style = UI.typo.h2.style(fontWeight = FontWeight.Black)
            )
        }

        if (!state.loading) {
            item { Summary(state) }
            item {
                ChartCard(title = stringResource(R.string.income_vs_spending)) {
                    MonthlyBars(months = state.months)
                    Spacer(Modifier.height(8.dp))
                    Legend()
                }
            }
            item {
                ChartCard(title = stringResource(R.string.net_worth)) {
                    NetWorthLine(points = state.netWorth, currency = state.currency)
                }
            }
            if (state.categories.isNotEmpty()) {
                item {
                    ChartCard(title = stringResource(R.string.this_month_vs_last)) {
                        CategoryBars(categories = state.categories, currency = state.currency)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(96.dp)) }
    }
}

@Composable
private fun Summary(state: InsightsState) {
    val current = state.months.lastOrNull()
    val previous = state.months.getOrNull(state.months.size - 2)
    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
        StatTile(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.spent_this_month),
            value = "${(current?.expense ?: 0.0).format(state.currency)} ${state.currency}",
            footnote = previous?.let {
                stringResource(R.string.last_month_value, it.expense.format(state.currency))
            },
        )
        Spacer(Modifier.width(12.dp))
        StatTile(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.average_month),
            value = "${state.averageMonthlySpend.format(state.currency)} ${state.currency}",
            footnote = stringResource(R.string.average_month_footnote),
        )
    }
}

@Composable
private fun StatTile(label: String, value: String, footnote: String?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .border(2.dp, UI.colors.medium, UI.shapes.r4)
            .padding(16.dp)
    ) {
        Text(text = label, style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(4.dp))
        Text(text = value, style = UI.typo.nB1.style(fontWeight = FontWeight.ExtraBold))
        if (footnote != null) {
            Spacer(Modifier.height(4.dp))
            Text(text = footnote, style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.Medium))
        }
    }
}

@Composable
private fun ChartCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .border(2.dp, UI.colors.medium, UI.shapes.r4)
            .padding(16.dp)
    ) {
        Text(text = title, style = UI.typo.b2.style(fontWeight = FontWeight.ExtraBold))
        Spacer(Modifier.height(16.dp))
        content()
    }
}

/** Grouped green (income) / red (spending) bars for each month. */
@Composable
private fun MonthlyBars(months: ImmutableList<MonthTotals>) {
    val max = months.maxOfOrNull { maxOf(it.income, it.expense) }?.takeIf { it > 0 } ?: 1.0
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
    ) {
        val slot = size.width / months.size.coerceAtLeast(1)
        val barWidth = slot * BarFraction / 2
        val radius = CornerRadius(barWidth / 2, barWidth / 2)
        months.forEachIndexed { i, month ->
            val left = i * slot + slot * (1 - BarFraction) / 2
            val incomeHeight = (month.income / max * size.height).toFloat()
            val expenseHeight = (month.expense / max * size.height).toFloat()
            drawRoundRect(
                color = Green,
                topLeft = Offset(left, size.height - incomeHeight),
                size = Size(barWidth, incomeHeight),
                cornerRadius = radius,
            )
            drawRoundRect(
                color = Red,
                topLeft = Offset(left + barWidth, size.height - expenseHeight),
                size = Size(barWidth, expenseHeight),
                cornerRadius = radius,
            )
        }
    }
    Spacer(Modifier.height(6.dp))
    MonthLabels(months.map { it.month.month.getDisplayName(TextStyle.NARROW, Locale.getDefault()) })
}

@Composable
private fun MonthLabels(labels: List<String>) {
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
private fun Legend() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(Green)
        Text(
            modifier = Modifier.padding(start = 6.dp, end = 16.dp),
            text = stringResource(R.string.income),
            style = UI.typo.c.style(fontWeight = FontWeight.SemiBold)
        )
        Dot(Red)
        Text(
            modifier = Modifier.padding(start = 6.dp),
            text = stringResource(R.string.expenses),
            style = UI.typo.c.style(fontWeight = FontWeight.SemiBold)
        )
    }
}

@Composable
private fun Dot(color: Color) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .background(color, UI.shapes.rFull)
    )
}

/** Total balance at the end of each month. */
@Composable
private fun NetWorthLine(points: ImmutableList<NetWorthPoint>, currency: String) {
    if (points.isEmpty()) return
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
            .height(140.dp)
    ) {
        val step = if (points.size > 1) size.width / (points.size - 1) else 0f
        fun y(value: Double): Float = (size.height - (value - min) / range * size.height).toFloat()

        if (min < 0 && max > 0) {
            // zero line
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
    MonthLabels(points.map { it.month.month.getDisplayName(TextStyle.NARROW, Locale.getDefault()) })
}

/** Top categories this month (solid) against last month (faded). */
@Composable
private fun CategoryBars(categories: ImmutableList<CategoryBar>, currency: String) {
    val max = categories.maxOf { maxOf(it.thisMonth, it.lastMonth) }.takeIf { it > 0 } ?: 1.0
    val unspecified = stringResource(R.string.unspecified)
    categories.forEach { category ->
        val color = category.color?.toComposeColor() ?: UI.colors.gray
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                modifier = Modifier.weight(1f),
                text = category.name.ifBlank { unspecified },
                style = UI.typo.c.style(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "${category.thisMonth.format(currency)} / ${category.lastMonth.format(currency)}",
                style = UI.typo.nC.style(color = UI.colors.gray, fontWeight = FontWeight.SemiBold)
            )
        }
        Spacer(Modifier.height(4.dp))
        Bar(fraction = (category.thisMonth / max).toFloat(), color = color)
        Spacer(Modifier.height(2.dp))
        Bar(fraction = (category.lastMonth / max).toFloat(), color = color.copy(alpha = FadedAlpha))
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Bar(fraction: Float, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
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

private const val BarFraction = 0.7f
private const val LineWidth = 6f
private const val DotRadius = 7f
private const val FadedAlpha = 0.35f
