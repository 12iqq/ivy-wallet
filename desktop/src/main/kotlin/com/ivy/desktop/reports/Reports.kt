package com.ivy.desktop.reports

import com.ivy.desktop.data.CategoryId
import com.ivy.desktop.data.Ledger
import com.ivy.desktop.data.LedgerAccount
import com.ivy.desktop.data.LedgerTransaction
import com.ivy.insights.FlowKind
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

/** What the Reports page is set to: a month or a year, spending or income, maybe one category. */
@Suppress("DataClassTypedIDs")
data class ReportChoice(
    val mode: PeriodMode,
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
    /** The last 12 months, oldest first */
    val months: List<MonthTotals>,
    val netWorth: List<NetWorthPoint>,
    val averageMonthlySpend: Double,
)

/** The phone's report maths ([ReportsCalculator], [InsightsCalculator], [RecurringCharges]) on a [Ledger]. */
object Reports {
    const val UNCATEGORIZED_NAME = "Uncategorized"

    fun period(choice: ReportChoice): ReportPeriod = when (choice.mode) {
        PeriodMode.Month -> ReportPeriod.month(choice.month)
        PeriodMode.Year -> ReportPeriod.year(choice.month.year)
    }

    /** The period before (-1) or after (+1) the one shown. */
    fun step(choice: ReportChoice, by: Long): ReportChoice = when (choice.mode) {
        PeriodMode.Month -> choice.copy(month = choice.month.plusMonths(by))
        PeriodMode.Year -> choice.copy(month = choice.month.plusYears(by))
    }

    fun report(ledger: Ledger, choice: ReportChoice, today: LocalDate): ReportView {
        val period = period(choice)
        val records = ledger.records
        val byId = ledger.transactions.associateBy { it.id.value }
        return ReportView(
            period = period,
            summary = ReportsCalculator.summary(records, choice.kind, period, choice.mode, today, choice.categoryKey),
            categories = ReportsCalculator.categoryShares(records, choice.kind, period).map { share ->
                val category = ledger.categories[CategoryId(share.key)]
                CategoryRow(
                    key = share.key,
                    name = category?.name ?: UNCATEGORIZED_NAME,
                    color = category?.color,
                    amount = share.amount,
                    count = share.count,
                    share = share.share,
                )
            },
            places = ReportsCalculator.topTitles(records, choice.kind, period, choice.categoryKey),
            chart = when (choice.mode) {
                PeriodMode.Month -> MonthPace(
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

                PeriodMode.Year -> YearMonths(
                    ReportsCalculator.monthsOfYear(records, choice.kind, choice.month.year, choice.categoryKey)
                )
            },
            transactions = ReportsCalculator.transactions(records, choice.kind, period, choice.categoryKey)
                .mapNotNull { record -> record.id?.let(byId::get) },
        )
    }

    fun overview(ledger: Ledger, today: LocalDate): OverviewView {
        val months = InsightsCalculator.monthlyTotals(ledger.records, today)
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
            netWorth = InsightsCalculator.netWorth(ledger.balanceDeltas, today),
            averageMonthlySpend = InsightsCalculator.averageMonthlySpend(months),
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
