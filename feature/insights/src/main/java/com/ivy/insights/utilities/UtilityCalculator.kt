package com.ivy.insights.utilities

import androidx.compose.runtime.Immutable
import com.ivy.data.db.entity.UtilityBillEntity
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** A utility bill ready for the report. */
@Immutable
data class UtilityBill(
    val account: String,
    val start: LocalDate,
    val end: LocalDate,
    val electricity: Double,
    val water: Double,
    val gas: Double,
    val municipality: Double,
    val vat: Double,
    val others: Double,
    /** Unpaid amount carried over from earlier bills */
    val arrears: Double,
    /** What the provider asked to pay, [arrears] included */
    val total: Double,
    val due: LocalDate?,
    val currency: String,
) {
    /** Everything besides the utilities: municipality fee, VAT and other charges. */
    val rest: Double get() = municipality + vat + others

    /** What this period cost, without amounts carried over from earlier bills. */
    val charges: Double get() = electricity + water + gas + rest

    val days: Int get() = ChronoUnit.DAYS.between(start, end).toInt() + 1
}

/** What the Reports screen shows: the latest bill, or that tracking is off. */
@Immutable
data class UtilityCard(val enabled: Boolean, val latest: UtilityBill?)

@Immutable
data class UtilityReport(
    /** Newest first. */
    val bills: ImmutableList<UtilityBill>,
    val averageCharges: Double,
    val averageElectricity: Double,
    val averageWater: Double,
    val averageGas: Double,
    val thisYear: Double,
    val allTime: Double,
)

object UtilityCalculator {
    /** null for a bill whose dates can't be read */
    fun toBill(entity: UtilityBillEntity): UtilityBill? = try {
        UtilityBill(
            account = entity.account,
            start = LocalDate.parse(entity.periodStart),
            end = LocalDate.parse(entity.periodEnd),
            electricity = entity.electricity,
            water = entity.water,
            gas = entity.gas,
            municipality = entity.municipality,
            vat = entity.vat,
            others = entity.others,
            arrears = entity.arrears,
            total = entity.total,
            due = entity.dueDate?.let(LocalDate::parse),
            currency = entity.currency,
        )
    } catch (e: DateTimeParseException) {
        null
    }

    /** Accounts with bills, the one billed most recently first. */
    fun accounts(bills: List<UtilityBill>): List<String> =
        bills.sortedByDescending { it.end }.map { it.account }.distinct()

    /** Report for one account's bills; null when there are none. */
    fun report(bills: List<UtilityBill>, today: LocalDate): UtilityReport? {
        if (bills.isEmpty()) return null
        val newestFirst = bills.sortedByDescending { it.end }
        return UtilityReport(
            bills = newestFirst.toImmutableList(),
            averageCharges = newestFirst.map { it.charges }.average(),
            averageElectricity = newestFirst.map { it.electricity }.average(),
            averageWater = newestFirst.map { it.water }.average(),
            averageGas = newestFirst.map { it.gas }.average(),
            thisYear = newestFirst.filter { it.end.year == today.year }.sumOf { it.charges },
            allTime = newestFirst.sumOf { it.charges },
        )
    }

    /** Whole-percent change; null when there's nothing to compare with. */
    fun changePercent(current: Double, previous: Double): Int? =
        if (previous > 0) ((current - previous) / previous * PERCENT).roundToInt() else null

    private const val PERCENT = 100
}
