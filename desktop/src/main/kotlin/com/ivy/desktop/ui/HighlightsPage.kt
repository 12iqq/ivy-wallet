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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ivy.desktop.data.Ledger
import com.ivy.desktop.reports.Csv
import com.ivy.desktop.reports.Format
import com.ivy.desktop.reports.Highlights
import com.ivy.desktop.reports.HighlightsCalculator
import com.ivy.desktop.reports.Reports
import com.ivy.desktop.reports.YearTotals
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** One fun fact: what it's about, the answer, and a line more. */
@Immutable
data class Fact(
    val label: String,
    val value: String,
    val note: String,
)

/** Everything since the first transaction: totals, fun facts, year by year, and when the money goes. */
@Composable
internal fun HighlightsPage(
    ledger: Ledger,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    val highlights = remember(ledger, today) { HighlightsCalculator.of(ledger, today) }
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
            if (highlights == null) {
                PageTitle(title = "Highlights", subtitle = "Nothing to show yet: there are no transactions.")
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PageTitle(
                        title = "Highlights",
                        subtitle = "Everything since your first transaction on " +
                            "${Format.day(highlights.since, today)}, in $currency",
                        modifier = Modifier.weight(1f),
                    )
                    ExportButton(
                        fileName = "Ivy Wallet - year by year.csv",
                        csv = { Csv.years(highlights.years.map { it.year }, ledger) },
                    )
                }
                OtherCurrencies(ledger)
                Totals(highlights, currency, today)
                facts(highlights, currency, today).chunked(FactsPerRow).forEach { row ->
                    FactRow(row.toImmutableList())
                }
                YearByYear(highlights.years.toImmutableList(), currency, today)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TopCategories(highlights, currency, Modifier.weight(1f))
                    Weekdays(highlights, currency, Modifier.weight(1f))
                }
                // purchases typed in by hand all get the time they were saved, so only show real times
                if (highlights.hoursSeen >= HoursWorthShowing) Hours(highlights, currency)
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
private fun Totals(highlights: Highlights, currency: String, today: LocalDate) {
    val months = Reports.monthsBetween(YearMonth.from(highlights.since), YearMonth.from(today)).size
    val left = highlights.received - highlights.spent
    Row(
        modifier = Modifier.height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Stat(
            label = "Keeping track for",
            value = Format.duration(highlights.since, today),
            note = "${highlights.days} days",
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
        Stat(
            label = "Transactions",
            value = "${highlights.count}",
            note = "About ${highlights.count / months.coerceAtLeast(1)} a month",
            info = "Income and spending in $currency. Transfers between your own accounts aren't counted.",
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
        Stat(
            label = "Spent in all",
            value = Format.money(highlights.spent, currency),
            note = "${Format.money(highlights.spent / months.coerceAtLeast(1), currency)} a month on average",
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
        Stat(
            label = "Received in all",
            value = Format.money(highlights.received, currency),
            note = if (left >= 0) {
                "${Format.money(left, currency)} more than you spent"
            } else {
                "${Format.money(-left, currency)} less than you spent"
            },
            noteColor = if (left >= 0) Green else Red,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
    }
}

/** The fun facts that the data has an answer for. */
internal fun facts(highlights: Highlights, currency: String, today: LocalDate): List<Fact> {
    val spent = highlights.spent.takeIf { it > 0 } ?: 1.0
    val busiest = DayOfWeek.entries.maxBy { highlights.weekdays[it.ordinal] }
    val weekend = highlights.weekdays[DayOfWeek.SATURDAY.ordinal] + highlights.weekdays[DayOfWeek.SUNDAY.ordinal]
    val hour = highlights.hours.indices.maxBy { highlights.hours[it] }
    return listOfNotNull(
        highlights.categories.firstOrNull()?.let { top ->
            Fact(
                "Where most of it goes",
                top.name,
                "${Format.percent(top.share)} of everything you spent: ${Format.money(top.amount, currency)}",
            )
        },
        highlights.mostOften?.let { place ->
            Fact(
                "Where you go most often",
                place.title,
                "${place.count} times, ${Format.money(place.amount, currency)} in all",
            )
        },
        highlights.mostSpentAt?.let { place ->
            Fact(
                "Where you spent the most",
                place.title,
                "${Format.money(place.amount, currency)} over ${Format.plural(place.count, "time")}",
            )
        },
        highlights.biggest?.let { trn ->
            Fact(
                "Your biggest purchase",
                Format.money(trn.amount, trn.currency),
                listOfNotNull(trn.title?.takeIf { it.isNotBlank() }, Format.day(trn.time.toLocalDate(), today))
                    .joinToString(", "),
            )
        },
        highlights.priciestMonth?.let { month ->
            Fact(
                "Your priciest month",
                Format.month(month.month),
                "${Format.money(month.expense, currency)} spent",
            )
        },
        highlights.cheapestMonth?.let { month ->
            Fact(
                "Your cheapest month",
                Format.month(month.month),
                "${Format.money(month.expense, currency)} spent",
            )
        },
        highlights.bestMonth?.let { month ->
            Fact(
                "Your best month for saving",
                Format.month(month.month),
                "${Format.money(month.income - month.expense, currency)} left over",
            )
        },
        Fact(
            "Your big spending day",
            "${Format.weekday(busiest)}s",
            "${Format.percent(highlights.weekdays[busiest.ordinal] / spent)} of your spending happens on " +
                "${Format.weekday(busiest)}s",
        ).takeIf { highlights.spent > 0 },
        Fact(
            "Days without spending",
            "${highlights.noSpendDays}",
            "${Format.percent(highlights.noSpendDays.toDouble() / highlights.days)} of the days since you started",
        ),
        Fact(
            "A typical purchase",
            Format.money(highlights.typicalPurchase, currency),
            "Half cost less than this. The average is ${Format.money(highlights.averagePurchase, currency)}, " +
                "pulled up by the big ones.",
        ).takeIf { highlights.averagePurchase > 0 },
        Fact(
            "Weekends",
            Format.percent(weekend / spent),
            "of your spending happens on Saturdays and Sundays",
        ).takeIf { highlights.spent > 0 },
        Fact(
            "Your busiest hour",
            "${Format.hour(hour)} to ${Format.hour((hour + 1) % highlights.hours.size)}",
            "${Format.percent(highlights.hours[hour] / spent)} of your spending happens then",
        ).takeIf { highlights.hoursSeen >= HoursWorthShowing },
    )
}

/** Fun facts side by side, all as tall as the tallest. */
@Composable
private fun FactRow(facts: ImmutableList<Fact>) {
    Row(
        modifier = Modifier.height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        facts.forEach { fact ->
            Panel(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                Text(
                    text = fact.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(text = fact.value, style = MaterialTheme.typography.titleLarge, maxLines = 2)
                Spacer(Modifier.height(4.dp))
                Note(fact.note)
            }
        }
        // keep the last row's facts as wide as the others
        repeat(FactsPerRow - facts.size) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun YearByYear(years: ImmutableList<YearTotals>, currency: String, today: LocalDate) {
    Panel(
        modifier = Modifier.fillMaxWidth(),
        title = "Year by year",
        subtitle = "What came in and what you spent each year. Point at a year to see it.",
    ) {
        Legend(persistentListOf("Income" to Green, "Spending" to Red))
        Spacer(Modifier.height(12.dp))
        BarChart(
            series = persistentListOf(
                years.map { it.income }.toImmutableList(),
                years.map { it.expense }.toImmutableList(),
            ),
            colors = persistentListOf(Green, Red),
            labels = years.map { it.year.toString() }.toImmutableList(),
            tips = yearTips(years, currency, today),
        )
        Spacer(Modifier.height(16.dp))
        TableRow(persistentListOf("Year", "Income", "Spending", "Saved", "Transactions"), header = true)
        years.reversed().forEach { year ->
            TableRow(
                persistentListOf(
                    year.year.toString() + if (year.year == today.year) " so far" else "",
                    Format.money(year.income, currency),
                    Format.money(year.expense, currency),
                    Format.money(year.income - year.expense, currency),
                    "${year.count}",
                ),
                header = false,
            )
        }
    }
}

@Composable
private fun TableRow(cells: ImmutableList<String>, header: Boolean) {
    Row(modifier = Modifier.padding(vertical = 6.dp)) {
        cells.forEachIndexed { index, cell ->
            Text(
                text = cell,
                style = if (header) MaterialTheme.typography.labelMedium else NumberStyle,
                color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textAlign = if (index == 0) TextAlign.Start else TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TopCategories(highlights: Highlights, currency: String, modifier: Modifier = Modifier) {
    Panel(
        modifier = modifier,
        title = "Top categories",
        subtitle = "Where your money has gone since you started. Reports → All time has every category.",
    ) {
        highlights.categories.take(TopCategoriesShown).forEach { row ->
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(colorOf(row.color))
                    Spacer(Modifier.width(10.dp))
                    Text(text = row.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Note("${row.count}×", modifier = Modifier.padding(end = 12.dp))
                    Text(text = Format.money(row.amount, currency), style = NumberStyle)
                    Text(
                        text = Format.percent(row.share),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(48.dp),
                    )
                }
                Spacer(Modifier.height(6.dp))
                ShareBar(share = row.share, color = colorOf(row.color))
            }
        }
    }
}

@Composable
private fun Weekdays(highlights: Highlights, currency: String, modifier: Modifier = Modifier) {
    val spent = highlights.spent.takeIf { it > 0 } ?: 1.0
    Panel(
        modifier = modifier,
        title = "By day of the week",
        subtitle = "Everything you've spent on each day of the week. Point at a day to see it.",
    ) {
        BarChart(
            series = persistentListOf(highlights.weekdays.toImmutableList()),
            colors = persistentListOf(Red),
            labels = DayOfWeek.entries.map { it.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) }.toImmutableList(),
            tips = DayOfWeek.entries.map { day ->
                val amount = highlights.weekdays[day.ordinal]
                ChartTip(
                    title = "${Format.weekday(day)}s",
                    rows = persistentListOf(
                        TipRow("Spent", Format.money(amount, currency), Red),
                        TipRow("Of all your spending", Format.percent(amount / spent), Color.Unspecified),
                    ),
                )
            }.toImmutableList(),
        )
    }
}

@Composable
private fun Hours(highlights: Highlights, currency: String) {
    val spent = highlights.spent.takeIf { it > 0 } ?: 1.0
    val hours = highlights.hours.indices
    Panel(
        modifier = Modifier.fillMaxWidth(),
        title = "By time of day",
        subtitle = "What you've spent in each hour of the day, by the time of each purchase. Point at an hour to " +
            "see it.",
    ) {
        BarChart(
            series = persistentListOf(highlights.hours.toImmutableList()),
            colors = persistentListOf(Ivy),
            labels = hours.map { if (it % HourLabelEvery == 0) Format.hour(it) else "" }.toImmutableList(),
            tips = hours.map { hour ->
                val amount = highlights.hours[hour]
                ChartTip(
                    title = "${Format.hour(hour)} to ${Format.hour((hour + 1) % hours.count())}",
                    rows = persistentListOf(
                        TipRow("Spent", Format.money(amount, currency), Ivy),
                        TipRow("Of all your spending", Format.percent(amount / spent), Color.Unspecified),
                    ),
                )
            }.toImmutableList(),
        )
    }
}

private const val FactsPerRow = 3
private const val TopCategoriesShown = 8
private const val HourLabelEvery = 3

/** Purchases in at least this many different hours: the times are real ones */
private const val HoursWorthShowing = 6
