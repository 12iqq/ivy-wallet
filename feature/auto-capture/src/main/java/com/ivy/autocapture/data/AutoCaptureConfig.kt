package com.ivy.autocapture.data

import kotlinx.serialization.Serializable

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

object CaptureLogic {
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

    /** Identifies the same message (banks sometimes deliver an SMS twice). */
    fun fingerprint(sender: String, body: String): String =
        "${sender.lowercase().filter { it.isLetterOrDigit() }}|${body.trim().hashCode()}"
}
