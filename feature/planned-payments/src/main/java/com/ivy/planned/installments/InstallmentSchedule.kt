package com.ivy.planned.installments

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** Buy-now-pay-later providers common in the UAE, with their usual plan. */
enum class InstallmentProvider(
    val displayName: String,
    val defaultCount: Int,
    val defaultInterval: InstallmentInterval,
) {
    Tabby("Tabby", defaultCount = 4, defaultInterval = InstallmentInterval.Monthly),
    Tamara("Tamara", defaultCount = 4, defaultInterval = InstallmentInterval.Monthly),
    Other("Other", defaultCount = 4, defaultInterval = InstallmentInterval.Monthly),
}

enum class InstallmentInterval {
    Monthly,
    EveryTwoWeeks,
}

data class Installment(
    /** 1-based, as shown to the user ("2/4"). */
    val number: Int,
    val date: LocalDate,
    val amount: Double,
)

object InstallmentSchedule {
    const val MIN_COUNT = 2
    const val MAX_COUNT = 12
    private const val WEEKS_BETWEEN = 2L
    private const val MONEY_SCALE = 2

    /**
     * Splits [total] into [count] payments starting on [firstDate].
     * Amounts are rounded down to fils; the last payment absorbs the remainder,
     * so the payments always add up to exactly [total].
     */
    fun split(
        total: Double,
        count: Int,
        firstDate: LocalDate,
        interval: InstallmentInterval,
    ): List<Installment> {
        require(count in MIN_COUNT..MAX_COUNT) { "count must be in $MIN_COUNT..$MAX_COUNT" }
        require(total > 0) { "total must be positive" }

        val totalExact = BigDecimal.valueOf(total).setScale(MONEY_SCALE, RoundingMode.HALF_UP)
        val each = totalExact.divide(BigDecimal(count), MONEY_SCALE, RoundingMode.DOWN)
        val last = totalExact - each * BigDecimal(count - 1)

        return (0 until count).map { i ->
            Installment(
                number = i + 1,
                date = when (interval) {
                    // plusMonths clamps to the end of shorter months (31 Jan -> 28 Feb)
                    InstallmentInterval.Monthly -> firstDate.plusMonths(i.toLong())
                    InstallmentInterval.EveryTwoWeeks -> firstDate.plusWeeks(WEEKS_BETWEEN * i)
                },
                amount = (if (i == count - 1) last else each).toDouble(),
            )
        }
    }

    /** "Tabby · Amazon (2/4)" */
    fun title(provider: InstallmentProvider, merchant: String, number: Int, count: Int): String {
        val name = merchant.trim()
        val prefix = if (provider == InstallmentProvider.Other) "" else "${provider.displayName} · "
        return if (name.isEmpty()) {
            "${provider.displayName} ($number/$count)"
        } else {
            "$prefix$name ($number/$count)"
        }
    }
}
