package com.ivy.insights

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivy.base.model.TransactionType
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.insights.utilities.UtilityBillsCard
import com.ivy.legacy.utils.format
import com.ivy.legacy.utils.onScreenStart
import com.ivy.navigation.EditTransactionScreen
import com.ivy.navigation.ReportScreen
import com.ivy.navigation.UtilityBillsScreen
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.ui.R
import com.ivy.wallet.ui.theme.Green
import com.ivy.wallet.ui.theme.Red
import com.ivy.wallet.ui.theme.components.IvyOutlinedButton
import com.ivy.wallet.ui.theme.components.IvyToolbar
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.abs

@Composable
fun BoxWithConstraintsScope.InsightsScreenImpl() {
    val viewModel: InsightsViewModel = screenScopedViewModel()
    val state = viewModel.uiState()
    onScreenStart { viewModel.onEvent(InsightsEvent.Refresh) }
    InsightsUi(state = state, onEvent = viewModel::onEvent)
}

@Suppress("LongMethod")
@Composable
private fun InsightsUi(state: InsightsState, onEvent: (InsightsEvent) -> Unit) {
    val nav = navigation()
    val accent = if (state.kind == FlowKind.Expense) Red else Green
    val selected = state.categories.firstOrNull { it.key == state.selectedCategory }
    val unspecified = stringResource(R.string.unspecified)

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
                text = stringResource(R.string.reports),
                style = UI.typo.h2.style(fontWeight = FontWeight.Black)
            )
            Spacer(Modifier.height(16.dp))
            PeriodBar(state = state, onEvent = onEvent)
            Spacer(Modifier.height(12.dp))
            KindToggle(kind = state.kind, onEvent = onEvent)
        }

        if (!state.loading) {
            item {
                SummaryCard(
                    state = state,
                    title = selected?.let { it.name ?: unspecified },
                    onClearCategory = { onEvent(InsightsEvent.SelectCategory(null)) }
                )
            }

            item {
                ReportCard(title = stringResource(R.string.by_category)) {
                    if (state.categories.isEmpty()) {
                        EmptyText()
                    } else {
                        CategoryDonut(
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            slices = state.categories,
                            selectedKey = state.selectedCategory,
                            centerLabel = selected?.let { it.name ?: unspecified }
                                ?: stringResource(R.string.report_total),
                            centerValue = (selected?.amount ?: state.categories.sumOf { it.amount })
                                .format(state.currency),
                        )
                        Spacer(Modifier.height(16.dp))
                        CategoryList(
                            slices = state.categories,
                            selectedKey = state.selectedCategory,
                            currency = state.currency,
                            onSelect = { onEvent(InsightsEvent.SelectCategory(it)) },
                        )
                    }
                }
            }

            item {
                if (state.mode == PeriodMode.Month) {
                    ReportCard(title = stringResource(R.string.spending_pace)) {
                        PaceChart(
                            current = state.pace,
                            previous = state.previousPace,
                            daysInMonth = YearMonth.from(state.period.start).lengthOfMonth(),
                            color = accent,
                        )
                    }
                } else {
                    ReportCard(title = stringResource(R.string.by_month)) {
                        YearBars(values = state.yearBars, color = accent)
                    }
                }
            }

            if (state.topTitles.isNotEmpty()) {
                item {
                    ReportCard(
                        title = stringResource(
                            if (state.kind == FlowKind.Expense) R.string.top_places else R.string.top_sources
                        )
                    ) {
                        TopTitlesList(titles = state.topTitles, currency = state.currency, color = accent)
                    }
                }
            }

            if (state.transactions.isNotEmpty()) {
                item {
                    ReportCard(title = stringResource(R.string.report_transactions)) {
                        ReportTransactions(
                            rows = state.transactions,
                            currency = state.currency,
                            hasMore = state.hasMoreTransactions,
                            onOpen = { row ->
                                row.id?.let {
                                    nav.navigateTo(
                                        EditTransactionScreen(
                                            initialTransactionId = UUID.fromString(it),
                                            type = if (row.isIncome) TransactionType.INCOME else TransactionType.EXPENSE
                                        )
                                    )
                                }
                            },
                            onShowMore = { onEvent(InsightsEvent.ShowMoreTransactions) },
                        )
                    }
                }
            }

            item {
                ReportCard(title = stringResource(R.string.past_months)) {
                    MonthHistory(
                        months = state.months,
                        selected = YearMonth.from(state.period.start).takeIf { state.mode == PeriodMode.Month },
                        currency = state.currency,
                        onSelect = { onEvent(InsightsEvent.SelectMonth(it)) },
                    )
                }
            }

            item {
                ReportCard(title = stringResource(R.string.income_vs_spending)) {
                    IncomeSpendingBars(months = state.months)
                }
            }

            item {
                ReportCard(title = stringResource(R.string.net_worth)) {
                    NetWorthLine(points = state.netWorth, currency = state.currency)
                }
            }

            item {
                UtilityBillsCard(card = state.utilities, onOpen = { nav.navigateTo(UtilityBillsScreen) })
            }
        }

        item { Spacer(Modifier.height(96.dp)) }
    }
}

