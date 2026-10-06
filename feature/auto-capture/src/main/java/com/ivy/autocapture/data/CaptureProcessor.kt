package com.ivy.autocapture.data

import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import com.ivy.autocapture.parser.BankMessageRouter
import com.ivy.autocapture.parser.Direction
import com.ivy.autocapture.parser.GenericMessageParser
import com.ivy.autocapture.parser.ParsedMessage
import com.ivy.autocapture.parser.UaeBanks
import com.ivy.base.legacy.Transaction
import com.ivy.base.model.TransactionType
import com.ivy.data.db.dao.read.AccountDao
import com.ivy.data.db.dao.read.SettingsDao
import com.ivy.data.db.dao.read.TransactionDao
import com.ivy.data.db.dao.write.CapturedTransactionDao
import com.ivy.data.db.entity.CapturedTransactionEntity
import com.ivy.data.repository.TransactionRepository
import com.ivy.data.repository.mapper.TransactionMapper
import com.ivy.domain.AppStarter
import com.ivy.legacy.datamodel.temp.toDomain
import com.ivy.legacy.utils.format
import com.ivy.ui.R
import com.ivy.wallet.android.notification.IvyNotificationChannel
import com.ivy.wallet.android.notification.NotificationService
import com.ivy.wallet.domain.deprecated.logic.currency.ExchangeRatesLogic
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Turns bank messages into captured transactions waiting for review
 * (or adds them right away when "Add without review" is on).
 */
