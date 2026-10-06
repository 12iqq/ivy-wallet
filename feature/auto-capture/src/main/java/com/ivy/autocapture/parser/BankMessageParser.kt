package com.ivy.autocapture.parser

/**
 * Turns bank SMS / notification text into transactions.
 *
 * Pure Kotlin (no Android) so it can be unit-tested easily.
 * To support a new bank add a [BankProfile] to [UaeBanks] - most banks work
 * with the shared heuristics in [GenericMessageParser] and only need sender ids.
 * Banks with unusual wording can supply [BankProfile.patterns].
 */
enum class Direction {
    /** Money left the account / was charged to the card. */
    Debit,

    /** Money came in (salary, refund, card payment received...). */
    Credit,
}

@Suppress("DataClassTypedIDs")
data class ParsedMessage(
    val bankId: String,
    val direction: Direction,
    val amount: Double,
    val currency: String,
    val merchant: String?,
    /** Last 4 digits of the card or account the message refers to. */
    val last4: String?,
    /** true = credit card, false = debit card / account, null = unknown. */
    val isCreditCard: Boolean?,
    val availableBalance: Double?,
)

/**
 * A custom regex for banks whose wording the generic parser can't handle.
 * Named groups: `amount` (required), `currency`, `merchant`, `last4`.
 */
@Suppress("DataClassDefaultValues")
data class MessagePattern(
    val regex: Regex,
    val direction: Direction,
    val isCreditCard: Boolean? = null,
)

@Suppress("DataClassDefaultValues", "DataClassTypedIDs")
data class BankProfile(
    val id: String,
    val displayName: String,
    /** SMS sender ids (alphanumeric) and notification app names/packages. */
    val senders: List<String>,
    val patterns: List<MessagePattern> = emptyList(),
    /** Text the bank uses in messages that are never transactions. */
    val ignorePhrases: List<String> = emptyList(),
    val defaultCurrency: String = "AED",
)

@Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "MaximumLineLength")
object GenericMessageParser {

    // Currencies commonly seen in UAE bank messages
    private const val CURRENCIES =
        "AED|USD|EUR|GBP|SAR|QAR|OMR|KWD|BHD|INR|PKR|PHP|EGP|JOD|CAD|AUD|CHF|JPY|CNY|TRY|THB|SGD|LKR|NPR|BDT|Dhs|DHS|Dh|AE"

    private val amountAfterCurrency = Regex(
        """(?<![A-Za-z])($CURRENCIES)\.?\s*([0-9][0-9,]*(?:\.[0-9]{1,3})?)""",
        RegexOption.IGNORE_CASE
    )
    private val amountBeforeCurrency = Regex(
        """([0-9][0-9,]*(?:\.[0-9]{1,3})?)\s*($CURRENCIES)(?![A-Za-z])""",
        RegexOption.IGNORE_CASE
    )

    private val ignoreAlways = listOf(
        "otp", "one time password", "one-time password", "verification code",
        "do not share", "don't share", "activation code", "passcode",
        "declined", "unsuccessful", "not successful", "failed", "insufficient",
        "was rejected", "has been blocked", "will be debited", "is due on",
        "minimum payment", "min. payment", "min payment", "statement is ready",
        "statement has been generated", "pre-approved", "apply now", "offer",
    )

    private val creditFirstPhrases = listOf(
        "payment received", "payment of", // checked together with "received" below
        "received towards", "thank you for your payment", "thank you for the payment",
        "has been credited", "is credited", "was credited", "credited to your",
        "credited in your", "refund", "reversal", "reversed", "cashback", "cash back",
        "salary", "deposited", "deposit of", "you have received", "received from",
        "inward remittance", "incoming transfer",
    )

    private val debitPhrases = listOf(
        "purchase", "purchased", "spent", "used for", "used at", "was used", "paid",
        "debited", "withdrawn", "withdrawal", "transaction of", "trxn", "txn of", "trx of",
        "charged", "payment to", "transfer to", "transferred to", "sent to", "pos ",
        "atm", "bill payment", "direct debit",
    )

    private val balanceKeywords = Regex(
        """(avl|avail|available|bal\b|balance|limit|outstanding|o/s|due amount)""",
        RegexOption.IGNORE_CASE
    )

    private val creditCardHints = listOf(
        "credit card", "cr.card", "cr card", "cr. card", "creditcard", "avl cr",
        "available credit", "credit limit", "cr. limit", "cr limit", "card limit",
    )
    private val debitCardHints = listOf(
        "debit card",
        "account",
        "a/c",
        "acct",
        "ac no",
        "current a",
        "savings",
    )

    private val last4Regex = Regex(
        """(?:(?:[Xx*•]{2,}[ -]?)|(?:ending\s*(?:with|in|no\.?)?\s*[:\-]?\s*)|(?:card\s*(?:no\.?|number)?\s*[:\-]?\s*[Xx*•]*))(\d{4})\b""",
        RegexOption.IGNORE_CASE
    )

    private val merchantRegex = Regex(
        """\b(?:at|@|to|from|merchant[:\s])\s+([A-Za-z0-9][A-Za-z0-9 &'*.,\-_/]{1,60}?)""" +
            """(?=\s+on\s|\s+was\s|\s+is\s|\s+has\s|\s+dated|\s+date|\s+using|\s+with|\s+via|\s+by\s|\s+for\s|\s+avl|\s+avail|\s+bal|""" +
            """\s+ref|\s+txn|\.\s|\.$|;|\s+-\s|\s{2,}|\s+\d{1,2}[/\-]|$)""",
        RegexOption.IGNORE_CASE
    )