@Composable
private fun PeriodBar(state: InsightsState, onEvent: (InsightsEvent) -> Unit) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ReportChip(
            text = stringResource(R.string.month_short),
            selected = state.mode == PeriodMode.Month,
        ) { onEvent(InsightsEvent.SetMode(PeriodMode.Month)) }
        Spacer(Modifier.width(8.dp))
        ReportChip(
            text = stringResource(R.string.year_short),
            selected = state.mode == PeriodMode.Year,
        ) { onEvent(InsightsEvent.SetMode(PeriodMode.Year)) }

        Spacer(Modifier.weight(1f))

        ArrowButton(text = "‹", enabled = true) { onEvent(InsightsEvent.Previous) }
        Text(
            modifier = Modifier.padding(horizontal = 8.dp),
            text = when (state.mode) {
                PeriodMode.Month -> state.period.start.format(DateTimeFormatter.ofPattern("MMM yyyy"))
                PeriodMode.Year -> state.period.start.year.toString()
            },
            style = UI.typo.b2.style(fontWeight = FontWeight.ExtraBold)
        )
        ArrowButton(text = "›", enabled = state.canGoNext) { onEvent(InsightsEvent.Next) }
    }
}

@Composable
private fun ArrowButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        modifier = Modifier
            .clip(UI.shapes.rFull)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        text = text,
        style = UI.typo.h2.style(
            color = if (enabled) UI.colors.pureInverse else UI.colors.medium,
            fontWeight = FontWeight.Bold
        )
    )
}

@Composable
private fun KindToggle(kind: FlowKind, onEvent: (InsightsEvent) -> Unit) {
    Row(modifier = Modifier.padding(horizontal = 16.dp)) {
        ReportChip(
            modifier = Modifier.weight(1f),
            text = stringResource(R.string.expenses),
            selected = kind == FlowKind.Expense,
        ) { onEvent(InsightsEvent.SetKind(FlowKind.Expense)) }
        Spacer(Modifier.width(8.dp))
        ReportChip(
            modifier = Modifier.weight(1f),
            text = stringResource(R.string.income),
            selected = kind == FlowKind.Income,
        ) { onEvent(InsightsEvent.SetKind(FlowKind.Income)) }
    }
}

/** Total for the period (or the selected category) with the change vs the previous period. */
@Composable
private fun SummaryCard(state: InsightsState, title: String?, onClearCategory: () -> Unit) {
    val summary = state.summary
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .fillMaxWidth()
            .border(2.dp, UI.colors.medium, UI.shapes.r4)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                modifier = Modifier.weight(1f),
                text = title ?: stringResource(
                    if (state.kind == FlowKind.Expense) R.string.total_spent else R.string.total_received
                ),
                style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.Bold)
            )
            if (title != null) {
                Text(
                    modifier = Modifier
                        .clip(UI.shapes.rFull)
                        .clickable(onClick = onClearCategory)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    text = stringResource(R.string.show_all_categories),
                    style = UI.typo.c.style(color = UI.colors.pureInverse, fontWeight = FontWeight.Bold)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${summary.total.format(state.currency)} ${state.currency}",
            style = UI.typo.nH2.style(fontWeight = FontWeight.ExtraBold)
        )
        Spacer(Modifier.height(8.dp))
        summary.changePercent?.let { change ->
            // more spending is bad (red), more income is good (green)
            val good = if (state.kind == FlowKind.Expense) change <= 0 else change >= 0
            Text(
                text = stringResource(
                    if (change >= 0) R.string.change_up_vs_previous else R.string.change_down_vs_previous,
                    abs(change),
                    summary.previousTotal.format(state.currency)
                ),
                style = UI.typo.c.style(color = if (good) Green else Red, fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.height(4.dp))
        }
        Text(
            text = stringResource(
                R.string.daily_average_and_count,
                summary.dailyAverage.format(state.currency),
                summary.count
            ),
            style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.SemiBold)
        )
    }
}

@Composable
private fun EmptyText() {
    Text(
        text = stringResource(R.string.nothing_in_this_period),
        style = UI.typo.b2.style(color = UI.colors.gray, fontWeight = FontWeight.Medium)
    )
}
