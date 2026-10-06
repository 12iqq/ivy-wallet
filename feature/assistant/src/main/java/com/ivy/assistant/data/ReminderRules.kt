package com.ivy.assistant.data

import com.ivy.data.model.CreditCardCycle
import com.ivy.data.model.CreditCardDetails
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** When a reminder should fire. Pure functions so they're easy to test. */
object ReminderRules {

    @Suppress("DataClassTypedIDs")
    data class CardReminder(
        val key: String,
        val dueDate: LocalDate,
        val daysLeft: Int,
    )

    enum class BudgetLevel(val percent: Int) {
        Almost(ALMOST_PERCENT),
        Over(OVER_PERCENT),
    }

    @Suppress("DataClassTypedIDs")
    data class BudgetAlert(
        val key: String,
        val level: BudgetLevel,
        /** Every key this alert covers (reaching 100% also covers 80%). */
        val coveredKeys: Set<String>,
    )

    /**
     * A card reminder fires once in each window before the due date:
     * "a few days before" (2..[daysBefore]), "tomorrow" and "today".
     * Windows (not exact days) make it robust when the daily job runs late.
     */
    @Suppress("ReturnCount")
    fun cardReminder(
        accountId: String,
        owed: Double,
        details: CreditCardDetails?,
        today: LocalDate,
        daysBefore: Int,
        sentKeys: Set<String>,
    ): CardReminder? {
        if (owed <= 0.0) return null
        val daysLeft = CreditCardCycle.daysUntilDue(today, details) ?: return null
        val window = when {
            daysLeft == 0 -> "today"
            daysLeft == 1 -> "tomorrow"
            daysLeft <= daysBefore -> "soon"
            else -> return null
        }
        val dueDate = today.plusDays(daysLeft.toLong())
        val key = "card:$dueDate:$accountId:$window"
        return if (key in sentKeys) null else CardReminder(key, dueDate, daysLeft)
    }

    fun budgetAlert(
        budgetId: String,
        periodStart: LocalDate,
        budgetAmount: Double,
        spent: Double,
        sentKeys: Set<String>,
    ): BudgetAlert? {
        if (budgetAmount <= 0.0) return null
        val percent = spent / budgetAmount * FULL_PERCENT
        val almostKey = "budget:$periodStart:$budgetId:${BudgetLevel.Almost.percent}"
        val overKey = "budget:$periodStart:$budgetId:${BudgetLevel.Over.percent}"
        return when {
            percent >= OVER_PERCENT && overKey !in sentKeys ->
                BudgetAlert(overKey, BudgetLevel.Over, setOf(almostKey, overKey))

            percent >= ALMOST_PERCENT && percent < OVER_PERCENT && almostKey !in sentKeys ->
                BudgetAlert(almostKey, BudgetLevel.Almost, setOf(almostKey))

            else -> null
        }
    }

    @Suppress("ReturnCount")
    fun isBackupDue(now: Instant, lastBackup: Instant?, frequency: BackupFrequency): Boolean {
        if (frequency == BackupFrequency.Off) return false
        if (lastBackup == null) return true
        // an hour of slack so a daily job that runs slightly early still backs up
        val interval = Duration.ofDays(frequency.days).minusHours(1)
        return !Duration.between(lastBackup, now).minus(interval).isNegative
    }

    /** Forgets keys whose date (2nd segment) is older than [keepDays]. */
    fun pruneKeys(keys: Set<String>, today: LocalDate, keepDays: Long = KEEP_DAYS): Set<String> =
        keys.filterTo(mutableSetOf()) { key ->
            val date = key.split(":").getOrNull(1)
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            date == null || !date.isBefore(today.minusDays(keepDays))
        }

    private const val ALMOST_PERCENT = 80
    private const val OVER_PERCENT = 100
    private const val FULL_PERCENT = 100.0
    private const val KEEP_DAYS = 70L
}
