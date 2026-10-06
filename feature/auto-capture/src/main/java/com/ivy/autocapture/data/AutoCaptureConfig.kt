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
    /** Normalized merchant -> category id, learned from what the user picked. */
    val learnedCategories: Map<String, String> = emptyMap(),
    /** Normalized merchant -> title the user prefers (e.g. "CARREFOUR MOE" -> "Groceries"). */
    val learnedTitles: Map<String, String> = emptyMap(),
)

object CaptureLogic {
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

    private const val MERCHANT_KEY_WORDS = 3

    /** "CARREFOUR, DUBAI MALL 123" -> "carrefour dubai mall" */
    fun merchantKey(merchant: String): String = merchant.lowercase()
        .replace(Regex("""[^a-z ]"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .split(" ")
        .take(MERCHANT_KEY_WORDS)
        .joinToString(" ")

    /** Identifies the same message (banks sometimes deliver an SMS twice). */
    fun fingerprint(sender: String, body: String): String =
        "${sender.lowercase().filter { it.isLetterOrDigit() }}|${body.trim().hashCode()}"
}
