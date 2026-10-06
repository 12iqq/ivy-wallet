package com.ivy.home.quickadd

/** One past transaction, reduced to what quick-add needs. */
@Suppress("DataClassTypedIDs")
data class QuickAddRecord(
    val title: String,
    val categoryId: String?,
    val accountId: String,
    val isIncome: Boolean,
    val amount: Double,
    val daysAgo: Long,
)

/** A one-tap shortcut: "Emarat · Groceries · usually 12 AED". */
@Suppress("DataClassTypedIDs")
data class QuickAddTemplate(
    val title: String,
    val categoryId: String?,
    val accountId: String,
    val isIncome: Boolean,
    val typicalAmount: Double,
    val uses: Int,
) {
    /** Stable identity for lists. */
    val key: String get() = "$title|$categoryId|$accountId|$isIncome"
}

/**
 * Learns shortcuts from the user's own history: the same title, category and account
 * used again and again. Recent use counts more, so habits that changed fade out.
 */
object QuickAddSuggestions {
    const val DEFAULT_LIMIT = 8
    const val MIN_USES = 3
    private const val HALF_WEIGHT_DAYS = 30.0

    fun suggest(
        records: List<QuickAddRecord>,
        limit: Int = DEFAULT_LIMIT,
        minUses: Int = MIN_USES,
    ): List<QuickAddTemplate> = records
        .filter { it.title.isNotBlank() && it.amount > 0 }
        .groupBy { listOf(it.title.trim().lowercase(), it.categoryId, it.accountId, it.isIncome) }
        .values
        .filter { it.size >= minUses }
        .map { group ->
            val newest = group.minBy { it.daysAgo }
            val score = group.sumOf { 1.0 / (1.0 + it.daysAgo / HALF_WEIGHT_DAYS) }
            score to QuickAddTemplate(
                title = newest.title.trim(),
                categoryId = newest.categoryId,
                accountId = newest.accountId,
                isIncome = newest.isIncome,
                typicalAmount = median(group.map { it.amount }),
                uses = group.size,
            )
        }
        .sortedByDescending { it.first }
        .take(limit)
        .map { it.second }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }
}
