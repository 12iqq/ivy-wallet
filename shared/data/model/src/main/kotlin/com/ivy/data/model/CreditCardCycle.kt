package com.ivy.data.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Date maths for credit card statement cycles.
 * A statement "closes" on [CreditCardDetails.statementDay]; the cycle is everything
 * after the previous closing day up to and including the next one.
 */
object CreditCardCycle {

    /** [dayOfMonth] in [month]'s month, clamped to short months (31 -> 30 Apr, 28/29 Feb). */
    fun dayInMonth(month: LocalDate, dayOfMonth: Int): LocalDate =
        month.withDayOfMonth(dayOfMonth.coerceIn(1, month.lengthOfMonth()))

    /** The next date (today or later) that falls on [dayOfMonth]. */
    fun nextDayOfMonth(today: LocalDate, dayOfMonth: Int): LocalDate {
        val thisMonth = dayInMonth(today, dayOfMonth)
        return if (!thisMonth.isBefore(today)) {
            thisMonth
        } else {
            dayInMonth(today.withDayOfMonth(1).plusMonths(1), dayOfMonth)
        }
    }

    /** First day of the statement cycle that [today] belongs to. */
    fun cycleStart(today: LocalDate, statementDay: Int): LocalDate {
        val closingThisMonth = dayInMonth(today, statementDay)
        val lastClosing = if (today.isAfter(closingThisMonth)) {
            closingThisMonth
        } else {
            dayInMonth(today.withDayOfMonth(1).minusMonths(1), statementDay)
        }
        return lastClosing.plusDays(1)
    }

    /** Whole days from [today] to the next payment due date, or null if no due day is set. */
    fun daysUntilDue(today: LocalDate, details: CreditCardDetails?): Int? {
        val dueDay = details?.paymentDueDay ?: return null
        return ChronoUnit.DAYS.between(today, nextDayOfMonth(today, dueDay)).toInt()
    }
}
