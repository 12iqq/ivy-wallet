package com.ivy.desktop.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ivy.desktop.data.CategoryId
import com.ivy.desktop.data.Ledger
import com.ivy.desktop.reports.CategoryRow
import com.ivy.desktop.reports.Csv
import com.ivy.desktop.reports.Format
import com.ivy.desktop.reports.MonthPace
import com.ivy.desktop.reports.ReportChoice
import com.ivy.desktop.reports.ReportView
import com.ivy.desktop.reports.Reports
import com.ivy.desktop.reports.YearMonths
import com.ivy.insights.FlowKind
import com.ivy.insights.PeriodMode
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

@Composable
internal fun ReportsPage(
    ledger: Ledger,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    var choice by remember {
        mutableStateOf(ReportChoice(PeriodMode.Month, YearMonth.from(today), FlowKind.Expense, null))
    }
    val report = remember(ledger, choice, today) { Reports.report(ledger, choice, today) }
    val listState = rememberLazyListState()
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(PagePadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ReportHeader(choice = choice, report = report, onChoice = { choice = it })
            }
            item { OtherCurrencies(ledger) }
            item {
                Summary(
                    choice = choice,
                    report = report,
                    currency = ledger.baseCurrency,
                    categoryName = choice.categoryKey?.let { key ->
                        ledger.categories[CategoryId(key)]?.name ?: Reports.UNCATEGORIZED_NAME
                    },
                    onClearCategory = { choice = choice.copy(categoryKey = null) },
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Categories(
                        choice = choice,
                        rows = report,
                        currency = ledger.baseCurrency,
                        onCategory = { key ->
                            choice = choice.copy(categoryKey = key.takeIf { it != choice.categoryKey })
                        },
                        modifier = Modifier.weight(1f),
                    )
                    Chart(choice, report, ledger.baseCurrency, Modifier.weight(1f))
                }
            }
            item { Places(choice, report, ledger.baseCurrency) }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Transactions",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    ExportButton(
                        fileName = exportName(choice, report, ledger),
                        csv = { Csv.transactions(report.transactions, ledger) },
                    )
                }
            }
            if (report.transactions.isEmpty()) {
                item { Note("Nothing here for this period.") }
            }
            items(report.transactions, key = { it.id.value }) { trn ->
                val category = trn.categoryId?.let { ledger.categories[it] }
                TransactionRow(
                    date = Format.day(trn.time.toLocalDate(), today),
                    title = trn.title?.takeIf { it.isNotBlank() } ?: category?.name ?: Reports.UNCATEGORIZED_NAME,
                    details = listOfNotNull(
                        category?.name,
                        trn.accountName,
                        trn.description?.takeIf { it.isNotBlank() },
                    ).joinToString(" · "),
                    amount = Format.money(trn.amount, trn.currency),
                    type = trn.type,
                    categoryColor = colorOf(category?.color),
                )
            }
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(listState),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
        )
    }
}

@Composable
private fun ReportHeader(
    choice: ReportChoice,
    report: ReportView,
    onChoice: (ReportChoice) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PageTitle(title = "Reports", modifier = Modifier.weight(1f))
        Toggle(
            options = persistentListOf(FlowKind.Expense, FlowKind.Income),
            selected = choice.kind,
            label = { if (it == FlowKind.Expense) "Spending" else "Income" },
            onSelect = { onChoice(choice.copy(kind = it, categoryKey = null)) },
        )
        Toggle(
            options = persistentListOf(PeriodMode.Month, PeriodMode.Year),
            selected = choice.mode,
            label = { it.name },
            onSelect = { onChoice(choice.copy(mode = it)) },
        )
        Chip(text = "‹", onClick = { onChoice(Reports.step(choice, -1)) })
        Text(
            text = Format.period(report.period, choice.mode),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.width(PeriodLabelWidth),
            textAlign = TextAlign.Center,
        )
        Chip(text = "›", onClick = { onChoice(Reports.step(choice, 1)) })
    }
}

