package com.ivy.wallet.domain.deprecated.logic

import com.ivy.base.legacy.Transaction
import com.ivy.base.model.TransactionType
import com.ivy.base.time.TimeProvider
import com.ivy.data.db.dao.read.PlannedPaymentRuleDao
import com.ivy.data.repository.TransactionRepository
import com.ivy.data.repository.mapper.TransactionMapper
import com.ivy.legacy.datamodel.PlannedPaymentRule
import com.ivy.legacy.datamodel.temp.toDomain
import com.ivy.legacy.datamodel.temp.toLegacyDomain
import com.ivy.legacy.incrementDate
import timber.log.Timber
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import com.ivy.data.model.Transaction as DomainTransaction

/**
 * Creates the upcoming transactions of planned payment rules.
 *
 * Previously occurrences were only generated when a rule was saved and were capped
 * (3 years / 72 instances), so long-running rules silently stopped showing up.
 * Now [topUpAll] runs on every app start and keeps every rule filled
 * [HORIZON] ahead of today.
 */
class PlannedPaymentsGenerator @Inject constructor(
    private val transactionMapper: TransactionMapper,
    private val transactionRepository: TransactionRepository,
    private val plannedPaymentRuleDao: PlannedPaymentRuleDao,
    private val timeProvider: TimeProvider,
) {
    companion object {
        private const val GENERATED_INSTANCES_LIMIT = 400
        private val HORIZON: Duration = Duration.ofDays(366)

        // How far back we create missed (overdue) occurrences when catching up
        private val CATCH_UP_WINDOW: Duration = Duration.ofDays(45)

        // How far back a brand-new rule with a past start date is generated
        private val NEW_RULE_BACKFILL: Duration = Duration.ofDays(366)
    }

    /**
     * Regenerates a rule after it was created or edited:
     * removes its unpaid occurrences and creates fresh ones.
     */
    suspend fun generate(rule: PlannedPaymentRule) {
        transactionRepository.deletedByRecurringRuleIdAndNoDateTime(
            recurringRuleId = rule.id
        )
        fill(rule)
    }

    /**
     * Adds any missing upcoming occurrences for all rules without touching
     * the existing ones. Safe to call repeatedly.
     */
    suspend fun topUpAll() {
        plannedPaymentRuleDao.findAll().forEach { entity ->
            try {
                fill(entity.toLegacyDomain())
            } catch (e: Exception) {
                Timber.e(e, "Failed to top-up planned payment ${entity.id}")
            }
        }
    }

    private suspend fun fill(rule: PlannedPaymentRule) {
        val existing = transactionRepository.findAllByRecurringRuleId(recurringRuleId = rule.id)
        if (rule.oneTime) {
            if (existing.isEmpty()) {
                generateTransaction(rule, rule.startDate ?: return)
            }
        } else {
            generateRecurring(rule, existing)
        }
    }

    @Suppress("ReturnCount")
    private suspend fun generateRecurring(
        rule: PlannedPaymentRule,
        existing: List<DomainTransaction>
    ) {
        val startDate = rule.startDate ?: return
        val intervalN = rule.intervalN?.takeIf { it > 0 } ?: return
        val intervalType = rule.intervalType ?: return

        val now = timeProvider.utcNow()
        val dates = plannedOccurrences(
            startDate = startDate,
            lastKnown = existing.maxOfOrNull { it.occurrenceDate() },
            floor = if (existing.isEmpty()) {
                now.minus(NEW_RULE_BACKFILL)
            } else {
                now.minus(CATCH_UP_WINDOW)
            },
            until = now.plus(HORIZON),
            limit = GENERATED_INSTANCES_LIMIT,
            next = { intervalType.incrementDate(it, intervalN.toLong()) }
        )
        dates.forEach { generateTransaction(rule, it) }
    }

    /** The due date this transaction covers. */
    private fun DomainTransaction.occurrenceDate(): Instant = if (settled) {
        metadata.paidForDateTime ?: time
    } else {
        time
    }

    private suspend fun generateTransaction(rule: PlannedPaymentRule, dueDate: Instant) {
        val isTransfer = rule.type == TransactionType.TRANSFER
        Transaction(
            type = rule.type,
            accountId = rule.accountId,
            recurringRuleId = rule.id,
            categoryId = rule.categoryId,
            amount = rule.amount.toBigDecimal(),
            title = rule.title,
            description = rule.description,
            dueDate = dueDate,
            dateTime = null,
            toAccountId = if (isTransfer) rule.toAccountId else null,
            toAmount = (rule.toAmount?.takeIf { isTransfer } ?: rule.amount).toBigDecimal(),
            isSynced = false
        ).toDomain(transactionMapper)?.let {
            transactionRepository.save(it)
        }
    }
}

/**
 * Pure scheduling: the occurrences of a recurring rule that still need to be created.
 *
 * @param lastKnown latest occurrence that already exists (paid or pending), if any.
 * Only occurrences strictly after it are returned so nothing gets duplicated and
 * skipped occurrences are not resurrected.
 * @param floor occurrences before this are not created (avoids flooding with years
 * of overdue items when a rule had stopped generating).
 */
@Suppress("LongParameterList", "MagicNumber")
fun plannedOccurrences(
    startDate: Instant,
    lastKnown: Instant?,
    floor: Instant,
    until: Instant,
    limit: Int,
    next: (Instant) -> Instant,
): List<Instant> {
    // tolerate small time differences between the stored and the computed date
    val after = lastKnown?.plus(Duration.ofHours(12))
    val result = mutableListOf<Instant>()
    var date = startDate
    var guard = 0
    while (!date.isAfter(until) && result.size < limit && guard++ < 100_000) {
        val isNew = after == null || date.isAfter(after)
        if (isNew && !date.isBefore(floor)) {
            result.add(date)
        }
        val nextDate = next(date)
        if (!nextDate.isAfter(date)) break
        date = nextDate
    }
    return result
}
