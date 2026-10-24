package com.ivy.autocapture.data

/**
 * Bank SMS name a shop the way the card network knows it ("GALADARI ICE CREAM CO LLC",
 * "CARREFOUR MOE DUBAI AE"). These helpers recognise the same shop across messages and
 * turn the raw text into something readable for a first-time title.
 */
object MerchantNames {
    private const val KeyWords = 3
    private const val MaxUpperCaseWord = 3
    private const val MinSpacedTitle = 5

    /** Where the shop is or what kind of company it is - not which shop it is. */
    private val noise = setOf(
        "dubai", "sharjah", "ajman", "fujairah", "ae", "are", "uae",
        "llc", "fze", "fzco", "fzllc", "co", "est", "br",
    )
    private val abuDhabi = Regex("""(?i)\babu\s*dhabi\b""")

    /** "TAP*Keeta", "MYF*KEETA", "WL *STEAM PURCHASE": a payment processor's prefix before the shop. */
    private val processorPrefix = Regex("""^[A-Za-z]{1,4}\s?\*\s*(?=\S)""")

    /**
     * Identifies the shop: "GALADARI ICE CREAM CO LLC" and "Galadari Ice Cream - Dubai"
     * both give "galadari ice cream". null when there's nothing to recognise it by.
     */
    fun key(merchant: String?): String? = merchant.orEmpty()
        .replace(processorPrefix, "")
        .replace(abuDhabi, " ")
        .lowercase()
        .split(Regex("[^a-z]+"))
        .filter { it.length > 1 && it !in noise }
        .take(KeyWords)
        .joinToString(" ")
        .takeIf { it.isNotEmpty() }

    /**
     * Whether two [key]s are the same shop: equal, or one cut shorter ("emarat atrium" and
     * "emarat atrium stor"). A one-word key must be equal, so "amazon" isn't "amazon now".
     */
    fun sameShop(a: String, b: String): Boolean {
        if (a == b) return true
        val (shorter, longer) = if (a.length <= b.length) a to b else b to a
        return ' ' in shorter && longer.startsWith(shorter)
    }

    /**
     * How well one of your titles names the shop with this [key]: the length matched, or 0.
     * "Nesto" names NESTO CENTER LLC, "Baybites" names BAY BITES RESTAURANT, and for
     * AMAZON NOW "Amazon Now" beats "Amazon".
     */
    fun titleMatch(title: String, merchantKey: String): Int {
        val titleKey = key(title) ?: return 0
        val titleLetters = titleKey.filter { it.isLetter() }
        val merchantLetters = merchantKey.filter { it.isLetter() }
        return when {
            merchantKey == titleKey || merchantKey.startsWith("$titleKey ") -> titleKey.length
            // written with different spacing ("Baybites" / "BAY BITES")
            titleLetters.length >= MinSpacedTitle && merchantLetters.startsWith(titleLetters) -> titleLetters.length
            else -> 0
        }
    }

    /**
     * "GALADARI ICE CREAM CO LLC" -> "Galadari Ice Cream", "AMAZON NOW,DUBAI,AE" -> "Amazon Now".
     * Drops trailing locations, company types and store codes; text that isn't all
     * capitals is kept as the bank wrote it.
     */
    fun tidy(merchant: String): String {
        val words = merchant.replace(processorPrefix, "").replace(abuDhabi, " ")
            .split(Regex("""[\s,]+"""))
            .filter { it.isNotEmpty() }
            .toMutableList()
        while (words.size > 1 && words.last().isTrailingNoise()) words.removeAt(words.lastIndex)
        val name = words.joinToString(" ").trim('-', '.', ' ')
        if (name.isEmpty()) return merchant.trim()
        return when {
            name.any { it.isLowerCase() } -> name
            // a short brand on its own ("KFC") reads better in capitals
            ' ' !in name && name.count { it.isLetter() } <= MaxUpperCaseWord -> name
            else -> name.split(" ").joinToString(" ") { it.titleCase() }
        }
    }

    private fun String.isTrailingNoise(): Boolean {
        val letters = lowercase().filter { it.isLetter() }
        return letters.isEmpty() || letters in noise || any { it.isDigit() }
    }

    /** "BASKIN-ROBBINS" -> "Baskin-Robbins", "7ELEVEN" -> "7Eleven". */
    private fun String.titleCase(): String {
        val lower = lowercase()
        val first = lower.indexOfFirst { it.isLetter() }
        return lower.mapIndexed { i, c -> if (i == first || i > 0 && lower[i - 1] == '-') c.uppercaseChar() else c }
            .joinToString("")
    }
}