@Composable
private fun Summary(
    choice: ReportChoice,
    report: ReportView,
    currency: String,
    categoryName: String?,
    onClearCategory: () -> Unit,
) {
    val summary = report.summary
    val previous = Format.period(Reports.period(Reports.step(choice, -1)), choice.mode)
    Panel(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (choice.kind == FlowKind.Expense) "Total spent" else "Total received",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (categoryName != null) {
                Chip(text = "$categoryName  ✕", onClick = onClearCategory, selected = true)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(text = Format.money(summary.total, currency), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        val change = summary.changePercent
        if (change != null) {
            // spending going up is bad news, income going up is good news
            val good = (change <= 0) == (choice.kind == FlowKind.Expense)
            Text(
                text = "${if (change >= 0) "▲" else "▼"} ${abs(change)}% vs $previous " +
                    "(${Format.money(summary.previousTotal, currency)})",
                style = MaterialTheme.typography.labelMedium,
                color = if (good) Green else Red,
            )
        }
        Note("${Format.money(summary.dailyAverage, currency)} a day on average · ${summary.count} transactions")
    }
}

@Composable
private fun Categories(
    choice: ReportChoice,
    rows: ReportView,
    currency: String,
    onCategory: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Panel(
        modifier = modifier,
        title = "By category",
        subtitle = "Click a category to see only its transactions, and again to see all.",
    ) {
        if (rows.categories.isEmpty()) Note("Nothing here for this period.")
        val period = Format.period(rows.period, choice.mode)
        val what = if (choice.kind == FlowKind.Expense) "spending" else "income"
        rows.categories.forEach { row ->
            Hint(
                text = "${row.count} transactions, ${Format.percent(row.share)} of all your $what in $period. " +
                    "That's ${Format.money(row.amount / row.count.coerceAtLeast(1), currency)} each on average.",
            ) {
                CategoryLine(
                    row = row,
                    currency = currency,
                    selected = row.key == choice.categoryKey,
                    onClick = { onCategory(row.key) },
                )
            }
        }
    }
}

@Composable
private fun CategoryLine(
    row: CategoryRow,
    currency: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val color = colorOf(row.color)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(color)
            Spacer(Modifier.width(10.dp))
            Text(text = row.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Note("${row.count}×", modifier = Modifier.padding(end = 16.dp))
            Text(text = Format.money(row.amount, currency), style = NumberStyle)
            Text(
                text = Format.percent(row.share),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(ShareWidth),
                textAlign = TextAlign.End,
            )
        }
        Spacer(Modifier.height(6.dp))
        ShareBar(share = row.share, color = color)
    }
}

@Composable
private fun Chart(
    choice: ReportChoice,
    report: ReportView,
    currency: String,
    modifier: Modifier = Modifier,
) {
    val color = if (choice.kind == FlowKind.Expense) Red else Green
    val what = if (choice.kind == FlowKind.Expense) "spending" else "income"
    when (val chart = report.chart) {
        is MonthPace -> Panel(
            modifier = modifier,
            title = "Through the month",
            subtitle = "Your $what added up day by day, next to the month before (dashed). Point at a day " +
                "to see the amounts.",
        ) {
            val days = maxOf(chart.daysInMonth, chart.previous.size)
            Legend(
                persistentListOf(
                    Format.month(choice.month) to color,
                    Format.month(choice.month.minusMonths(1)) to Gray,
                )
            )
            Spacer(Modifier.height(12.dp))
            LineChart(
                lines = persistentListOf(
                    ChartLine(chart.current.toImmutableList(), color, dashed = false, filled = true),
                    ChartLine(chart.previous.toImmutableList(), Gray, dashed = true, filled = false),
                ),
                length = days,
                labels = (1..days).map { day -> if (day == 1 || day % DayLabelEvery == 0) "$day" else "" }
                    .toImmutableList(),
                tips = paceTips(choice, chart, days, color, currency),
            )
        }

        is YearMonths -> Panel(
            modifier = modifier,
            title = "By month",
            subtitle = "Your $what in each month of ${choice.month.year}. Point at a month to see it.",
        ) {
            BarChart(
                series = persistentListOf(chart.totals.toImmutableList()),
                colors = persistentListOf(color),
                labels = Month.entries
                    .map { it.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) }
                    .toImmutableList(),
                tips = yearTips(choice, chart, color, currency),
            )
        }
    }
}

@Composable
private fun Places(
    choice: ReportChoice,
    report: ReportView,
    currency: String,
) {
    Panel(
        modifier = Modifier.fillMaxWidth(),
        title = if (choice.kind == FlowKind.Expense) "Where it went" else "Where it came from",
        subtitle = "Added up by the transaction's name, biggest first. 3× means three transactions.",
    ) {
        if (report.places.isEmpty()) Note("Nothing here for this period.")
        val most = report.places.maxOfOrNull { it.amount }?.takeIf { it > 0 } ?: 1.0
        report.places.forEach { place ->
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = place.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Note("${place.count}×", modifier = Modifier.padding(end = 16.dp))
                    Text(text = Format.money(place.amount, currency), style = NumberStyle)
                }
                Spacer(Modifier.height(6.dp))
                ShareBar(share = place.amount / most, color = Ivy)
            }
        }
    }
}

/** For each day: the running total so far, this month and the month before. */
internal fun paceTips(
    choice: ReportChoice,
    chart: MonthPace,
    days: Int,
    color: Color,
    currency: String,
): ImmutableList<ChartTip> = (0 until days).map { index ->
    ChartTip(
        title = "By day ${index + 1}",
        rows = listOfNotNull(
            chart.current.getOrNull(index)?.let {
                TipRow(Format.month(choice.month), Format.money(it, currency), color)
            },
            chart.previous.getOrNull(index)?.let {
                TipRow(Format.month(choice.month.minusMonths(1)), Format.money(it, currency), Gray)
            },
        ).toImmutableList(),
    )
}.toImmutableList()

/** For each month of the year: its total, and its share of the year. */
internal fun yearTips(
    choice: ReportChoice,
    chart: YearMonths,
    color: Color,
    currency: String,
): ImmutableList<ChartTip> {
    val year = chart.totals.sum()
    return chart.totals.mapIndexed { index, total ->
        ChartTip(
            title = Format.month(YearMonth.of(choice.month.year, index + 1)),
            rows = listOfNotNull(
                TipRow(
                    if (choice.kind == FlowKind.Expense) "Spent" else "Received",
                    Format.money(total, currency),
                    color,
                ),
                TipRow("Of the year", Format.percent(total / year), Color.Unspecified).takeIf { year > 0 },
            ).toImmutableList(),
        )
    }.toImmutableList()
}

/** Ivy Wallet - October 2026 spending - Groceries.csv */
private fun exportName(choice: ReportChoice, report: ReportView, ledger: Ledger): String {
    val what = if (choice.kind == FlowKind.Expense) "spending" else "income"
    val category = choice.categoryKey?.let { key ->
        " - " + (ledger.categories[CategoryId(key)]?.name ?: Reports.UNCATEGORIZED_NAME)
    }.orEmpty()
    return "Ivy Wallet - ${Format.period(report.period, choice.mode)} $what$category.csv"
}

private const val DayLabelEvery = 5
private val PeriodLabelWidth = 150.dp
private val ShareWidth = 48.dp
