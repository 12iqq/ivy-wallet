package com.ivy.planned.installments

import com.ivy.base.legacy.Transaction
import com.ivy.base.model.TransactionType
import com.ivy.data.db.dao.write.WritePlannedPaymentRuleDao
import com.ivy.data.repository.TransactionRepository
import com.ivy.data.repository.mapper.TransactionMapper
import com.ivy.legacy.datamodel.PlannedPaymentRule
import com.ivy.legacy.datamodel.temp.toDomain
import com.ivy.wallet.domain.deprecated.logic.PlannedPaymentsGenerator
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

@Suppress("DataClassTypedIDs")
data class InstallmentPlanInput(
    val provider: InstallmentProvider,
    val merchant: String,
    val total: Double,
    val count: Int,
    val interval: InstallmentInterval,
    val firstDate: LocalDate,
    /** Tabby & Tamara charge the first payment at checkout. */
    val firstPaidNow: Boolean,
    val accountId: UUID,
    val categoryId: UUID?,
)

/**
 * Turns a buy-now-pay-later purchase into payments: the first one as a normal
 * expense (when already paid) and the rest as one-time planned payments, so they
 * show up in Planned payments and in Upcoming until they're paid.
 */
class InstallmentCreator @Inject constructor(
    private val plannedPaymentRuleWriter: WritePlannedPaymentRuleDao,
    private val plannedPaymentsGenerator: PlannedPaymentsGenerator,
    private val transactionRepository: TransactionRepository,
    private val transactionMapper: TransactionMapper,
) {
    suspend fun create(input: InstallmentPlanInput) {
        val zone = ZoneId.systemDefault()
        val installments = InstallmentSchedule.split(
            total = input.total,
            count = input.count,
            firstDate = input.firstDate,
            interval = input.interval,
        )
        installments.forEach { installment ->
            val title = InstallmentSchedule.title(input.provider, input.merchant, installment.number, input.count)
            if (installment.number == 1 && input.firstPaidNow) {
                Transaction(
                    accountId = input.accountId,
                    type = TransactionType.EXPENSE,
                    amount = installment.amount.toBigDecimal(),
                    title = title,
                    categoryId = input.categoryId,
                    dateTime = Instant.now(),
                ).toDomain(transactionMapper)?.let { transactionRepository.save(it) }
            } else {
                val rule = PlannedPaymentRule(
                    startDate = installment.date.atTime(DUE_TIME).atZone(zone).toInstant(),
                    intervalN = null,
                    intervalType = null,
                    oneTime = true,
                    type = TransactionType.EXPENSE,
                    accountId = input.accountId,
                    amount = installment.amount,
                    categoryId = input.categoryId,
                    title = title,
                )
                plannedPaymentRuleWriter.save(rule.toEntity())
                plannedPaymentsGenerator.generate(rule)
            }
        }
    }

    private companion object {
        val DUE_TIME: LocalTime = LocalTime.NOON
    }
}
