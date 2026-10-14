package com.ivy.insights

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

enum class PeriodMode { Month, Year }

/** An inclusive date range shown by the report. */
data class ReportPeriod(
    val start: LocalDate,
    val end: LocalDate,
) {
    companion object {
        fun month(month: YearMonth): ReportPeriod = ReportPeriod(month.atDay(1), month.atEndOfMonth())
        fun year(year: Int): ReportPeriod {
            val start = LocalDate.of(year, 1, 1)
            return ReportPeriod(start, start.plusYears(1).minusDays(1))
        }
    }
}

operator fun ReportPeriod.contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)

data class ReportSummary(
    val total: Double,
    val previousTotal: Double,
    val count: Int,
    val dailyAverage: Double,
) {
    /** Change vs the previous period in percent, or null when there's nothing to compare with. */
    val changePercent: Int?
        get() = if (previousTotal > 0) {
            ((total - previousTotal) / previousTotal * PERCENT).toInt()
        } else {
            null
        }

    private companion object {
        const val PERCENT = 100
    }
}

/** One slice of the category donut. [key] is the category id or [ReportsCalculator.UNCATEGORIZED]. */
@Suppress("DataClassTypedIDs")
data class CategoryShare(
    val key: String,
    val amount: Double,
    val count: Int,
    val share: Double,
)

data class TitleTotal(
    val title: String,
    val amount: Double,
    val count: Int,
)

/**
 * Pure maths behind the Reports screen (inspired by Cashew's spending page):
 * totals for a period, category shares, top places, spending pace and history.
 */
object ReportsCalculator {
    const val UNCATEGORIZED = "uncategorized"
    const val TOP_TITLES = 8

    fun categoryKey(record: FlowRecord): String = record.categoryId ?: UNCATEGORIZED

    /** The records of one kind in [period], optionally only one category. */
    fun select(
        records: List<FlowRecord>,
        kind: FlowKind,
        period: ReportPeriod,
        categoryKey: String? = null,
    ): List<FlowRecord> = records.filter {
        it.kind == kind && it.date in period && (categoryKey == null || categoryKey(it) == categoryKey)
    }

    fun previous(period: ReportPeriod, mode: PeriodMode): ReportPeriod = when (mode) {
        PeriodMode.Month -> ReportPeriod.month(YearMonth.from(period.start).minusMonths(1))
        PeriodMode.Year -> ReportPeriod.year(period.start.year - 1)
    }

    fun summary(
        records: List<FlowRecord>,
        kind: FlowKind,
        period: ReportPeriod,
        mode: PeriodMode,
        today: LocalDate,
        categoryKey: String? = null,
    ): ReportSummary {
        val current = select(records, kind, period, categoryKey)
        val previous = select(records, kind, previous(period, mode), categoryKey)
        val total = current.sumOf { it.amount }
        // a running period is averaged over the days so far, not the whole month/year
        val lastDay = if (today in period) today else period.end
        val days = ChronoUnit.DAYS.between(period.start, lastDay) + 1
        return ReportSummary(
            total = total,
            previousTotal = previous.sumOf { it.amount },
            count = current.size,
            dailyAverage = if (days > 0 && !period.start.isAfter(today)) total / days else 0.0,
        )
    }

    /** Category shares of [kind] in [period], biggest first. */
    fun categoryShares(records: List<FlowRecord>, kind: FlowKind, period: ReportPeriod): List<CategoryShare> {
        val selected = select(records, kind, period)
        val total = selected.sumOf { it.amount }
        if (total <= 0.0) return emptyList()
        return selected.groupBy(::categoryKey)
            .map { (key, list) ->
                val amount = list.sumOf { it.amount }
                CategoryShare(key = key, amount = amount, count = list.size, share = amount / total)
            }
            .sortedByDescending { it.amount }
    }

    /** Where the money went (or came from), by transaction title. */
    fun topTitles(
        records: List<FlowRecord>,
        kind: FlowKind,
        period: ReportPeriod,
        categoryKey: String? = null,
        limit: Int = TOP_TITLES,
    ): List<TitleTotal> = select(records, kind, period, categoryKey)
        .filter { !it.title.isNullOrBlank() }
        .groupBy { it.title!!.trim().lowercase() }
        .values
        .map { group ->
            // show the spelling used most often
            val title = group.groupingBy { it.title!!.trim() }.eachCount().maxBy { it.value }.key
            TitleTotal(title = title, amount = group.sumOf { it.amount }, count = group.size)
        }
        .sortedByDescending { it.amount }
        .take(limit)

    /**
     * Running total for each day of [month]. For the current month the list stops at
     * [today], so the line shows how far into the month we are.
     */
    fun cumulativeByDay(
        records: List<FlowRecord>,
        kind: FlowKind,
        month: YearMonth,
        today: LocalDate,
        categoryKey: String? = null,
    ): List<Double> {
        val perDay = select(records, kind, ReportPeriod.month(month), categoryKey)
            .groupBy { it.date.dayOfMonth }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
        val lastDay = when {
            YearMonth.from(today) == month -> today.dayOfMonth
            month.isAfter(YearMonth.from(today)) -> 0
            else -> month.lengthOfMonth()
        }
        var running = 0.0
        return (1..lastDay).map { day ->
            running += perDay[day] ?: 0.0
            running
        }
    }

    /** Totals for each month of [year] (12 values). */
    fun monthsOfYear(
        records: List<FlowRecord>,
        kind: FlowKind,
        year: Int,
        categoryKey: String? = null,
    ): List<Double> {
        val byMonth = select(records, kind, ReportPeriod.year(year), categoryKey)
            .groupBy { it.date.monthValue }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
        return (1..MONTHS_IN_YEAR).map { byMonth[it] ?: 0.0 }
    }

    /** Newest first. */
    fun transactions(
        records: List<FlowRecord>,
        kind: FlowKind,
        period: ReportPeriod,
        categoryKey: String? = null,
    ): List<FlowRecord> = select(records, kind, period, categoryKey)
        .sortedWith(compareByDescending<FlowRecord> { it.date }.thenByDescending { it.epochMillis })

    private const val MONTHS_IN_YEAR = 12
}
