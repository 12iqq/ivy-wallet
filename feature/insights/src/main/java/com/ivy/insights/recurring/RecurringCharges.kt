package com.ivy.insights.recurring

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

/** One expense in your history, as [RecurringCharges] sees it. */
@Suppress("DataClassTypedIDs")
data class Charge(
    val name: String,
    /** In the base currency, for totals. */
    val amount: Double,
    /** In its account's currency, for planning it. */
    val accountAmount: Double,
    val date: LocalDate,
    val categoryId: String?,
    val accountId: String?,
    /** Paid as part of a planned payment. */
    val planned: Boolean,
)

/** How often a charge comes back. */
enum class Cadence(
    private val months: Long,
    private val weeks: Long,
    /** How many days early or late a charge can be and still be on time. */
    val toleranceDays: Long,
    val perYear: Double,
    /** The most recent periods looked at, and how many of them must have had the charge. */
    val lookBack: Int,
    val minPeriods: Int,
) {
    Weekly(months = 0, weeks = 1, toleranceDays = 1, perYear = 52.0, lookBack = 8, minPeriods = 4),
    Monthly(months = 1, weeks = 0, toleranceDays = 5, perYear = 12.0, lookBack = 6, minPeriods = 3),
    Quarterly(months = 3, weeks = 0, toleranceDays = 10, perYear = 4.0, lookBack = 4, minPeriods = 3),
    Yearly(months = 12, weeks = 0, toleranceDays = 20, perYear = 1.0, lookBack = 3, minPeriods = 2);

    fun before(date: LocalDate, periods: Long): LocalDate =
        if (weeks > 0) date.minusWeeks(weeks * periods) else date.minusMonths(months * periods)

    fun after(date: LocalDate): LocalDate = if (weeks > 0) date.plusWeeks(weeks) else date.plusMonths(months)
}

/** A charge that keeps coming back: a subscription, a bill, a fee. */
@Suppress("DataClassTypedIDs")
data class RecurringCharge(
    val key: String,
    val name: String,
    val cadence: Cadence,
    /** The price; for an amount that varies (a bill), its usual amount. */
    val amount: Double,
    val varies: Boolean,
    /** The price before it changed, when it changed lately. */
    val previousAmount: Double?,
    val perMonth: Double,
    /** What to plan it for, in its account's currency. */
    val planAmount: Double,
    val lastDate: LocalDate,
    val nextDate: LocalDate,
    val categoryId: String?,
    val accountId: String?,
    val planned: Boolean,
    /** Charged when it was expected; else it may have stopped. */
    val active: Boolean,
    /** Newest first. */
    val charges: List<Charge>,
)

/**
 * Finds the charges in your history that come back on a schedule: same name, about every week,
 * month, three months or year, and most of that name's charges on that schedule (a shop you
 * visit often isn't a subscription).
 */
@Suppress("MagicNumber")
object RecurringCharges {
    private const val ON_TIME_SHARE = 0.75
    private const val ON_SCHEDULE_SHARE = 0.7
    private const val SAME_PRICE = 0.02

    /** Seen only this few times, it counts only at about the same price. */
    private const val FEW_CHARGES = 3
    private const val ABOUT_THE_SAME = 1.2

    // Ivy's own balance corrections (see WalletAccountLogic) aren't charges
    private val notCharges = setOf("adjust balance")

    fun key(name: String): String = name.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

    /** Active ones first, by what they cost a month; then the ones that seem to have stopped. */
    fun find(
        charges: List<Charge>,
        today: LocalDate,
        plannedNames: Set<String> = emptySet(),
    ): List<RecurringCharge> = charges
        .filter { it.name.isNotBlank() && it.amount > 0 && !it.date.isAfter(today) }
        .groupBy { key(it.name) }
        .filterKeys { it !in notCharges }
        .mapNotNull { (key, group) ->
            val sorted = group.sortedBy { it.date }
            // stopped over a year ago: not worth listing
            if (sorted.last().date.isBefore(today.minusMonths(13))) return@mapNotNull null
            Cadence.entries.firstNotNullOfOrNull { detect(key, sorted, it, today, plannedNames) }
        }
        .sortedWith(compareByDescending<RecurringCharge> { it.active }.thenByDescending { it.perMonth })

    @Suppress("ReturnCount")
    private fun detect(
        key: String,
        group: List<Charge>,
        cadence: Cadence,
        today: LocalDate,
        plannedNames: Set<String>,
    ): RecurringCharge? {
        val last = group.last()
        val since = group.first().date.minusDays(cadence.toleranceDays)
        // the periods you could have been charged in, back from the last charge
        val expected = (0 until cadence.lookBack)
            .map { cadence.before(last.date, it.toLong()) }
            .takeWhile { !it.isBefore(since) }
        if (expected.size < cadence.minPeriods) return null
        val onTime = expected.mapNotNull { date ->
            group.filter { daysApart(it.date, date) <= cadence.toleranceDays }.minByOrNull { daysApart(it.date, date) }
        }.distinct()
        if (onTime.size < expected.size * ON_TIME_SHARE) return null
        // twice a year apart is little to go on (a café on your birthday): only at about the same price
        if (onTime.size < FEW_CHARGES && onTime.maxOf { it.amount } > onTime.minOf { it.amount } * ABOUT_THE_SAME) {
            return null
        }
        val from = expected.last().minusDays(cadence.toleranceDays)
        if (onTime.size < group.count { !it.date.isBefore(from) } * ON_SCHEDULE_SHARE) return null

        val newestFirst = onTime.sortedByDescending { it.date }
        val prices = priceLevels(newestFirst.reversed().map { it.amount })
        val varies = prices.size > 2
        val amount = if (varies) median(newestFirst.map { it.amount }) else last.amount
        val next = cadence.after(last.date)
        return RecurringCharge(
            key = key,
            name = last.name.trim(),
            cadence = cadence,
            amount = amount,
            varies = varies,
            previousAmount = prices.takeIf { it.size == 2 }?.first(),
            perMonth = amount * cadence.perYear / 12,
            planAmount = if (varies) median(newestFirst.map { it.accountAmount }) else last.accountAmount,
            lastDate = last.date,
            nextDate = next,
            categoryId = last.categoryId,
            accountId = last.accountId,
            planned = newestFirst.any { it.planned } || key in plannedNames,
            active = !today.isAfter(next.plusDays(cadence.toleranceDays * 2)),
            charges = newestFirst,
        )
    }

    /** The prices in a row of amounts, oldest first: 64.43, 74.99, 74.99 is two (it went up once). */
    private fun priceLevels(amounts: List<Double>): List<Double> = amounts.fold(emptyList()) { levels, amount ->
        val current = levels.lastOrNull()
        if (current != null && abs(amount - current) <= current * SAME_PRICE) levels else levels + amount
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
    }

    private fun daysApart(a: LocalDate, b: LocalDate): Long = abs(ChronoUnit.DAYS.between(a, b))
}
