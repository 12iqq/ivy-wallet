package com.ivy.insights

import java.time.LocalDate
import java.time.YearMonth

enum class FlowKind { Income, Expense }

/** An income or expense, already converted to the base currency. */
@Suppress("DataClassTypedIDs")
data class FlowRecord(
    val date: LocalDate,
    val kind: FlowKind,
    val amount: Double,
    val categoryId: String?,
)

/** A change to the total balance (net worth) on [date]. */
data class BalanceDelta(
    val date: LocalDate,
    val amount: Double,
)

data class MonthTotals(
    val month: YearMonth,
    val income: Double,
    val expense: Double,
)

data class NetWorthPoint(
    val month: YearMonth,
    val value: Double,
)

@Suppress("DataClassTypedIDs")
data class CategoryChange(
    val categoryId: String?,
    val thisMonth: Double,
    val lastMonth: Double,
)

/** Pure aggregations behind the Insights charts. */
object InsightsCalculator {
    const val DEFAULT_MONTHS = 12
    const val TOP_CATEGORIES = 6

    /** Income and expenses for each of the last [months] calendar months (oldest first). */
    fun monthlyTotals(
        records: List<FlowRecord>,
        today: LocalDate,
        months: Int = DEFAULT_MONTHS,
    ): List<MonthTotals> {
        val byMonth = records.groupBy { YearMonth.from(it.date) }
        return lastMonths(today, months).map { month ->
            val inMonth = byMonth[month].orEmpty()
            MonthTotals(
                month = month,
                income = inMonth.filter { it.kind == FlowKind.Income }.sumOf { it.amount },
                expense = inMonth.filter { it.kind == FlowKind.Expense }.sumOf { it.amount },
            )
        }
    }

    /** Total balance at the end of each of the last [months] months (oldest first). */
    fun netWorth(
        deltas: List<BalanceDelta>,
        today: LocalDate,
        months: Int = DEFAULT_MONTHS,
    ): List<NetWorthPoint> {
        val sorted = deltas.sortedBy { it.date }
        var index = 0
        var running = 0.0
        return lastMonths(today, months).map { month ->
            val end = month.atEndOfMonth()
            while (index < sorted.size && !sorted[index].date.isAfter(end)) {
                running += sorted[index].amount
                index++
            }
            NetWorthPoint(month, running)
        }
    }

    /** Biggest spending categories this month, next to what they were last month. */
    fun categoryChanges(
        records: List<FlowRecord>,
        today: LocalDate,
        limit: Int = TOP_CATEGORIES,
    ): List<CategoryChange> {
        val thisMonth = YearMonth.from(today)
        val lastMonth = thisMonth.minusMonths(1)
        val expenses = records.filter { it.kind == FlowKind.Expense }
        fun spentBy(month: YearMonth): Map<String?, Double> = expenses
            .filter { YearMonth.from(it.date) == month }
            .groupBy { it.categoryId }
            .mapValues { (_, list) -> list.sumOf { it.amount } }

        val now = spentBy(thisMonth)
        val before = spentBy(lastMonth)
        return (now.keys + before.keys)
            .map { CategoryChange(it, now[it] ?: 0.0, before[it] ?: 0.0) }
            .sortedByDescending { maxOf(it.thisMonth, it.lastMonth) }
            .take(limit)
    }

    /** Average monthly spending over the full months in [totals] (ignores the current, partial month). */
    fun averageMonthlySpend(totals: List<MonthTotals>): Double {
        val full = totals.dropLast(1).filter { it.expense > 0 || it.income > 0 }
        return if (full.isEmpty()) 0.0 else full.sumOf { it.expense } / full.size
    }

    private fun lastMonths(today: LocalDate, months: Int): List<YearMonth> {
        val current = YearMonth.from(today)
        return (months - 1 downTo 0).map { current.minusMonths(it.toLong()) }
    }
}
