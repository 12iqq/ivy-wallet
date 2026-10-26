package com.ivy.autocapture.data

import com.ivy.autocapture.parser.CardBill
import com.ivy.base.model.TransactionType
import com.ivy.data.db.entity.TransactionEntity
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.UUID

/** Decisions about a card bill's planned payment, kept apart from the database. */
object CardBillRules {
    /** The planned payment's name until you give one of them your own. */
    const val TITLE = "Card bill"

    private const val AMOUNT_TOLERANCE = 0.01
    private const val SEPARATOR = " · "
    private const val PAID = "Paid"

    /** How long a planned bill is remembered after its due date (see [prune]). */
    private const val KEEP_DAYS = 100L

    /** The planned payment of a card's bill: the same every time the bill's alert is read. */
    fun ruleId(cardId: UUID, dueDate: LocalDate): UUID =
        UUID.nameUUIDFromBytes("card-bill:$cardId:$dueDate".toByteArray())

    /** The pending transaction of that planned payment. */
    fun paymentId(cardId: UUID, dueDate: LocalDate): UUID =
        UUID.nameUUIDFromBytes("card-bill-payment:$cardId:$dueDate".toByteArray())

    /** A bill that was planned once, so reading its alert again doesn't bring it back. */
    fun key(cardId: UUID, dueDate: LocalDate): String = "$dueDate|$cardId"

    /** Drops the keys of bills that were due long ago. */
    fun prune(keys: Set<String>, today: LocalDate): Set<String> = keys.filterTo(mutableSetOf()) { key ->
        val due = runCatching { LocalDate.parse(key.substringBefore('|')) }.getOrNull()
        due != null && !due.isBefore(today.minusDays(KEEP_DAYS))
    }

    /** A card bill's payment, pending or paid: a transfer into the card for the bill due that day. */
    fun isBillPayment(transaction: TransactionEntity, zone: ZoneId): Boolean {
        val cardId = transaction.toAccountId
        val due = transaction.dueDate ?: transaction.paidForDateTime
        return transaction.type == TransactionType.TRANSFER && cardId != null && due != null &&
            transaction.recurringRuleId == ruleId(cardId, due.atZone(zone).toLocalDate())
    }

    /** [paid] into the card is all that's [left] of its bill, or more. */
    fun covers(paid: Double, left: Double): Boolean = paid > left - AMOUNT_TOLERANCE

    /** "Total due AED 1,234.56 · Minimum AED 100.00", kept with the planned payment */
    fun description(bill: CardBill): String = listOfNotNull(
        "Total due ${bill.currency} ${amount(bill.totalDue)}",
        bill.minimumDue?.let { "Minimum ${bill.currency} ${amount(it)}" },
    ).joinToString(SEPARATOR)

    /** The description once [paid] of the bill came in: "… · Paid AED 500.00". */
    fun withPaid(description: String?, currency: String, paid: Double): String =
        description.orEmpty().split(SEPARATOR)
            .filterNot { it.isBlank() || it.startsWith(PAID) }
            .plus("$PAID $currency ${amount(paid)}")
            .joinToString(SEPARATOR)

    /** To the fils (cent): what's left after a payment shouldn't read 234.55999999999995. */
    fun round(value: Double): Double = value.toBigDecimal().setScale(2, RoundingMode.HALF_UP).toDouble()

    private fun amount(value: Double): String = String.format(Locale.ROOT, "%,.2f", value)
}
