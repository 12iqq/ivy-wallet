package com.ivy.domain.utilities

import com.ivy.base.legacy.SharedPrefs
import com.ivy.base.model.TransactionType
import com.ivy.data.db.dao.read.AccountDao
import com.ivy.data.db.dao.read.TransactionDao
import com.ivy.data.db.dao.write.CapturedTransactionDao
import com.ivy.data.db.dao.write.UtilityBillDao
import com.ivy.data.db.dao.write.WritePlannedPaymentRuleDao
import com.ivy.data.db.dao.write.WriteTransactionDao
import com.ivy.data.db.entity.CapturedTransactionEntity
import com.ivy.data.db.entity.PlannedPaymentRuleEntity
import com.ivy.data.db.entity.TransactionEntity
import com.ivy.data.db.entity.UtilityBillEntity
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Each new utility bill becomes a planned payment due on the bill's due date, so it waits under
 * Upcoming. When the provider confirms it received the money, that payment is marked paid; the
 * bank's own text about the same payment joins it instead of being added a second time.
 */
@Singleton
class UtilityBillPlanner @Inject constructor(
    private val sharedPrefs: SharedPrefs,
    private val billDao: UtilityBillDao,
    private val transactionDao: TransactionDao,
    private val transactionWriter: WriteTransactionDao,
    private val ruleWriter: WritePlannedPaymentRuleDao,
    private val accountDao: AccountDao,
    private val capturedDao: CapturedTransactionDao,
) {
    fun enabled(): Boolean = sharedPrefs.getBoolean(SharedPrefs.TRACK_UTILITY_BILLS, false) &&
        sharedPrefs.getBoolean(SharedPrefs.PLAN_UTILITY_BILLS, true)

    /** Every bill still to be paid that never had a planned payment gets one. */
    suspend fun planDueBills(today: LocalDate = LocalDate.now()) {
        if (!enabled()) return
        val bills = billDao.findAll()
        bills.filter { UtilityBillRules.needsPlan(it, today) }.forEach { plan(it, bills) }
    }

    private suspend fun plan(bill: UtilityBillEntity, bills: List<UtilityBillEntity>) {
        val due = UtilityBillRules.dueDate(bill)?.atTime(DueTime)?.atZone(ZoneId.systemDefault())?.toInstant()
        val like = paymentLike(bill, bills)
        val accountId = like?.accountId?.takeIf { accountDao.findById(it) != null } ?: defaultAccountId()
        if (due == null || accountId == null) return

        val ruleId = UtilityBillRules.ruleId(bill.id)
        val title = like?.title?.takeIf { it.isNotBlank() } ?: bill.provider
        val description = UtilityBillRules.description(bill)
        ruleWriter.save(
            PlannedPaymentRuleEntity(
                startDate = due,
                intervalN = null,
                intervalType = null,
                oneTime = true,
                type = TransactionType.EXPENSE,
                accountId = accountId,
                amount = bill.total,
                categoryId = like?.categoryId,
                title = title,
                description = description,
                id = ruleId,
            )
        )
        transactionWriter.save(
            TransactionEntity(
                accountId = accountId,
                type = TransactionType.EXPENSE,
                amount = bill.total,
                title = title,
                description = description,
                dueDate = due,
                categoryId = like?.categoryId,
                recurringRuleId = ruleId,
                id = UtilityBillRules.paymentId(bill.id),
            )
        )
        billDao.saveAll(listOf(bill.copy(planned = true)))
    }

    /**
     * How this account's previous bill was paid (the account, name and category you used), else
     * your latest payment to the provider that isn't part of another planned payment.
     */
    private suspend fun paymentLike(bill: UtilityBillEntity, bills: List<UtilityBillEntity>): TransactionEntity? {
        val previous = bills
            .filter { it.planned && it.periodEnd < bill.periodEnd && sameAccount(it, bill) }
            .maxByOrNull { it.periodEnd }
            ?.let { transactionDao.findAllByRecurringRuleId(UtilityBillRules.ruleId(it.id)).firstOrNull() }
        return previous ?: (
            transactionDao.findAllByTitleMatchingPattern("%${bill.provider}%") +
                transactionDao.findAllByBankDetailsMatchingPattern("%${bill.provider}%")
            )
            .filter { it.type == TransactionType.EXPENSE && it.dateTime != null && it.recurringRuleId == null }
            .maxByOrNull { it.dateTime ?: Instant.EPOCH }
    }

    private fun sameAccount(a: UtilityBillEntity, b: UtilityBillEntity): Boolean =
        a.provider == b.provider && a.account == b.account

    /** The account picked last when adding a transaction, else the first one. */
    private suspend fun defaultAccountId(): UUID? {
        val lastUsed = sharedPrefs.getString(SharedPrefs.LAST_SELECTED_ACCOUNT_ID, null)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?.takeIf { accountDao.findById(it) != null }
        return lastUsed ?: accountDao.findAll().minByOrNull { it.orderNum }?.id
    }

    /**
     * The provider confirmed a payment: its bill's planned payment is paid. If the bank's text
     * about the same payment came first, that transaction becomes the bill's payment instead.
     */
    suspend fun onPaymentReceived(payment: UtilityPayment, receivedAt: Instant) {
        val bill = UtilityBillRules.billPaidBy(billDao.findAll(), payment)?.takeIf { enabled() } ?: return
        val ruleId = UtilityBillRules.ruleId(bill.id)
        val pending = transactionDao.findAllByRecurringRuleId(ruleId).firstOrNull { it.dateTime == null } ?: return

        val from = receivedAt.minus(SamePayment)
        val to = receivedAt.plus(SamePayment)
        val added = transactionDao.findAllBetweenAndType(from, to, TransactionType.EXPENSE).firstOrNull {
            it.recurringRuleId == null && UtilityBillRules.sameAmount(it.amount, payment.amount) &&
                UtilityBillParser.providerIn("${it.title} ${it.bankDetails}") == bill.provider
        }
        if (added != null) {
            transactionWriter.deleteById(pending.id)
            transactionWriter.save(added.copy(recurringRuleId = ruleId, paidForDateTime = pending.dueDate))
        } else {
            payNow(pending, payment, receivedAt, waitingInReview(bill.provider, payment.amount, from, to))
        }
        // paid one-time planned payments go away, like when paying one by hand
        ruleWriter.deleteById(ruleId)
    }

    /** The bank's text about this payment, still waiting in auto-add's review. */
    private suspend fun waitingInReview(
        provider: String,
        amount: Double,
        from: Instant,
        to: Instant,
    ): CapturedTransactionEntity? = capturedDao.findReceivedBetween(from, to).firstOrNull {
        it.status == CapturedTransactionEntity.STATUS_PENDING && it.type == TransactionType.EXPENSE &&
            UtilityBillRules.sameAmount(it.amount, amount) && UtilityBillParser.providerIn(it.merchant) == provider
    }

    private suspend fun payNow(
        pending: TransactionEntity,
        payment: UtilityPayment,
        paidAt: Instant,
        bankText: CapturedTransactionEntity?,
    ) {
        transactionWriter.save(
            pending.copy(
                // the bank's text knows which card or account paid; the confirmation doesn't
                accountId = bankText?.accountId?.takeIf { accountDao.findById(it) != null } ?: pending.accountId,
                amount = payment.amount,
                dateTime = paidAt,
                dueDate = null,
                paidForDateTime = pending.dueDate,
            )
        )
        // it's this payment, not a new one to add
        bankText?.let { capturedDao.updateStatus(it.id, CapturedTransactionEntity.STATUS_MERGED, pending.id) }
    }

    /**
     * The bill payment a bank's text is about, when the provider's confirmation already marked
     * it paid ("We have received AED 475.00" came before "AED475.00 debited ... for SEWA").
     */
    suspend fun recordedPayment(merchant: String?, amount: Double, at: Instant): TransactionEntity? {
        val provider = UtilityBillParser.providerIn(merchant)?.takeIf { enabled() } ?: return null
        val ruleIds = billDao.findAll()
            .filter { it.provider == provider && it.planned }
            .map { UtilityBillRules.ruleId(it.id) }
            .toSet()
        val sameTime = transactionDao.findAllBetweenAndType(
            startDate = at.minus(SamePayment),
            endDate = at.plus(SamePayment),
            type = TransactionType.EXPENSE
        )
        return sameTime.firstOrNull { it.recurringRuleId in ruleIds && UtilityBillRules.sameAmount(it.amount, amount) }
    }

    /** The bank's text says which account paid [payment]. */
    suspend fun paidFrom(payment: TransactionEntity, accountId: UUID?) {
        if (accountId != null && accountId != payment.accountId && accountDao.findById(accountId) != null) {
            transactionWriter.save(payment.copy(accountId = accountId))
        }
    }

    private companion object {
        // the bank's text and the provider's confirmation arrive within minutes; a day covers delays
        val SamePayment: Duration = Duration.ofDays(1)
        val DueTime: LocalTime = LocalTime.NOON
    }
}
