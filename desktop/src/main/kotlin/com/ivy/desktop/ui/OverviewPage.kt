package com.ivy.desktop.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ivy.desktop.data.Ledger
import com.ivy.desktop.data.LedgerAccount
import com.ivy.desktop.reports.CardView
import com.ivy.desktop.reports.Csv
import com.ivy.desktop.reports.Format
import com.ivy.desktop.reports.OverviewView
import com.ivy.desktop.reports.Reports
import com.ivy.desktop.reports.YearTotals
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import java.time.LocalDate
import java.time.YearMonth

@Composable
internal fun OverviewPage(
    ledger: Ledger,
    today: LocalDate,
    modifier: Modifier = Modifier,
    startWithAllTime: Boolean = false,
) {
    var allTime by remember { mutableStateOf(startWithAllTime) }
    val overview = remember(ledger, today, allTime) { Reports.overview(ledger, today, allTime) }
    val currency = ledger.baseCurrency
    val scroll = rememberScrollState()
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(PagePadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PageTitle(
                    title = "Overview",
                    subtitle = "Amounts in $currency, as of ${Format.day(today, today)}",
                    modifier = Modifier.weight(1f),
                )
                Toggle(
                    options = persistentListOf(false, true),
                    selected = allTime,
                    label = { if (it) "All time" else "Last 12 months" },
                    onSelect = { allTime = it },
                )
                Spacer(Modifier.width(12.dp))
                ExportButton(
                    fileName = if (allTime) "Ivy Wallet - every month.csv" else "Ivy Wallet - last 12 months.csv",
                    csv = { Csv.months(overview, ledger) },
                )
            }
            OtherCurrencies(ledger)
            Totals(overview, currency, today)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Accounts(overview.accounts.toImmutableList(), Modifier.weight(1f))
                if (overview.cards.isNotEmpty()) {
                    Cards(overview.cards.toImmutableList(), today, Modifier.weight(1f))
                }
            }
            IncomeAndSpending(overview, ledger, today, allTime)
            Panel(
                title = if (allTime) {
                    "Total balance since ${Format.month(overview.months.first().month)}"
                } else {
                    "Total balance over time"
                },
                subtitle = "What all your accounts added up to at the end of each month, with card debt " +
                    "taken off. This month shows today. Point at a month to see the amount.",
            ) {
                LineChart(
                    lines = persistentListOf(
                        ChartLine(
                            values = overview.netWorth.map { it.value }.toImmutableList(),
                            color = Ivy,
                            dashed = false,
                            filled = true,
                        ),
                    ),
                    length = overview.netWorth.size,
                    labels = monthLabels(overview.netWorth.map { it.month }),
                    tips = balanceTips(overview, currency, today),
                )
            }
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scroll),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
        )
    }
}

