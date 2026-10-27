package com.ivy.desktop.reports

import com.ivy.insights.PeriodMode
import com.ivy.insights.ReportPeriod
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Amounts and dates the way the phone app writes them. */
object Format {
    private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val dayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    private val monthYear = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
    private val shortMonth = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)
    private val time = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
    private const val PERCENT = 100

    /** 1,234.56 AED */
    fun money(amount: Double, currency: String): String = "${amount(amount)} $currency"

    fun amount(amount: Double): String = String.format(Locale.ENGLISH, "%,.2f", amount)

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
}
