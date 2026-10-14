package com.ivy.autocapture.data

import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import com.ivy.autocapture.parser.BankMessageRouter
import com.ivy.autocapture.parser.Direction
import com.ivy.autocapture.parser.GenericMessageParser
import com.ivy.autocapture.parser.ParsedMessage
import com.ivy.autocapture.parser.GoogleWalletPackage
import com.ivy.autocapture.parser.UaeBanks
import com.ivy.autocapture.parser.WalletNotifications
import com.ivy.base.legacy.Transaction
import com.ivy.base.model.TransactionType
import com.ivy.data.db.dao.read.AccountDao
import com.ivy.data.db.dao.read.SettingsDao
import com.ivy.data.db.dao.read.TransactionDao
import com.ivy.data.db.dao.write.CapturedTransactionDao
import com.ivy.data.db.entity.CapturedTransactionEntity
import com.ivy.data.model.CardEndings
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.math.RoundingMode
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
    private val captureLock = Mutex()

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

        // SMS and Wallet notifications arrive on different threads: check-and-save one at a time
        return captureLock.withLock {
            // already captured from Google Wallet's notification
            if (reportedBy(CapturedTransactionEntity.SOURCE_NOTIFICATION, parsed, receivedAt)) {
                null
            } else {
                capture(
                    sender = sender,
                    body = body,
                    receivedAt = receivedAt,
                    source = source,
                    notify = notify,
                    parsed = parsed,
                    linkedAccountId = CaptureLogic.linkedAccountId(config.links, parsed.bankId, parsed.last4),
                )
            }
        }
    }

    /**
     * A Google Wallet tap-to-pay notification. Skipped when the bank's SMS
     * for the same purchase was already captured.
     */
    suspend fun onWalletNotification(
        title: String?,
        text: String?,
        postedAt: Instant,
    ): CapturedTransactionEntity? {
        val config = settingsStore.current()
        if (config.mode == AutoCaptureMode.Off || !config.readWalletNotifications) return null
        val body = WalletNotifications.toMessage(title, text) ?: return null
        val parsed = WalletNotifications.parse(title, text) ?: return null

        return captureLock.withLock {
            if (reportedBy(CapturedTransactionEntity.SOURCE_SMS, parsed, postedAt)) {
                null
            } else {
                capture(
                    sender = GoogleWalletPackage,
                    body = body,
                    receivedAt = postedAt,
                    source = CapturedTransactionEntity.SOURCE_NOTIFICATION,
                    notify = true,
                    parsed = parsed,
                    linkedAccountId = CaptureLogic.linkedAccountIdByLast4(config.links, parsed.last4),
                )
            }
        }
    }

    /** An account whose "card / account number ending" (set when editing the account) matches. */
    private suspend fun accountWithEnding(last4: String): UUID? =
        accountDao.findAll().firstOrNull { last4 in CardEndings.parse(it.cardEndings) }?.id

    /** The same purchase (amount, currency and card when known) already came in from [source]. */
    private suspend fun reportedBy(source: String, parsed: ParsedMessage, at: Instant): Boolean =
        capturedDao.countFromSourceBetween(
            source = source,
            amount = parsed.amount,
            currency = parsed.currency,
            last4 = parsed.last4,
            from = at.minus(SAME_PURCHASE_WINDOW),
            to = at.plus(SAME_PURCHASE_WINDOW),
        ) > 0

    @Suppress("LongParameterList")
    private suspend fun capture(
        sender: String,
        body: String,
        receivedAt: Instant,
        source: String,
        notify: Boolean,
        parsed: ParsedMessage,
        linkedAccountId: String?,
    ): CapturedTransactionEntity? {
        val config = settingsStore.current()
        val fingerprint = CaptureLogic.fingerprint(sender, body)
        // Wallet texts have no date or balance, so an identical one later is a new purchase;
        // bank SMS are only re-delivered / re-scanned within a short time
        val window = if (source == CapturedTransactionEntity.SOURCE_NOTIFICATION) {
            SAME_NOTIFICATION_WINDOW
        } else {
            SAME_MESSAGE_WINDOW
        }
        if (capturedDao.countByFingerprintBetween(fingerprint, receivedAt.minus(window), receivedAt.plus(window)) > 0) {
            return null
        }

        val accountId = linkedAccountId
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?.takeIf { accountDao.findById(it) != null }
            ?: parsed.last4?.let { accountWithEnding(it) }
        val merchantKey = parsed.merchant?.let(CaptureLogic::merchantKey)?.takeIf { it.isNotBlank() }
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

        val addedRightAway = config.addWithoutReview && accountId != null && !isPossibleDuplicate(captured) &&
            add(captured, accountId = accountId, categoryId = categoryId, title = null) != null
        if (!addedRightAway && notify) {
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
        // without a rate the amount would be copied 1:1 (USD 100 -> AED 100): keep it for review
        if (!exchangeRatesLogic.canConvert(baseCurrency, captured.currency, accountCurrency)) return null
        // a double tap or "Add all" twice must not create two transactions
        if (capturedDao.claimPending(captured.id, CapturedTransactionEntity.STATUS_ADDING) == 0) return null
        val amount = if (captured.currency.equals(accountCurrency, ignoreCase = true)) {
            captured.amount
        } else {
            exchangeRatesLogic.convertAmount(
                baseCurrency = baseCurrency,
                amount = captured.amount,
                fromCurrency = captured.currency,
                toCurrency = accountCurrency
            ).toBigDecimal().setScale(2, RoundingMode.HALF_UP).toDouble()
        }
        val config = settingsStore.current()
        val merchantKey = captured.merchant?.let(CaptureLogic::merchantKey)?.takeIf { it.isNotBlank() }
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
        // the item is claimed now: whatever happens, don't leave it stuck as "adding"
        val saved = runCatching {
            Transaction(
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
        }.getOrElse {
            Timber.e(it, "Saving captured ${captured.id} failed")
            false
        }
        if (!saved) {
            Timber.e("Couldn't create transaction from captured ${captured.id}")
            capturedDao.updateStatus(captured.id, CapturedTransactionEntity.STATUS_PENDING, null)
            return null
        }

        capturedDao.updateStatus(captured.id, CapturedTransactionEntity.STATUS_ADDED, transactionId)
        if (merchantKey != null) rememberChoices(merchantKey, captured, categoryId, title)
        return transactionId
    }

    /** Next time this merchant shows up, suggest the same category and title. */
    private fun rememberChoices(
        merchantKey: String,
        captured: CapturedTransactionEntity,
        categoryId: UUID?,
        title: String?,
    ) {
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

    /** true when the item is in a foreign currency and there's no exchange rate to convert it yet. */
    suspend fun missingRate(captured: CapturedTransactionEntity, accountId: UUID?): Boolean {
        val account = accountId?.let { accountDao.findById(it) } ?: return false
        val baseCurrency = settingsDao.findFirst().currency
        return !exchangeRatesLogic.canConvert(baseCurrency, captured.currency, account.currency ?: baseCurrency)
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
        private val SAME_PURCHASE_WINDOW: Duration = Duration.ofMinutes(15)
        private val SAME_NOTIFICATION_WINDOW: Duration = Duration.ofMinutes(2)
        private val SAME_MESSAGE_WINDOW: Duration = Duration.ofDays(1)
        private const val REVIEW_REQUEST_CODE = 4243
    }
}
