package com.ivy.desktop.reports

import com.ivy.desktop.data.Ledger
import com.ivy.desktop.data.LedgerTransaction
import com.ivy.desktop.data.TransactionType
import com.ivy.insights.FlowKind
import com.ivy.insights.MonthTotals
import com.ivy.insights.ReportsCalculator
import com.ivy.insights.TitleTotal
import com.ivy.insights.recurring.RecurringCharges
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Facts about everything since the first transaction, in the main currency like the reports. */
data class Highlights(
    /** The day of the first transaction */
    val since: LocalDate,
    /** From [since] to today, both counted */
    val days: Int,
    /** Incomes and expenses */
    val count: Int,
    val spent: Double,
    val received: Double,
    /** Spending by category, biggest first */
    val categories: List<CategoryRow>,
    /** The name spent at most often */
    val mostOften: TitleTotal?,
    /** The name spent at the most */
    val mostSpentAt: TitleTotal?,
    val biggest: LedgerTransaction?,
    /** The month with the most spending, this one too */
    val priciestMonth: MonthTotals?,
    /** The finished month with the least spending, of the months with any */
    val cheapestMonth: MonthTotals?,
    /** The finished month with the most left over */
    val bestMonth: MonthTotals?,
    /** Spending on each day of the week, Monday first */
    val weekdays: List<Double>,
    /** Spending in each hour of the day, from midnight */
    val hours: List<Double>,
    /** In how many different hours there were purchases: few means the times aren't real */
    val hoursSeen: Int,
    val noSpendDays: Int,
    /** Half of the purchases cost less than this */
    val typicalPurchase: Double,
    val averagePurchase: Double,
    val years: List<YearTotals>,
)

object HighlightsCalculator {
    private const val HOURS_IN_DAY = 24
    private const val HALF = 2

    // Ivy's own balance corrections aren't purchases
    private val NotPurchases = setOf("adjust balance")

    fun of(ledger: Ledger, today: LocalDate): Highlights? {
        val since = ledger.since?.takeIf { !it.isAfter(today) } ?: return null
        val period = Reports.allTime(ledger, today)
        val records = ledger.records.filter { !it.date.isBefore(since) && !it.date.isAfter(today) }
        val expenses = records.filter { it.kind == FlowKind.Expense }
        val incomes = records.filter { it.kind == FlowKind.Income }
        val places = ReportsCalculator.topTitles(ledger.records, FlowKind.Expense, period, limit = Int.MAX_VALUE)
            .filter { RecurringCharges.key(it.title) !in NotPurchases }
        val purchases = ledger.transactions.filter { trn ->
            trn.type == TransactionType.EXPENSE &&
                trn.currency == ledger.baseCurrency &&
                !trn.time.toLocalDate().isBefore(since) &&
                !trn.time.toLocalDate().isAfter(today) &&
                RecurringCharges.key(trn.title.orEmpty()) !in NotPurchases
        }
        val months = months(ledger, since, today)
        val finished = months.dropLast(1).filter { it.income > 0 || it.expense > 0 }
        val days = (ChronoUnit.DAYS.between(since, today) + 1).toInt()
        val amounts = purchases.map { it.amount }.sorted()
        val byWeekday = expenses.groupBy { it.date.dayOfWeek }.mapValues { (_, list) -> list.sumOf { it.amount } }
        val byHour = purchases.groupBy { it.time.hour }.mapValues { (_, list) -> list.sumOf { it.amount } }
        return Highlights(
            since = since,
            days = days,
            count = records.size,
            spent = expenses.sumOf { it.amount },
            received = incomes.sumOf { it.amount },
            categories = Reports.categoryRows(ledger, FlowKind.Expense, period),
            mostOften = places.maxWithOrNull(compareBy<TitleTotal> { it.count }.thenBy { it.amount }),
            mostSpentAt = places.firstOrNull(),
            biggest = purchases.maxByOrNull { it.amount },
            priciestMonth = months.filter { it.expense > 0 }.maxByOrNull { it.expense },
            cheapestMonth = finished.filter { it.expense > 0 }.minByOrNull { it.expense },
            bestMonth = finished.filter { it.income > it.expense }.maxByOrNull { it.income - it.expense },
            weekdays = DayOfWeek.entries.map { byWeekday[it] ?: 0.0 },
            hours = (0 until HOURS_IN_DAY).map { byHour[it] ?: 0.0 },
            hoursSeen = byHour.size,
            noSpendDays = days - expenses.map { it.date }.distinct().size,
            typicalPurchase = median(amounts),
            averagePurchase = if (amounts.isEmpty()) 0.0 else amounts.average(),
            years = Reports.years(ledger, today),
        )
    }

    private fun months(ledger: Ledger, since: LocalDate, today: LocalDate): List<MonthTotals> {
        val byMonth = ledger.records.groupBy { YearMonth.from(it.date) }
        return Reports.monthsBetween(YearMonth.from(since), YearMonth.from(today)).map { month ->
            val inMonth = byMonth[month].orEmpty()
            MonthTotals(
                month = month,
                income = inMonth.filter { it.kind == FlowKind.Income }.sumOf { it.amount },
                expense = inMonth.filter { it.kind == FlowKind.Expense }.sumOf { it.amount },
            )
        }
    }

    /** The middle one of [sorted], or between the two middle ones. */
    private fun median(sorted: List<Double>): Double = when {
        sorted.isEmpty() -> 0.0
        sorted.size % HALF == 1 -> sorted[sorted.size / HALF]
        else -> (sorted[sorted.size / HALF - 1] + sorted[sorted.size / HALF]) / HALF
    }
}
