package com.ivy.autocapture.data

import com.ivy.base.model.TransactionType
import com.ivy.data.db.entity.CapturedTransactionEntity
import kotlinx.serialization.Serializable
import java.time.Duration
import java.util.UUID
import kotlin.math.abs

@Serializable
enum class AutoCaptureMode {
    Off,
    Sms,
    Statement,
    Both;

    val readsSms: Boolean get() = this == Sms || this == Both
    val importsStatements: Boolean get() = this == Statement || this == Both
}

/** How new bank SMS are picked up. */
@Serializable
enum class SmsCheck(val hours: Int) {
    /** Android wakes the app briefly for each incoming SMS. */
    OnArrival(hours = 0),
    Hourly(hours = 1),
    Every3Hours(hours = 3),
    Every6Hours(hours = 6),
    Every12Hours(hours = 12),
    Daily(hours = 24);

    /** The app stays closed and reads new SMS from the inbox every [hours]. */
    val scheduled: Boolean get() = this != OnArrival
}

/**
 * Links messages of a bank (optionally a specific card/account by its last 4 digits)
 * to an Ivy Wallet account.
 */
@Serializable
data class CardLink(
    val bankId: String,
    val last4: String?,
    val accountId: String,
)

@Suppress("DataClassDefaultValues")
@Serializable
data class AutoCaptureConfig(
    val mode: AutoCaptureMode = AutoCaptureMode.Off,
    val enabledBankIds: Set<String> = emptySet(),
    /** User-added SMS sender names per bank id. */
    val extraSenders: Map<String, List<String>> = emptyMap(),
    val links: List<CardLink> = emptyList(),
    val addWithoutReview: Boolean = false,
    /** Also read Google Wallet tap-to-pay notifications (needs notification access). */
    val readWalletNotifications: Boolean = false,
    /** Card endings once typed on accounts were copied into [links]. */
    val accountEndingsMoved: Boolean = false,
    /** Bank names auto-add used to write into descriptions were moved out (see CaptureProcessor). */
    val bankDetailsMoved: Boolean = false,
    val smsCheck: SmsCheck = SmsCheck.OnArrival,
    /** When the inbox was last read by a scheduled check. */
    val lastSmsCheckMillis: Long? = null,
)

@Suppress("TooManyFunctions")
object CaptureLogic {
    private const val AmountTolerance = 0.01

    /** A card payment can be confirmed by the card a day after the bank sent it. */
    private val TransferWindow: Duration = Duration.ofDays(3)

    /** A plain card charge only joins a transfer it's close to (a top-up's charge comes within the hour). */
    private val ChargeWindow: Duration = Duration.ofHours(6)

    private val PairableStatuses = setOf(
        CapturedTransactionEntity.STATUS_PENDING,
        CapturedTransactionEntity.STATUS_ADDED,
    )

    /** The SMS receiver only exists while new messages are picked up as they arrive. */
    fun receivesSms(config: AutoCaptureConfig): Boolean = config.mode.readsSms && !config.smsCheck.scheduled

    /** The inbox is read every few hours instead. */
    fun checksSmsOnSchedule(config: AutoCaptureConfig): Boolean = config.mode.readsSms && config.smsCheck.scheduled

    /**
     * The Google Wallet listener only exists while it's needed. Wallet notifications can
     * only be caught as they arrive, so they pause while SMS are checked on a schedule.
     */
    fun listensToWallet(config: AutoCaptureConfig): Boolean =
        config.mode.readsSms && config.readWalletNotifications && !config.smsCheck.scheduled

    /**
     * Best account for a message: exact card match, then the bank's catch-all link,
     * then (if the bank has exactly one link) that one.
     */
    fun linkedAccountId(links: List<CardLink>, bankId: String, last4: String?): String? {
        val bankLinks = links.filter { it.bankId == bankId }
        return bankLinks.firstOrNull { last4 != null && it.last4 == last4 }?.accountId
            ?: bankLinks.firstOrNull { it.last4.isNullOrBlank() }?.accountId
            ?: bankLinks.singleOrNull()?.takeIf { last4 == null }?.accountId
    }

