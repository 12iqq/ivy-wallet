package com.ivy.autocapture.parser

/**
 * Google Wallet shows a notification after each tap-to-pay, e.g.
 * title "Carrefour", text "AED 23.50 with Visa ••1234".
 * We turn it into a sentence the [GenericMessageParser] understands.
 */
object WalletNotifications {

    private val hasAmount = Regex("""\d+(?:[.,]\d+)?""")

    /** @return a parsable message, or null for notifications that aren't payments (offers, passes...). */
    fun toMessage(title: String?, text: String?): String? {
        val body = text?.trim().orEmpty()
        val merchant = title?.trim().orEmpty()
        if (body.isEmpty() || !hasAmount.containsMatchIn(body)) return null
        return if (merchant.isEmpty()) "Paid $body" else "Paid $body at $merchant"
    }

    fun parse(title: String?, text: String?): ParsedMessage? =
        toMessage(title, text)?.let { GenericMessageParser.parse(UaeBanks.GOOGLE_WALLET, it) }
}
