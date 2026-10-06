package com.ivy.wallet.domain.deprecated.logic

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class PlannedOccurrencesTest {

    private fun date(y: Int, m: Int, d: Int): Instant =
        java.time.LocalDate.of(y, m, d).atStartOfDay().toInstant(ZoneOffset.UTC)

    private val monthly: (Instant) -> Instant =
        { it.atZone(ZoneOffset.UTC).plusMonths(1).toInstant() }

    @Test
    fun `new rule generates from start date until horizon`() {
        val result = plannedOccurrences(
            startDate = date(2026, 1, 5),
            lastKnown = null,
            floor = date(2025, 1, 1),
            until = date(2026, 6, 1),
            limit = 100,
            next = monthly,
        )

        result shouldBe listOf(
            date(2026, 1, 5),
            date(2026, 2, 5),
            date(2026, 3, 5),
            date(2026, 4, 5),
            date(2026, 5, 5),
        )
    }

    @Test
    fun `top-up only adds occurrences after the last known one`() {
        val result = plannedOccurrences(
            startDate = date(2026, 1, 5),
            lastKnown = date(2026, 3, 5),
            floor = date(2025, 1, 1),
            until = date(2026, 6, 1),
            limit = 100,
            next = monthly,
        )

        result shouldBe listOf(date(2026, 4, 5), date(2026, 5, 5))
    }

    @Test
    fun `small time differences do not duplicate the last occurrence`() {
        val result = plannedOccurrences(
            startDate = date(2026, 1, 5),
            lastKnown = date(2026, 3, 5).minusSeconds(60),
            floor = date(2025, 1, 1),
            until = date(2026, 4, 1),
            limit = 100,
            next = monthly,
        )

        result.shouldBeEmpty()
    }

    @Test
    fun `rule that stopped years ago does not flood with old overdue items`() {
        // rule started 2021, generation stopped in 2024; today is Oct 2026
        val result = plannedOccurrences(
            startDate = date(2021, 1, 1),
            lastKnown = date(2024, 1, 1),
            floor = date(2026, 8, 22),
            until = date(2027, 1, 15),
            limit = 100,
            next = monthly,
        )

        result shouldBe listOf(
            date(2026, 9, 1),
            date(2026, 10, 1),
            date(2026, 11, 1),
            date(2026, 12, 1),
            date(2027, 1, 1),
        )
    }

    @Test
    fun `respects the limit`() {
        val result = plannedOccurrences(
            startDate = date(2026, 1, 1),
            lastKnown = null,
            floor = date(2025, 1, 1),
            until = date(2030, 1, 1),
            limit = 3,
            next = { it.plusSeconds(86_400) },
        )

        result.size shouldBe 3
    }
}
