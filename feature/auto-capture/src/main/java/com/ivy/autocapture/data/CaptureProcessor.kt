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
import com.ivy.base.legacy.SharedPrefs
import com.ivy.base.legacy.Transaction
import com.ivy.base.model.TransactionType
import com.ivy.data.db.dao.read.AccountDao
import com.ivy.data.db.dao.read.SettingsDao
import com.ivy.data.db.dao.read.TransactionDao
import com.ivy.data.db.dao.write.CapturedTransactionDao
import com.ivy.data.db.dao.write.WriteTransactionDao
import com.ivy.data.db.entity.CapturedTransactionEntity
import com.ivy.data.db.entity.TransactionEntity
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
    private val transactionWriter: WriteTransactionDao,
    private val accountDao: AccountDao,
    private val settingsDao: SettingsDao,
    private val transactionRepository: TransactionRepository,
    private val transactionMapper: TransactionMapper,
    private val exchangeRatesLogic: ExchangeRatesLogic,
    private val notificationService: NotificationService,
    private val appStarter: AppStarter,
    private val sharedPrefs: SharedPrefs,
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
        moveAccountEndingsToLinks()
        val links = settingsStore.current().links

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
                    linkedAccountId = CaptureLogic.linkedAccountId(links, parsed.bankId, parsed.last4),
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
        moveAccountEndingsToLinks()
        val links = settingsStore.current().links

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
                    linkedAccountId = CaptureLogic.linkedAccountIdByLast4(links, parsed.last4),
                )
            }
        }
    }

    /** Card endings used to be typed on the account too; now they live only in the bank links. */
    suspend fun moveAccountEndingsToLinks() {
        if (settingsStore.current().accountEndingsMoved) return
        val endings = accountDao.findAll()
            .associate { it.id.toString() to CardEndings.parse(it.cardEndings) }
            .filterValues { it.isNotEmpty() }
        settingsStore.update { config -> CaptureLogic.withAccountEndings(config, endings) }
    }

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
        // the category you gave this shop last time, else one from a similarly titled transaction
        val categoryId = lastTransactionAt(parsed.merchant)?.categoryId
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
        val finalTitle = title?.takeIf { it.isNotBlank() } ?: suggestedTitle(captured)

        val transactionId = UUID.randomUUID()
        // the item is claimed now: whatever happens, don't leave it stuck as "adding"
        val saved = runCatching {
            Transaction(
                accountId = accountId,
                type = captured.type,
                amount = amount.toBigDecimal(),
                title = finalTitle,
                // the description stays the user's own; the bank's wording goes in bank details
                bankDetails = bankDetails(captured, accountCurrency),
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
        return transactionId
    }

    /**
     * What you called this shop last time - learned from your own transactions, so renaming
     * one anywhere in the app teaches it - else the bank's name made readable.
     */
    suspend fun suggestedTitle(captured: CapturedTransactionEntity): String? =
        lastTransactionAt(captured.merchant)?.title?.takeIf { it.isNotBlank() }
            ?: captured.merchant?.let(MerchantNames::tidy)

    /** Your most recent transaction at the shop the bank calls [merchant]. */
    private suspend fun lastTransactionAt(merchant: String?): TransactionEntity? {
        val key = MerchantNames.key(merchant) ?: return null
        return transactionDao.findAllByBankDetailsMatchingPattern("%${key.substringBefore(' ')}%")
            .firstOrNull { MerchantNames.key(it.bankDetails?.substringBefore(DETAILS_SEPARATOR)) == key }
    }

    /** "GALADARI ICE CREAM CO LLC", plus the original amount when it was in another currency. */
    private fun bankDetails(captured: CapturedTransactionEntity, accountCurrency: String): String? {
        val foreignAmount = "${captured.currency} ${captured.amount.format(captured.currency)}"
            .takeUnless { captured.currency.equals(accountCurrency, ignoreCase = true) }
        return listOfNotNull(captured.merchant, foreignAmount).joinToString(DETAILS_SEPARATOR).ifEmpty { null }
    }

    /**
     * Before v1.0.3 auto-add wrote the bank into the description ("Emirates Islamic ··1234").
     * Moves that out, unless the user changed it, so the description is theirs again, and keeps
     * the shop's name in bank details. Runs once.
     */
    suspend fun moveBankNamesOutOfDescriptions() {
        if (settingsStore.current().bankDetailsMoved) return
        capturedDao.findAdded().forEach { captured ->
            val transaction = captured.transactionId?.let { transactionDao.findById(it) } ?: return@forEach
            val accountCurrency = accountDao.findById(transaction.accountId)?.currency
                ?: settingsDao.findFirst().currency
            val moved = transaction.copy(
                description = transaction.description
                    .takeUnless { it == oldGeneratedDescription(captured, accountCurrency) },
                bankDetails = transaction.bankDetails ?: bankDetails(captured, accountCurrency),
            )
            if (moved != transaction) transactionWriter.save(moved)
        }
        settingsStore.update { it.copy(bankDetailsMoved = true) }
    }

    private fun oldGeneratedDescription(captured: CapturedTransactionEntity, accountCurrency: String): String =
        buildString {
            append(UaeBanks.byId(captured.bankId)?.displayName ?: captured.bankId)
            captured.last4?.let { append(" ··").append(it) }
            if (!captured.currency.equals(accountCurrency, ignoreCase = true)) {
                append(DETAILS_SEPARATOR).append(captured.currency).append(' ')
                    .append(captured.amount.format(captured.currency))
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
        val word = MerchantNames.key(merchant)?.substringBefore(' ')
            ?.takeIf { it.length >= 3 } ?: return null
        return transactionDao.findAllByTitleMatchingPattern("%$word%")
            .filter { it.categoryId != null }
            .maxByOrNull { it.dateTime ?: Instant.EPOCH }
            ?.categoryId
    }

    /** One notification for what a scheduled check found that still needs a look. */
    suspend fun notifyFound(found: List<CapturedTransactionEntity>) {
        val waiting = found.filter { capturedDao.findById(it.id)?.status == CapturedTransactionEntity.STATUS_PENDING }
        when {
            waiting.size == 1 -> notifyNew(waiting.first())
            waiting.size > 1 -> showReviewNotification(
                title = context.getString(R.string.new_bank_transactions, waiting.size),
                text = context.getString(R.string.new_bank_transactions_text),
            )
        }
    }

    private fun notifyNew(captured: CapturedTransactionEntity) {
        showReviewNotification(
            title = context.getString(R.string.new_bank_transaction),
            text = context.getString(
                R.string.new_bank_transaction_text,
                captured.currency,
                captured.amount.format(captured.currency),
                captured.merchant ?: UaeBanks.byId(captured.bankId)?.displayName.orEmpty()
            ),
        )
    }

    private fun showReviewNotification(title: String, text: String) {
        // the user can turn these off in Settings -> Notifications
        if (!sharedPrefs.getBoolean(SharedPrefs.NOTIFY_DETECTED_TRANSACTIONS, true)) return
        val notification = notificationService
            .defaultIvyNotification(channel = IvyNotificationChannel.AUTO_CAPTURE)
            .setContentTitle(title)
            .setContentText(text)
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
        private const val DETAILS_SEPARATOR = " · "
    }
}