@Suppress("LongParameterList", "TooManyFunctions", "ReturnCount", "MagicNumber")
@Singleton
class CaptureProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsStore: AutoCaptureSettingsStore,
    private val capturedDao: CapturedTransactionDao,
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val settingsDao: SettingsDao,
    private val transactionRepository: TransactionRepository,
    private val transactionMapper: TransactionMapper,
    private val exchangeRatesLogic: ExchangeRatesLogic,
    private val notificationService: NotificationService,
    private val appStarter: AppStarter,
) {
    private val router = BankMessageRouter()

    /**
     * Parses without saving anything - used by "Test a message" in settings.
     * Tries every known bank so it works before any bank is selected.
     */
    fun preview(sender: String, body: String): ParsedMessage? {
        val config = settingsStore.current()
        val allBanks = (UaeBanks.all + UaeBanks.OTHER).map { it.id }.toSet()
        return router.parse(sender, body, allBanks, config.extraSenders)
            ?: GenericMessageParser.parse(UaeBanks.OTHER, body)
    }

    /**
     * @return the captured item, or null if the message isn't a (new) transaction.
     */
    suspend fun onMessage(
        sender: String,
        body: String,
        receivedAt: Instant,
        source: String = CapturedTransactionEntity.SOURCE_SMS,
        notify: Boolean = true,
    ): CapturedTransactionEntity? {
        val config = settingsStore.current()
        if (config.mode == AutoCaptureMode.Off) return null

        val parsed = router.parse(sender, body, config.enabledBankIds, config.extraSenders)
            ?: return null
        val fingerprint = CaptureLogic.fingerprint(sender, body)
        if (capturedDao.countByFingerprint(fingerprint) > 0) return null

        val accountId = CaptureLogic.linkedAccountId(config.links, parsed.bankId, parsed.last4)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?.takeIf { accountDao.findById(it) != null }
        val merchantKey = parsed.merchant?.let(CaptureLogic::merchantKey)
        val categoryId = merchantKey?.let { config.learnedCategories[it] }
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: parsed.merchant?.let { suggestCategoryFromHistory(it) }

        val captured = CapturedTransactionEntity(
            source = source,
            sender = sender,
            rawText = body,
            bankId = parsed.bankId,
            type = if (parsed.direction == Direction.Debit) TransactionType.EXPENSE else TransactionType.INCOME,
            amount = parsed.amount,
            currency = parsed.currency,
            merchant = parsed.merchant,
            last4 = parsed.last4,
            isCreditCard = parsed.isCreditCard,
            receivedAt = receivedAt,
            accountId = accountId,
            categoryId = categoryId,
            status = CapturedTransactionEntity.STATUS_PENDING,
            transactionId = null,
            fingerprint = fingerprint,
        )
        capturedDao.save(captured)

        if (config.addWithoutReview && accountId != null && !isPossibleDuplicate(captured)) {
            add(captured, accountId = accountId, categoryId = categoryId, title = null)
        } else if (notify) {
            notifyNew(captured)
        }
        return captured
    }

    /**
     * Creates the real transaction from a captured item and remembers the
     * user's choices for next time.
     */
    suspend fun add(
        captured: CapturedTransactionEntity,
        accountId: UUID,
        categoryId: UUID?,
        title: String?,
    ): UUID? {
        val account = accountDao.findById(accountId) ?: return null
        val baseCurrency = settingsDao.findFirst().currency
        val accountCurrency = account.currency ?: baseCurrency
        val amount = if (captured.currency.equals(accountCurrency, ignoreCase = true)) {
            captured.amount
        } else {
            exchangeRatesLogic.convertAmount(
                baseCurrency = baseCurrency,
                amount = captured.amount,
                fromCurrency = captured.currency,
                toCurrency = accountCurrency
            )
        }
        val config = settingsStore.current()
        val merchantKey = captured.merchant?.let(CaptureLogic::merchantKey)
        val finalTitle = title?.takeIf { it.isNotBlank() }
            ?: merchantKey?.let { config.learnedTitles[it] }
            ?: captured.merchant
        val bankName = UaeBanks.byId(captured.bankId)?.displayName ?: captured.bankId
        val description = buildString {
            append(bankName)
            captured.last4?.let { append(" ··").append(it) }
            if (!captured.currency.equals(accountCurrency, ignoreCase = true)) {
                append(" · ").append(captured.currency).append(' ')
                    .append(captured.amount.format(captured.currency))
            }
        }

        val transactionId = UUID.randomUUID()
        val saved = Transaction(
            accountId = accountId,
            type = captured.type,
            amount = amount.toBigDecimal(),
            title = finalTitle,
            description = description,
            dateTime = captured.receivedAt,
            categoryId = categoryId,
            isSynced = false,
            id = transactionId,
        ).toDomain(transactionMapper)?.let {
            transactionRepository.save(it)
            true
        } ?: false
        if (!saved) {
            Timber.e("Couldn't create transaction from captured ${captured.id}")
            return null
        }

        capturedDao.updateStatus(captured.id, CapturedTransactionEntity.STATUS_ADDED, transactionId)
        if (merchantKey != null) {
            settingsStore.update { cfg ->
                cfg.copy(
                    learnedCategories = categoryId?.let {
                        cfg.learnedCategories + (merchantKey to it.toString())
                    } ?: cfg.learnedCategories,
                    learnedTitles = if (title != null && title.isNotBlank() && title != captured.merchant) {
                        cfg.learnedTitles + (merchantKey to title)
                    } else {
                        cfg.learnedTitles
                    }
                )
            }
        }
        return transactionId
    }

    suspend fun dismiss(capturedId: UUID) {
        capturedDao.updateStatus(capturedId, CapturedTransactionEntity.STATUS_DISMISSED, null)
    }

    /** Same amount within a day of an existing transaction = likely already entered manually. */
    suspend fun isPossibleDuplicate(captured: CapturedTransactionEntity): Boolean {
        val window = Duration.ofHours(36)
        return transactionDao.findAllBetween(
            startDate = captured.receivedAt.minus(window),
            endDate = captured.receivedAt.plus(window)
        ).any { trn ->
            trn.type == captured.type &&
                abs(trn.amount - captured.amount) < 0.01 &&
                (captured.accountId == null || trn.accountId == captured.accountId)
        }
    }

    /** Most recent category used for a transaction titled like the merchant. */
    private suspend fun suggestCategoryFromHistory(merchant: String): UUID? {
        val word = CaptureLogic.merchantKey(merchant).split(" ").firstOrNull()
            ?.takeIf { it.length >= 3 } ?: return null
        return transactionDao.findAllByTitleMatchingPattern("%$word%")
            .filter { it.categoryId != null }
            .maxByOrNull { it.dateTime ?: Instant.EPOCH }
            ?.categoryId
    }

    private fun notifyNew(captured: CapturedTransactionEntity) {
        val notification = notificationService
            .defaultIvyNotification(channel = IvyNotificationChannel.AUTO_CAPTURE)
            .setContentTitle(context.getString(R.string.new_bank_transaction))
            .setContentText(
                context.getString(
                    R.string.new_bank_transaction_text,
                    captured.currency,
                    captured.amount.format(captured.currency),
                    captured.merchant ?: UaeBanks.byId(captured.bankId)?.displayName.orEmpty()
                )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    REVIEW_REQUEST_CODE,
                    appStarter.getRootIntent().putExtra(EXTRA_OPEN_REVIEW, true),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        notificationService.showNotification(notification, NOTIFICATION_ID)
    }

    companion object {
        const val EXTRA_OPEN_REVIEW = "open_auto_capture_review"
        private const val NOTIFICATION_ID = 4242
        private const val REVIEW_REQUEST_CODE = 4243
    }
}