    fun parse(profile: BankProfile, body: String): ParsedMessage? {
        val text = body.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
        val lower = text.lowercase()

        if (isIgnored(profile, lower)) return null

        profile.patterns.forEach { pattern ->
            parseWithPattern(profile, pattern, text)?.let { return it }
        }

        val direction = direction(lower) ?: return null
        val (amount, currency) = transactionAmount(text, profile.defaultCurrency) ?: return null
        if (amount <= 0.0) return null

        return ParsedMessage(
            bankId = profile.id,
            direction = direction,
            amount = amount,
            currency = currency,
            merchant = merchant(text),
            last4 = last4Regex.find(text)?.groupValues?.get(1),
            isCreditCard = isCreditCard(lower),
            availableBalance = availableBalance(text),
        )
    }

    private fun isIgnored(profile: BankProfile, lower: String): Boolean {
        val phrases = ignoreAlways + profile.ignorePhrases.map { it.lowercase() }
        return phrases.any { phrase ->
            // match whole words for short phrases like "otp" / "offer"
            if (phrase.length <= 5) {
                Regex("""\b${Regex.escape(phrase)}\b""").containsMatchIn(lower)
            } else {
                lower.contains(phrase)
            }
        }
    }

    private fun parseWithPattern(
        profile: BankProfile,
        pattern: MessagePattern,
        text: String
    ): ParsedMessage? {
        val match = pattern.regex.find(text) ?: return null
        fun group(name: String): String? = runCatching { match.groups[name]?.value }.getOrNull()
        val amount = group("amount")?.toAmount() ?: return null
        return ParsedMessage(
            bankId = profile.id,
            direction = pattern.direction,
            amount = amount,
            currency = group("currency")?.normalizeCurrency() ?: profile.defaultCurrency,
            merchant = group("merchant")?.cleanMerchant(),
            last4 = group("last4") ?: last4Regex.find(text)?.groupValues?.get(1),
            isCreditCard = pattern.isCreditCard ?: isCreditCard(text.lowercase()),
            availableBalance = availableBalance(text),
        )
    }

    fun direction(lower: String): Direction? {
        val paymentReceived = (lower.contains("payment") || lower.contains("paid")) &&
            (lower.contains("received") || lower.contains("thank you"))
        if (paymentReceived) return Direction.Credit

        val creditIndex = creditFirstPhrases
            .filter { it != "payment of" }
            .mapNotNull { phrase -> lower.indexOf(phrase).takeIf { it >= 0 } }
            .minOrNull()
        val debitIndex = debitPhrases
            .mapNotNull { phrase -> lower.indexOf(phrase).takeIf { it >= 0 } }
            .minOrNull()

        return when {
            creditIndex == null && debitIndex == null -> null
            creditIndex == null -> Direction.Debit
            debitIndex == null -> Direction.Credit
            creditIndex < debitIndex -> Direction.Credit
            else -> Direction.Debit
        }
    }

    /** The first amount that isn't a balance / limit figure. */
    private fun transactionAmount(text: String, defaultCurrency: String): Pair<Double, String>? {
        val candidates = (
            amountAfterCurrency.findAll(text).map { m ->
                Triple(m.range.first, m.groupValues[2], m.groupValues[1])
            } + amountBeforeCurrency.findAll(text).map { m ->
                Triple(m.range.first, m.groupValues[1], m.groupValues[2])
            }
            ).sortedBy { it.first }.toList()

        val chosen = candidates.firstOrNull { (index, _, _) -> !isBalanceFigure(text, index) }
            ?: return null
        val amount = chosen.second.toAmount() ?: return null
        val currency = chosen.third.normalizeCurrency() ?: defaultCurrency
        return amount to currency
    }

    private fun isBalanceFigure(text: String, index: Int): Boolean {
        val before = text.substring((index - 28).coerceAtLeast(0), index)
        return balanceKeywords.containsMatchIn(before)
    }

    private fun availableBalance(text: String): Double? {
        val keyword = balanceKeywords.findAll(text).lastOrNull() ?: return null
        val after = text.substring(keyword.range.first)
        val m = amountAfterCurrency.find(after) ?: amountBeforeCurrency.find(after) ?: return null
        val raw = if (m.groupValues[1].first().isDigit()) m.groupValues[1] else m.groupValues[2]
        return raw.toAmount()
    }

    private fun merchant(text: String): String? = merchantRegex.findAll(text)
        .map { it.groupValues[1].cleanMerchant() }
        .firstOrNull { candidate ->
            candidate != null &&
                !candidate.startsWith("your", ignoreCase = true) &&
                !candidate.matches(Regex("""(?i)[x*\d\s]+""")) &&
                !candidate.contains("card", ignoreCase = true) &&
                !candidate.contains("account", ignoreCase = true)
        }

    private fun isCreditCard(lower: String): Boolean? = when {
        creditCardHints.any { lower.contains(it) } -> true
        debitCardHints.any { lower.contains(it) } -> false
        else -> null
    }
}

internal fun String.toAmount(): Double? = replace(",", "").trim().toDoubleOrNull()

@Suppress("MagicNumber")
internal fun String.normalizeCurrency(): String? = when (val upper = uppercase()) {
    "DHS", "DH", "AE" -> "AED"
    else -> upper.takeIf { it.length == 3 }
}

@Suppress("MagicNumber")
internal fun String.cleanMerchant(): String? {
    val cleaned = trim()
        .trimEnd('.', ',', '-', ' ')
        .replace(Regex("""(?i)[-\s]+(AE|ARE|UAE)$"""), "")
        .replace(Regex("""\s{2,}"""), " ")
        .trim()
    return cleaned.takeIf { it.length >= 2 }
}
