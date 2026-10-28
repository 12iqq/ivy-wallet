package com.ivy.desktop.reports

import com.ivy.desktop.data.CategoryId
import com.ivy.desktop.data.Ledger
import com.ivy.desktop.data.LedgerAccount
import com.ivy.desktop.data.LedgerTransaction
import com.ivy.insights.FlowKind
import com.ivy.insights.FlowRecord
import com.ivy.insights.InsightsCalculator
import com.ivy.insights.MonthTotals
import com.ivy.insights.NetWorthPoint
import com.ivy.insights.PeriodMode
import com.ivy.insights.ReportPeriod
import com.ivy.insights.ReportSummary
import com.ivy.insights.ReportsCalculator
import com.ivy.insights.TitleTotal
import com.ivy.insights.recurring.RecurringCharge
import com.ivy.insights.recurring.RecurringCharges
import java.time.LocalDate
import java.time.YearMonth

/** How much a report covers: a month, a year, or everything since the first transaction. */
enum class Span { Month, Year, AllTime }

/** What the Reports page is set to: a month, a year or all time, spending or income, maybe one category. */
@Suppress("DataClassTypedIDs")
data class ReportChoice(
    val span: Span,
    /** The month shown, or a month in the year shown */
    val month: YearMonth,
    val kind: FlowKind,
    /** A category id or [ReportsCalculator.UNCATEGORIZED]; null for all categories */
    val categoryKey: String?,
)

@Suppress("DataClassTypedIDs")
data class CategoryRow(
    /** A category id or [ReportsCalculator.UNCATEGORIZED] */
    val key: String,
    val name: String,
    val color: Int?,
    val amount: Double,
    val count: Int,
    val share: Double,
)

sealed interface ReportChart

/** Month: the running total for each day so far, and for the month before. */
data class MonthPace(
    val current: List<Double>,
    val previous: List<Double>,
    val daysInMonth: Int,
) : ReportChart

/** Year: the total of each month. */
data class YearMonths(
    val totals: List<Double>,
) : ReportChart

/** All time: the total of every month since the first transaction. */
data class EveryMonth(
    val months: List<YearMonth>,
    val totals: List<Double>,
) : ReportChart

/** A year's income and spending, and how many transactions there were. */
data class YearTotals(
    val year: Int,
    val income: Double,
    val expense: Double,
    val count: Int,
)

data class ReportView(
    val period: ReportPeriod,
    val summary: ReportSummary,
    val categories: List<CategoryRow>,
    val places: List<TitleTotal>,
    val chart: ReportChart,
    /** Newest first */
    val transactions: List<LedgerTransaction>,
)

/** A credit card with what it owes. */
data class CardView(
    val account: LedgerAccount,
    val owed: Double,
    /** Of the credit limit, 0 to 1; null without a limit */
    val used: Double?,
    val nextDue: LocalDate?,
)

data class OverviewView(
    val totalBalance: Double,
    val accounts: List<LedgerAccount>,
    val cards: List<CardView>,
    /** The last 12 months, or every month since the first transaction; oldest first */
    val months: List<MonthTotals>,
    val netWorth: List<NetWorthPoint>,
    val averageMonthlySpend: Double,
    /** How many months [averageMonthlySpend] is over: the full months before this one that have anything */
    val averageMonths: Int,
)

/** The phone's report maths ([ReportsCalculator], [InsightsCalculator], [RecurringCharges]) on a [Ledger]. */
object Reports {
    const val UNCATEGORIZED_NAME = "Uncategorized"

    /** The days a choice covers; all time is from the first transaction to today. */
    fun period(choice: ReportChoice, ledger: Ledger, today: LocalDate): ReportPeriod = when (choice.span) {
        Span.Month -> ReportPeriod.month(choice.month)
        Span.Year -> ReportPeriod.year(choice.month.year)
        Span.AllTime -> allTime(ledger, today)
    }

    /** From the first transaction to today. */
    fun allTime(ledger: Ledger, today: LocalDate): ReportPeriod =
        ReportPeriod(minOf(ledger.since ?: today, today), today)

    /** The month or year before (-1) or after (+1) the one shown. All time stays as it is. */
    fun step(choice: ReportChoice, by: Long): ReportChoice = when (choice.span) {
        Span.Month -> choice.copy(month = choice.month.plusMonths(by))
        Span.Year -> choice.copy(month = choice.month.plusYears(by))
        Span.AllTime -> choice
    }

    fun report(ledger: Ledger, choice: ReportChoice, today: LocalDate): ReportView {
        val period = period(choice, ledger, today)
        val records = ledger.records
        val byId = ledger.transactions.associateBy { it.id.value }
        // all time compares with the year before the first transaction, which has nothing: no change shown
        val mode = if (choice.span == Span.Month) PeriodMode.Month else PeriodMode.Year
        return ReportView(
            period = period,
            summary = ReportsCalculator.summary(records, choice.kind, period, mode, today, choice.categoryKey),
            categories = categoryRows(ledger, choice.kind, period),
            places = ReportsCalculator.topTitles(records, choice.kind, period, choice.categoryKey),
            chart = when (choice.span) {
                Span.Month -> MonthPace(
                    current = ReportsCalculator.cumulativeByDay(
                        records,
                        choice.kind,
                        choice.month,
                        today,
                        choice.categoryKey
                    ),
                    previous = ReportsCalculator.cumulativeByDay(
                        records,
                        choice.kind,
                        choice.month.minusMonths(1),
                        today,
                        choice.categoryKey
                    ),
                    daysInMonth = choice.month.lengthOfMonth(),
                )

                Span.Year -> YearMonths(
                    ReportsCalculator.monthsOfYear(records, choice.kind, choice.month.year, choice.categoryKey)
                )

                Span.AllTime -> everyMonth(records, choice.kind, period, choice.categoryKey)
            },
            transactions = ReportsCalculator.transactions(records, choice.kind, period, choice.categoryKey)
                .mapNotNull { record -> record.id?.let(byId::get) },
        )
    }

