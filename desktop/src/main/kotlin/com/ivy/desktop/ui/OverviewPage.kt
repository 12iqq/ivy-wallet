package com.ivy.desktop.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ivy.desktop.data.Ledger
import com.ivy.desktop.data.LedgerAccount
import com.ivy.desktop.reports.CardView
import com.ivy.desktop.reports.Format
import com.ivy.desktop.reports.OverviewView
import com.ivy.desktop.reports.Reports
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import java.time.LocalDate

@Composable
internal fun OverviewPage(
    ledger: Ledger,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    val overview = remember(ledger, today) { Reports.overview(ledger, today) }
    val scroll = rememberScrollState()
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(PagePadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PageTitle(title = "Overview")
            OtherCurrencies(ledger)
            Totals(overview, ledger.baseCurrency)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Accounts(overview.accounts.toImmutableList(), Modifier.weight(1f))
                if (overview.cards.isNotEmpty()) {
                    Cards(overview.cards.toImmutableList(), today, Modifier.weight(1f))
                }
            }
            Panel(title = "Income and spending, last 12 months") {
                Legend(persistentListOf("Income" to Green, "Spending" to Red))
                Spacer(Modifier.height(12.dp))
                BarChart(
                    series = persistentListOf(
                        overview.months.map { it.income }.toImmutableList(),
                        overview.months.map { it.expense }.toImmutableList(),
                    ),
                    colors = persistentListOf(Green, Red),
                    labels = overview.months.map { Format.month(it.month.atDay(1)) }.toImmutableList(),
                )
            }
            Panel(title = "Total balance over time") {
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
                    labels = overview.netWorth.map { Format.month(it.month.atDay(1)) }.toImmutableList(),
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
private fun Totals(overview: OverviewView, currency: String) {
    val thisMonth = overview.months.last()
    val lastMonth = overview.months[overview.months.size - 2]
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Stat(
            label = "Total balance",
            value = Format.money(overview.totalBalance, currency),
            modifier = Modifier.weight(1f),
        )
        Stat(
            label = "Spent this month",
            value = Format.money(thisMonth.expense, currency),
            note = "Last month ${Format.money(lastMonth.expense, currency)}",
            modifier = Modifier.weight(1f),
        )
        Stat(
            label = "Income this month",
            value = Format.money(thisMonth.income, currency),
            note = "Last month ${Format.money(lastMonth.income, currency)}",
            modifier = Modifier.weight(1f),
        )
        Stat(
            label = "Spending in an average month",
            value = Format.money(overview.averageMonthlySpend, currency),
            note = "Over the last full months",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Accounts(accounts: ImmutableList<LedgerAccount>, modifier: Modifier = Modifier) {
    Panel(modifier = modifier, title = "Accounts") {
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
    Panel(modifier = modifier, title = "Credit cards") {
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
                val limit = card.account.creditLimit?.let { "Limit ${Format.money(it, currency)}" }
                val due = card.nextDue?.let { "Due ${Format.day(it, today)}" }
                Spacer(Modifier.height(6.dp))
                Note(listOfNotNull(limit, due).joinToString(" · "))
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
private const val UsageHigh = 0.8
internal val PagePadding = 32.dp
