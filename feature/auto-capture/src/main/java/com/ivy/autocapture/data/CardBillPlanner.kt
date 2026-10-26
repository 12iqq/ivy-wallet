package com.ivy.autocapture.data

import com.ivy.autocapture.parser.CardBill
import com.ivy.autocapture.parser.CardBillParser
import com.ivy.base.legacy.SharedPrefs
import com.ivy.base.model.TransactionType
import com.ivy.data.db.dao.read.AccountDao
import com.ivy.data.db.dao.read.PlannedPaymentRuleDao
import com.ivy.data.db.dao.read.SettingsDao
import com.ivy.data.db.dao.read.TransactionDao
import com.ivy.data.db.dao.write.WritePlannedPaymentRuleDao
import com.ivy.data.db.dao.write.WriteTransactionDao
import com.ivy.data.db.entity.AccountEntity
import com.ivy.data.db.entity.PlannedPaymentRuleEntity
import com.ivy.data.db.entity.TransactionEntity
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A card's billing alert becomes a planned payment into the card for the total due, due on the
 * bill's due date and paid from the account you paid the card from last time, so it waits under
 * Upcoming. A payment into the card that covers it marks it paid; a smaller one lowers what's left.
 */
@Suppress("LongParameterList")
@Singleton
class CardBillPlanner @Inject constructor(
    private val settingsStore: AutoCaptureSettingsStore,
    private val sharedPrefs: SharedPrefs,
    private val transactionDao: TransactionDao,
    private val transactionWriter: WriteTransactionDao,
    private val ruleDao: PlannedPaymentRuleDao,
    private val ruleWriter: WritePlannedPaymentRuleDao,
    private val accountDao: AccountDao,
    private val settingsDao: SettingsDao,
) {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    /** @return true when [body] is a card's bill, so it isn't read as a transaction. */
    suspend fun onMessage(bankId: String, body: String, receivedAt: Instant): Boolean {
        val bill = CardBillParser.parse(bankId, body) ?: return false
        plan(bill, receivedAt)
        return true
    }

    @Suppress("ReturnCount")
    suspend fun plan(bill: CardBill, receivedAt: Instant, today: LocalDate = LocalDate.now()) {
        val config = settingsStore.current()
        val card = CaptureLogic.linkedAccountId(config.links, bill.bankId, bill.last4)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?.let { accountDao.findById(it) }
        if (!config.planCardBills || card == null || bill.dueDate.isBefore(today)) return
        val key = CardBillRules.key(card.id, bill.dueDate)
        val ruleId = CardBillRules.ruleId(card.id, bill.dueDate)
        // read again by a scan of the inbox, or planned before and since paid or deleted
        if (key in planned() || transactionDao.findAllByRecurringRuleId(ruleId).isNotEmpty()) return
        val payer = payer(card) ?: return

        // the new statement includes whatever was left of the last one
        pendingBills(card.id).forEach { replaced ->
            transactionWriter.deleteById(replaced.id)
            replaced.recurringRuleId?.let { ruleWriter.deleteById(it) }
        }
        save(bill, card, payer, ruleId)
        remember(key, today)

        // the bank can send the alert after you paid
        val paidSince = receivedAt.minus(PaidBeforeAlert)
        transactionDao.findAllTransfersToAccount(card.id)
            .filter { it.recurringRuleId == null && it.dateTime?.isBefore(paidSince) == false }
            .sortedBy { it.dateTime }
            .forEach { onPayment(it) }
    }

    private suspend fun save(bill: CardBill, card: AccountEntity, payer: Payer, ruleId: UUID) {
        val due = bill.dueDate.atTime(DueTime).atZone(zone).toInstant()
        val description = CardBillRules.description(bill)
        ruleWriter.save(
            PlannedPaymentRuleEntity(
                startDate = due,
                intervalN = null,
                intervalType = null,
                oneTime = true,
                type = TransactionType.TRANSFER,
                accountId = payer.accountId,
                amount = bill.totalDue,
                title = payer.title,
                description = description,
                toAccountId = card.id,
                toAmount = bill.totalDue,
                id = ruleId,
            )
        )
        transactionWriter.save(
            TransactionEntity(
                accountId = payer.accountId,
                type = TransactionType.TRANSFER,
                amount = bill.totalDue,
                toAccountId = card.id,
                toAmount = bill.totalDue,
                title = payer.title,
                description = description,
                dueDate = due,
                recurringRuleId = ruleId,
                id = CardBillRules.paymentId(card.id, bill.dueDate),
            )
        )
    }

    /** A transaction auto-add just added: see [onPayment]. */
    suspend fun onAdded(transactionId: UUID) {
        transactionDao.findById(transactionId)?.let { onPayment(it) }
    }

    /**
     * Money paid into a card (a transfer you added): its bill's planned payment becomes this
     * payment when it covers what's left of the bill, else what's left goes down by it.
     */
    @Suppress("ReturnCount")
    suspend fun onPayment(payment: TransactionEntity) {
        val cardId = payment.toAccountId ?: return
        val paidAt = payment.dateTime ?: return
        if (payment.type != TransactionType.TRANSFER || payment.recurringRuleId != null) return
        val bill = pendingBills(cardId)
            // not a late payment of an older statement
            .filter { it.dueDate?.minus(PaymentWindow)?.isAfter(paidAt) == false }
            .minByOrNull { it.dueDate ?: Instant.MAX }
            ?: return

        val left = bill.toAmount ?: bill.amount
        val paid = payment.toAmount ?: payment.amount
        if (CardBillRules.covers(paid, left)) {
            transactionWriter.deleteById(bill.id)
            transactionWriter.save(payment.copy(recurringRuleId = bill.recurringRuleId, paidForDateTime = bill.dueDate))
            // paid one-time planned payments go away, like when paying one by hand
            bill.recurringRuleId?.let { ruleWriter.deleteById(it) }
        } else {
            val total = bill.recurringRuleId?.let { ruleDao.findById(it) }?.let { it.toAmount ?: it.amount } ?: left
            val currency = accountDao.findById(cardId)?.currency ?: baseCurrency()
            transactionWriter.save(
                bill.copy(
                    amount = CardBillRules.round(bill.amount * (left - paid) / left),
                    toAmount = CardBillRules.round(left - paid),
                    description = CardBillRules.withPaid(bill.description, currency, total - left + paid),
                )
            )
        }
    }

    /** Planned card bills of [cardId] still waiting to be paid. */
    private suspend fun pendingBills(cardId: UUID): List<TransactionEntity> =
        transactionDao.findAllTransfersToAccount(cardId)
            .filter { it.dateTime == null && CardBillRules.isBillPayment(it, zone) }

    @Suppress("DataClassTypedIDs")
    private data class Payer(val accountId: UUID, val title: String)

    /**
     * The account you paid this card from last time, else the one you paid another card from,
     * else the account picked last when adding a transaction, else your first one; in the
     * card's currency. The name is the one you gave this card's last bill payment, if any.
     */
    private suspend fun payer(card: AccountEntity): Payer? {
        val base = baseCurrency()
        val all = accountDao.findAll()
        val cards = all.filter { it.accountType == AccountEntity.TYPE_CREDIT_CARD }.map { it.id }.toSet()
        val accounts = all.filter { it.id != card.id && (it.currency ?: base) == (card.currency ?: base) }
        val regular = accounts.filter { it.id !in cards }
        val ids = accounts.map { it.id }.toSet()
        val payments = transactionDao.findAllByType(TransactionType.TRANSFER)
            .filter { it.dateTime != null && it.accountId in ids }
        val toThisCard = payments.filter { it.toAccountId == card.id }.maxByOrNull { it.dateTime ?: Instant.EPOCH }
        val toACard = payments.filter { it.toAccountId in cards }.maxByOrNull { it.dateTime ?: Instant.EPOCH }
        val lastUsed = sharedPrefs.getString(SharedPrefs.LAST_SELECTED_ACCOUNT_ID, null)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?.takeIf { id -> regular.any { it.id == id } }
        val accountId = toThisCard?.accountId ?: toACard?.accountId ?: lastUsed
            ?: regular.minByOrNull { it.orderNum }?.id
            ?: return null
        val title = payments
            .filter { it.toAccountId == card.id && !it.title.isNullOrBlank() && CardBillRules.isBillPayment(it, zone) }
            .maxByOrNull { it.dateTime ?: Instant.EPOCH }
            ?.title
        return Payer(accountId, title ?: CardBillRules.TITLE)
    }

    private suspend fun baseCurrency(): String = settingsDao.findFirstOrNull()?.currency ?: DefaultCurrency

    private fun planned(): Set<String> = sharedPrefs.getString(SharedPrefs.PLANNED_CARD_BILLS, null)
        ?.split('\n')
        ?.filter { it.isNotBlank() }
        ?.toSet()
        .orEmpty()

    private fun remember(key: String, today: LocalDate) {
        val keys = CardBillRules.prune(planned() + key, today)
        sharedPrefs.putString(SharedPrefs.PLANNED_CARD_BILLS, keys.sorted().joinToString("\n"))
    }

    private companion object {
        val DueTime: LocalTime = LocalTime.NOON

        /** A payment this long before the alert can still be for it (paid as soon as the statement was out). */
        val PaidBeforeAlert: Duration = Duration.ofDays(2)

        /** Payments made earlier than this before a bill's due date are for an older statement. */
        val PaymentWindow: Duration = Duration.ofDays(30)

        const val DefaultCurrency = "AED"
    }
}