    /** What each category adds up to in [period], biggest first. */
    fun categoryRows(ledger: Ledger, kind: FlowKind, period: ReportPeriod): List<CategoryRow> =
        ReportsCalculator.categoryShares(ledger.records, kind, period).map { share ->
            val category = ledger.categories[CategoryId(share.key)]
            CategoryRow(
                key = share.key,
                name = category?.name ?: UNCATEGORIZED_NAME,
                color = category?.color,
                amount = share.amount,
                count = share.count,
                share = share.share,
            )
        }

    /** Each month from [from] to [to]. */
    fun monthsBetween(from: YearMonth, to: YearMonth): List<YearMonth> =
        generateSequence(from) { it.plusMonths(1) }.takeWhile { !it.isAfter(to) }.toList()

    private fun everyMonth(
        records: List<FlowRecord>,
        kind: FlowKind,
        period: ReportPeriod,
        categoryKey: String?,
    ): EveryMonth {
        val byMonth = ReportsCalculator.select(records, kind, period, categoryKey)
            .groupBy { YearMonth.from(it.date) }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
        val months = monthsBetween(YearMonth.from(period.start), YearMonth.from(period.end))
        return EveryMonth(months = months, totals = months.map { byMonth[it] ?: 0.0 })
    }

    /** Each year's income, spending and number of transactions, up to [today]; oldest first. */
    fun years(ledger: Ledger, today: LocalDate): List<YearTotals> = ledger.records
        .filter { !it.date.isAfter(today) }
        .groupBy { it.date.year }
        .toSortedMap()
        .map { (year, list) ->
            YearTotals(
                year = year,
                income = list.filter { it.kind == FlowKind.Income }.sumOf { it.amount },
                expense = list.filter { it.kind == FlowKind.Expense }.sumOf { it.amount },
                count = list.size,
            )
        }

    /**
     * Balances and cards today, and the months for the charts: the last 12, or with [allTime] every month
     * since the first transaction.
     */
    fun overview(ledger: Ledger, today: LocalDate, allTime: Boolean): OverviewView {
        val count = if (allTime) {
            monthsBetween(YearMonth.from(ledger.since ?: today), YearMonth.from(today)).size.coerceAtLeast(2)
        } else {
            InsightsCalculator.DEFAULT_MONTHS
        }
        val months = InsightsCalculator.monthlyTotals(ledger.records, today, count)
        return OverviewView(
            totalBalance = ledger.accounts
                .filter { it.includeInBalance && it.currency == ledger.baseCurrency }
                .sumOf { it.balance },
            accounts = ledger.accounts.filter { !it.creditCard },
            cards = ledger.accounts.filter { it.creditCard }.map { card ->
                val owed = (-card.balance).coerceAtLeast(0.0)
                CardView(
                    account = card,
                    owed = owed,
                    used = card.creditLimit?.takeIf { it > 0 }?.let { (owed / it).coerceIn(0.0, 1.0) },
                    nextDue = card.paymentDueDay?.let { nextDue(it, today) },
                )
            },
            months = months,
            netWorth = InsightsCalculator.netWorth(ledger.balanceDeltas, today, count),
            averageMonthlySpend = InsightsCalculator.averageMonthlySpend(months),
            averageMonths = months.dropLast(1).count { it.expense > 0 || it.income > 0 },
        )
    }

    /** Newest first. Matches the name, the note, the category or the account. */
    fun search(ledger: Ledger, query: String): List<LedgerTransaction> {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return ledger.transactions
        return ledger.transactions.filter { trn ->
            val text = listOfNotNull(
                trn.title,
                trn.description,
                trn.accountName,
                trn.toAccountName,
                trn.categoryId?.let { ledger.categories[it]?.name },
            ).joinToString(" ").lowercase()
            words.all { it in text }
        }
    }

    /** Active ones first, then by what they cost a month. */
    fun recurring(ledger: Ledger, today: LocalDate): List<RecurringCharge> =
        RecurringCharges.find(ledger.charges, today, ledger.plannedNames)

    /** The next due date on [day] of the month, today or later; short months use their last day. */
    fun nextDue(day: Int, today: LocalDate): LocalDate {
        val thisMonth = YearMonth.from(today)
        val due = thisMonth.atDay(day.coerceIn(1, thisMonth.lengthOfMonth()))
        if (!due.isBefore(today)) return due
        val next = thisMonth.plusMonths(1)
        return next.atDay(day.coerceIn(1, next.lengthOfMonth()))
    }
}