@Composable
private fun Totals(overview: OverviewView, currency: String, today: LocalDate) {
    val thisMonth = overview.months.last()
    val lastMonth = overview.months[overview.months.size - 2]
    val from = "1 ${Format.month(today)}"
    Row(
        modifier = Modifier.height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Stat(
            label = "Total balance",
            value = Format.money(overview.totalBalance, currency),
            note = "Across ${inTotal(overview, currency)} accounts",
            info = "All your accounts added up, with what you owe on credit cards taken off. Accounts you've " +
                "left out of the total in the phone app don't count.",
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
        Stat(
            label = "Spent this month",
            value = Format.money(thisMonth.expense, currency),
            note = "Last month ${Format.money(lastMonth.expense, currency)}",
            info = "Everything you spent from $from to today. Moving money between your own accounts, " +
                "like paying off a card, isn't spending, so it's left out.",
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
        Stat(
            label = "Income this month",
            value = Format.money(thisMonth.income, currency),
            note = "Last month ${Format.money(lastMonth.income, currency)}",
            info = "Everything that came in from $from to today. Money moved between your own accounts " +
                "isn't income, so it's left out.",
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
        val months = overview.averageMonths
        Stat(
            label = "Spending in an average month",
            value = Format.money(overview.averageMonthlySpend, currency),
            note = if (months > 0) "Over the last $months full months" else "Not enough history yet",
            info = if (months > 0) {
                "What you spent in the last $months full months, divided by $months. This month isn't " +
                    "counted because it isn't over yet, and neither are months with nothing in them."
            } else {
                "Shows once there's a full month of transactions before this one."
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
    }
}

@Composable
private fun IncomeAndSpending(overview: OverviewView, ledger: Ledger, today: LocalDate, allTime: Boolean) {
    val currency = ledger.baseCurrency
    // a long history reads better a year at a time
    val byYear = allTime && overview.months.size > MonthsBeforeYears
    val since = Format.month(overview.months.first().month)
    Panel(
        title = when {
            byYear -> "Income and spending by year"
            allTime -> "Income and spending since $since"
            else -> "Income and spending, last 12 months"
        },
        subtitle = "What came in and what you spent each ${if (byYear) "year" else "month"}. Money moved between " +
            "your own accounts isn't counted. Point at a bar to see the amounts.",
    ) {
        Legend(persistentListOf("Income" to Green, "Spending" to Red))
        Spacer(Modifier.height(12.dp))
        if (byYear) {
            val years = remember(ledger, today) { Reports.years(ledger, today) }
            BarChart(
                series = persistentListOf(
                    years.map { it.income }.toImmutableList(),
                    years.map { it.expense }.toImmutableList(),
                ),
                colors = persistentListOf(Green, Red),
                labels = years.map { it.year.toString() }.toImmutableList(),
                tips = yearTips(years, currency, today),
            )
        } else {
            BarChart(
                series = persistentListOf(
                    overview.months.map { it.income }.toImmutableList(),
                    overview.months.map { it.expense }.toImmutableList(),
                ),
                colors = persistentListOf(Green, Red),
                labels = monthLabels(overview.months.map { it.month }),
                tips = incomeSpendingTips(overview, currency, today),
            )
        }
        Spacer(Modifier.height(12.dp))
        val income = overview.months.sumOf { it.income }
        val spent = overview.months.sumOf { it.expense }
        val left = if (income >= spent) {
            "saved ${Format.money(income - spent, currency)}"
        } else {
            "spent ${Format.money(spent - income, currency)} more than came in"
        }
        val over = if (allTime) "Since $since" else "In these 12 months"
        Note("$over: ${Format.money(income, currency)} in, ${Format.money(spent, currency)} out, $left.")
    }
}

/** For each year: its income, spending and what was left. */
internal fun yearTips(years: List<YearTotals>, currency: String, today: LocalDate): ImmutableList<ChartTip> =
    years.map { year ->
        val difference = year.income - year.expense
        ChartTip(
            title = year.year.toString() + if (year.year == today.year) ", so far" else "",
            rows = persistentListOf(
                TipRow("Income", Format.money(year.income, currency), Green),
                TipRow("Spending", Format.money(year.expense, currency), Red),
                if (difference >= 0) {
                    TipRow("Saved", Format.money(difference, currency), Color.Unspecified)
                } else {
                    TipRow("Spent more than came in", Format.money(-difference, currency), Color.Unspecified)
                },
            ),
        )
    }.toImmutableList()

/** The accounts that make up the total balance: in it, and in the main currency. */
private fun inTotal(overview: OverviewView, currency: String): Int =
    overview.accounts.count { it.includeInBalance && it.currency == currency } +
        overview.cards.count { it.account.includeInBalance && it.account.currency == currency }

/** For each month: its income, spending and what was left. */
internal fun incomeSpendingTips(
    overview: OverviewView,
    currency: String,
    today: LocalDate,
): ImmutableList<ChartTip> =
    overview.months.map { month ->
        val difference = month.income - month.expense
        ChartTip(
            title = Format.month(month.month) + if (month.month == YearMonth.from(today)) ", so far" else "",
            rows = persistentListOf(
                TipRow("Income", Format.money(month.income, currency), Green),
                TipRow("Spending", Format.money(month.expense, currency), Red),
                if (difference >= 0) {
                    TipRow("Saved", Format.money(difference, currency), Color.Unspecified)
                } else {
                    TipRow("Spent more than came in", Format.money(-difference, currency), Color.Unspecified)
                },
            ),
        )
    }.toImmutableList()

/** For each month: the total balance at its end, and how it changed from the month before. */
internal fun balanceTips(overview: OverviewView, currency: String, today: LocalDate): ImmutableList<ChartTip> =
    overview.netWorth.mapIndexed { index, point ->
        val changeRow = overview.netWorth.getOrNull(index - 1)?.let { before ->
            val change = point.value - before.value
            val name = Format.month(before.month.atDay(1))
            if (change >= 0) {
                TipRow("Up from $name", Format.money(change, currency), Color.Unspecified)
            } else {
                TipRow("Down from $name", Format.money(-change, currency), Color.Unspecified)
            }
        }
        ChartTip(
            title = if (point.month == YearMonth.from(today)) "Today" else "End of ${Format.month(point.month)}",
            rows = listOfNotNull(TipRow("Total balance", Format.money(point.value, currency), Ivy), changeRow)
                .toImmutableList(),
        )
    }.toImmutableList()

@Composable
private fun Accounts(accounts: ImmutableList<LedgerAccount>, modifier: Modifier = Modifier) {
    Panel(modifier = modifier, title = "Accounts", subtitle = "What's in each one today") {
        accounts.forEach { account ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Dot(colorOf(account.color))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = account.name, style = MaterialTheme.typography.bodyLarge)
                    if (!account.includeInBalance) Note("Not in the total balance")
                }
                Text(
                    text = Format.money(account.balance, account.currency),
                    style = NumberStyle,
                    color = if (account.balance < 0) Red else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun Cards(cards: ImmutableList<CardView>, today: LocalDate, modifier: Modifier = Modifier) {
    Panel(modifier = modifier, title = "Credit cards", subtitle = "What you owe on each card today") {
        cards.forEach { card ->
            val currency = card.account.currency
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(colorOf(card.account.color))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = card.account.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(text = "${Format.money(card.owed, currency)} owed", style = NumberStyle)
                }
                card.used?.let { used ->
                    Spacer(Modifier.height(8.dp))
                    ShareBar(share = used, color = usageColor(used))
                }
                val used = card.used?.let { "${Format.percent(it)} of the limit used" }
                val limit = card.account.creditLimit?.let { "Limit ${Format.money(it, currency)}" }
                val due = card.nextDue?.let { "Due ${Format.day(it, today)}" }
                Spacer(Modifier.height(6.dp))
                Note(listOfNotNull(used, limit, due).joinToString(" · "))
            }
        }
    }
}

/** Green, orange or red, like the card's bar on the phone. */
private fun usageColor(used: Double): Color = when {
    used < UsageWarning -> Green
    used < UsageHigh -> Orange
    else -> Red
}

/** Accounts in another currency are left out of totals until the app has exchange rates. */
@Composable
internal fun OtherCurrencies(ledger: Ledger, modifier: Modifier = Modifier) {
    if (ledger.otherCurrencies.isEmpty()) return
    Note(
        text = "Accounts in ${ledger.otherCurrencies.sorted().joinToString()} aren't included in totals yet.",
        color = Orange,
        modifier = modifier,
    )
}

private const val UsageWarning = 0.5

/** All time shows each month up to a year and a half, then each year */
private const val MonthsBeforeYears = 18
private const val UsageHigh = 0.8
internal val PagePadding = 32.dp
