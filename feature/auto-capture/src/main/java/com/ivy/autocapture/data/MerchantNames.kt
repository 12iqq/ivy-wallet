package com.ivy.autocapture.data

/**
 * Bank SMS name a shop the way the card network knows it ("GALADARI ICE CREAM CO LLC",
 * "CARREFOUR MOE DUBAI AE"). These helpers recognise the same shop across messages and
 * turn the raw text into something readable for a first-time title.
 */
object MerchantNames {
    private const val KeyWords = 3
    private const val MaxUpperCaseWord = 3

    /** Where the shop is or what kind of company it is - not which shop it is. */
    private val noise = setOf(
        "dubai", "sharjah", "ajman", "fujairah", "ae", "are", "uae",
        "llc", "fze", "fzco", "fzllc", "co", "est", "br",
    )
    private val abuDhabi = Regex("""(?i)\babu\s*dhabi\b""")

    /**
     * Identifies the shop: "GALADARI ICE CREAM CO LLC" and "Galadari Ice Cream - Dubai"
     * both give "galadari ice cream". null when there's nothing to recognise it by.
     */
    fun key(merchant: String?): String? = merchant.orEmpty()
        .replace(abuDhabi, " ")
        .lowercase()
        .split(Regex("[^a-z]+"))
        .filter { it.length > 1 && it !in noise }
        .take(KeyWords)
        .joinToString(" ")
        .takeIf { it.isNotEmpty() }

    /**
     * "GALADARI ICE CREAM CO LLC" -> "Galadari Ice Cream", "AMAZON NOW,DUBAI,AE" -> "Amazon Now".
     * Drops trailing locations, company types and store codes; text that isn't all
     * capitals is kept as the bank wrote it.
     */
    fun tidy(merchant: String): String {
        val words = merchant.replace(abuDhabi, " ")
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
