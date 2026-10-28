package com.ivy.desktop.reports

import com.ivy.insights.PeriodMode
import com.ivy.insights.ReportPeriod
import com.ivy.insights.recurring.Cadence
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.YearMonth
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Amounts and dates the way the phone app writes them. */
object Format {
    private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val dayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    private val monthYear = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
    private val shortMonth = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)
    private val time = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
    private const val PERCENT = 100
    private const val THOUSAND = 1_000.0
    private const val MILLION = 1_000_000.0

    /** 1,234.56 AED */
    fun money(amount: Double, currency: String): String = "${amount(amount)} $currency"

    fun amount(amount: Double): String = String.format(Locale.ENGLISH, "%,.2f", amount)

    /** 25K, 2.5K, 1.2M, 500: short amounts for the side of a chart */
    fun compact(amount: Double): String {
        val (value, unit) = when {
            abs(amount) >= MILLION -> amount / MILLION to "M"
            abs(amount) >= THOUSAND -> amount / THOUSAND to "K"
            else -> amount to ""
        }
        val number = String.format(Locale.ENGLISH, "%.1f", value).removeSuffix(".0")
        return if (number == "-0") "0$unit" else "$number$unit"
    }

    /** 42% */
    fun percent(share: Double): String = "${(share * PERCENT).roundToInt()}%"

    /** 27 Oct, with the year when it isn't this year */
    fun day(date: LocalDate, today: LocalDate): String =
        date.format(if (date.year == today.year) dayMonth else dayMonthYear)

    /** 27 Oct 2026, 14:05 */
    fun dateTime(dateTime: LocalDateTime): String = "${dateTime.format(dayMonthYear)}, ${dateTime.format(time)}"

    /** October 2026, or 2026 */
    fun period(period: ReportPeriod, mode: PeriodMode): String = when (mode) {
        PeriodMode.Month -> period.start.format(monthYear)
        PeriodMode.Year -> period.start.year.toString()
    }

    /** Oct */
    fun month(date: LocalDate): String = date.format(shortMonth)

    /** October 2026 */
    fun month(month: YearMonth): String = month.format(monthYear)

    /** How often a recurring charge comes */
    fun cadence(cadence: Cadence): String = when (cadence) {
        Cadence.Weekly -> "Weekly"
        Cadence.Monthly -> "Monthly"
        Cadence.Quarterly -> "Every 3 months"
        Cadence.Yearly -> "Yearly"
    }
}
