package com.ivy.autocapture.parser

import java.time.LocalDate

/** A credit card's statement: what it owes and when that's due. */
@Suppress("DataClassTypedIDs")
data class CardBill(
    val bankId: String,
    /** Last 4 digits of the card. */
    val last4: String?,
    val currency: String,
    val totalDue: Double,
    val minimumDue: Double?,
    val dueDate: LocalDate,
)

/**
 * Reads a card's billing alert, e.g. ADCB's "Cr.Card XXX1234 Billing alert: Total due to avoid
 * fin. charges: AED1,234.56. Due date Oct 30 2026; Pay min. AED100.00 by due date to avoid
 * AED250.00 late fees." Needs the card, the total due and the due date.
 */
@Suppress("MagicNumber", "ReturnCount")
object CardBillParser {
    private const val AMOUNT = """([A-Za-z]{3})\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)"""

    // "Total due to avoid fin. charges: AED1,234.56", "Total amount due AED 1,234.56"
    private val total = Regex("""(?i)\btotal\s+(?:amount\s+)?due\b[^:\d]{0,40}:?\s*$AMOUNT""")

    // "Pay min. AED100.00", "Minimum amount due: AED 100.00"
    private val minimum = Regex("""(?i)\bmin(?:imum)?\.?\s+(?:amount\s+)?(?:due\s*)?(?:of\s+)?:?\s*$AMOUNT""")

    // "Due date Oct 30 2026", "due by 30/10/2026", "Payment due date: 30-Oct-2026"
    private val dueAt = Regex("""(?i)\bdue\s+(?:date|by|on)\b\s*(?:is\s+)?:?\s*""")
    private val card = Regex("""(?i)\bcard\b\s*(?:no\.?\s*)?(?:ending\s*(?:with|in)?\s*)?[X*•]*\s?(\d{4})\b""")

    private val monthDayYear = Regex("""^([A-Za-z]{3,9})\.?\s+(\d{1,2}),?\s+(\d{4})\b""")
    private val dayMonthYear = Regex("""^(\d{1,2})[\s\-/]([A-Za-z]{3,9})[\s\-/,]+(\d{4}|\d{2})\b""")
    private val numeric = Regex("""^(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{4})\b""")
    private val iso = Regex("""^(\d{4})-(\d{2})-(\d{2})\b""")
    private val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    fun parse(bankId: String, body: String): CardBill? {
        val text = body.replace(Regex("\\s+"), " ").trim()
        if (!text.contains("card", ignoreCase = true)) return null
        val (currency, totalDue) = total.find(text)?.let(::amount) ?: return null
        val dueDate = dueDate(text) ?: return null
        if (totalDue <= 0.0) return null
        return CardBill(
            bankId = bankId,
            last4 = card.find(text)?.groupValues?.get(1),
            currency = currency,
            totalDue = totalDue,
            minimumDue = minimum.find(text)?.let(::amount)?.second,
            dueDate = dueDate,
        )
    }

    private fun amount(match: MatchResult): Pair<String, Double>? {
        val value = match.groupValues[2].replace(",", "").toDoubleOrNull() ?: return null
        return match.groupValues[1].uppercase() to value
    }

    private fun dueDate(text: String): LocalDate? = dueAt.findAll(text)
        .firstNotNullOfOrNull { date(text.substring(it.range.last + 1)) }

    private fun date(text: String): LocalDate? =
        monthDayYear.find(text)?.let {
            of(year = it.groupValues[3], month = month(it.groupValues[1]), day = it.groupValues[2])
        }
            ?: dayMonthYear.find(text)?.let {
                of(year = it.groupValues[3], month = month(it.groupValues[2]), day = it.groupValues[1])
            }
            // banks here write the day first
            ?: numeric.find(text)?.let {
                of(year = it.groupValues[3], month = it.groupValues[2].toIntOrNull(), day = it.groupValues[1])
            }
            ?: iso.find(text)?.let {
                of(year = it.groupValues[1], month = it.groupValues[2].toIntOrNull(), day = it.groupValues[3])
            }

    private fun month(name: String): Int? = months.indexOf(name.take(3).lowercase()).takeIf { it >= 0 }?.plus(1)

    private fun of(year: String, month: Int?, day: String): LocalDate? {
        val y = year.toIntOrNull()?.let { if (it < 100) 2000 + it else it } ?: return null
        return runCatching { LocalDate.of(y, month ?: return null, day.toInt()) }.getOrNull()
    }
}
