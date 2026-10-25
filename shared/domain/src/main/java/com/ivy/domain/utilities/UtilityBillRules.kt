package com.ivy.domain.utilities

import com.ivy.data.db.entity.UtilityBillEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

/** Decisions about bills and their planned payments, kept apart from the database. */
object UtilityBillRules {
    /** The planned payment of a bill: the same every time the bill is read. */
    fun ruleId(billId: String): UUID = UUID.nameUUIDFromBytes("utility-bill:$billId".toByteArray())

    /** The pending transaction of that planned payment. */
    fun paymentId(billId: String): UUID = UUID.nameUUIDFromBytes("utility-bill-payment:$billId".toByteArray())

    fun dueDate(bill: UtilityBillEntity): LocalDate? = bill.dueDate?.let(::date)

    /** A bill still to be paid that never had a planned payment. */
    fun needsPlan(bill: UtilityBillEntity, today: LocalDate): Boolean =
        !bill.planned && dueDate(bill)?.isBefore(today) == false

    /** The bill a payment confirmation is about: the latest one of that account billed before it. */
    fun billPaidBy(bills: List<UtilityBillEntity>, payment: UtilityPayment): UtilityBillEntity? = bills
        .filter { bill ->
            bill.provider == payment.provider && bill.planned &&
                payment.accounts.any { UtilityBillParser.sameAccount(it, bill.account) } &&
                date(bill.periodEnd)?.isAfter(payment.date) == false
        }
        .maxByOrNull { it.periodEnd }

    fun sameAmount(a: Double, b: Double): Boolean = abs(a - b) < AmountTolerance

    /** "Electricity 281.45 · Water 61.30 · Gas 20.00 · 17 Sep – 17 Oct", kept with the planned payment */
    fun description(bill: UtilityBillEntity): String {
        val period = listOfNotNull(date(bill.periodStart), date(bill.periodEnd))
            .joinToString(" – ") { it.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)) }
        return listOf(
            "Electricity ${amount(bill.electricity)}",
            "Water ${amount(bill.water)}",
            "Gas ${amount(bill.gas)}",
            period,
        ).filter { it.isNotBlank() }.joinToString(" · ")
    }

    private fun amount(value: Double): String = String.format(Locale.ROOT, "%.2f", value)

    private fun date(text: String): LocalDate? = runCatching { LocalDate.parse(text) }.getOrNull()

    private const val AmountTolerance = 0.01
}