    /**
     * Account for a Google Wallet payment: Wallet doesn't name the bank,
     * so match the card's last 4 digits against links of any bank.
     */
    fun linkedAccountIdByLast4(links: List<CardLink>, last4: String?): String? =
        last4?.let { digits -> links.firstOrNull { it.last4 == digits }?.accountId }

    /**
     * Card endings that used to be typed on accounts ([endings]: account id -> last 4 digits),
     * turned into links for the banks already linked to that account, else every enabled bank.
     * Waits (returns [config] unchanged) while there's no bank to link them to.
     */
    fun withAccountEndings(config: AutoCaptureConfig, endings: Map<String, List<String>>): AutoCaptureConfig {
        if (endings.isNotEmpty() && config.enabledBankIds.isEmpty() && config.links.isEmpty()) return config
        val newLinks = endings.flatMap { (accountId, digits) ->
            val banks = config.links.filter { it.accountId == accountId }.map { it.bankId }.distinct()
                .ifEmpty { config.enabledBankIds.sorted() }
            digits.flatMap { last4 -> banks.map { bankId -> CardLink(bankId, last4, accountId) } }
        }
            .filterNot { new -> config.links.any { it.bankId == new.bankId && it.last4 == new.last4 } }
            .distinctBy { it.bankId to it.last4 }
        return config.copy(links = config.links + newLinks, accountEndingsMoved = true)
    }

    /**
     * The earlier message about the same money as [new] when it moved between two of your
     * accounts: the bank's "card payment" and the card's "payment received", or a wallet top-up
     * and the card charge for it. Same amount, the other direction, another of your accounts,
     * and one of the two reads as a transfer.
     */
    fun otherHalf(new: CapturedTransactionEntity, recent: List<CapturedTransactionEntity>): CapturedTransactionEntity? {
        val accountId = new.accountId ?: return null
        return recent
            .filter { it.id != new.id && it.status in PairableStatuses }
            .filter { abs(it.amount - new.amount) < AmountTolerance }
            .filter { it.currency.equals(new.currency, ignoreCase = true) }
            .filter { other ->
                if (other.type == TransactionType.TRANSFER) {
                    // already a transfer: this message must be its other end
                    val end = if (new.type == TransactionType.EXPENSE) other.accountId else other.toAccountId
                    end == accountId
                } else {
                    other.type != new.type && other.accountId != null && other.accountId != accountId &&
                        (new.isTransfer || other.isTransfer)
                }
            }
            .filter { other -> apart(other, new) <= pairingWindow(new, other) }
            .minByOrNull { apart(it, new) }
    }

    private fun pairingWindow(new: CapturedTransactionEntity, other: CapturedTransactionEntity): Duration {
        val readsAsTransfer = new.isTransfer || other.type != TransactionType.TRANSFER && other.isTransfer
        return if (readsAsTransfer) TransferWindow else ChargeWindow
    }

    private fun apart(a: CapturedTransactionEntity, b: CapturedTransactionEntity): Duration =
        Duration.between(a.receivedAt, b.receivedAt).abs()

    /** From the account the money left to the one it reached, for [new] and its [other] half. */
    fun transferAccounts(new: CapturedTransactionEntity, other: CapturedTransactionEntity): Pair<UUID, UUID>? {
        val (from, to) = when {
            other.type == TransactionType.TRANSFER -> other.accountId to other.toAccountId
            new.type == TransactionType.EXPENSE -> new.accountId to other.accountId
            else -> other.accountId to new.accountId
        }
        return if (from != null && to != null && from != to) from to to else null
    }

    /** Identifies the same message (banks sometimes deliver an SMS twice). */
    fun fingerprint(sender: String, body: String): String =
        "${sender.lowercase().filter { it.isLetterOrDigit() }}|${body.trim().hashCode()}"
}
