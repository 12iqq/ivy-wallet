package com.ivy.data.model

import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.LocalDate

class CreditCardCycleTest {

    @Test
    fun `cycle started after this month's statement day`() {
        CreditCardCycle.cycleStart(LocalDate.of(2026, 10, 20), 15) shouldBe LocalDate.of(2026, 10, 16)
    }

    @Test
    fun `on the statement day the cycle is still the previous one`() {
        CreditCardCycle.cycleStart(LocalDate.of(2026, 10, 15), 15) shouldBe LocalDate.of(2026, 9, 16)
    }

    @Test
    fun `before the statement day the cycle started last month`() {
        CreditCardCycle.cycleStart(LocalDate.of(2026, 10, 3), 15) shouldBe LocalDate.of(2026, 9, 16)
    }

    @Test
    fun `statement day 31 clamps in short months`() {
        CreditCardCycle.cycleStart(LocalDate.of(2026, 3, 10), 31) shouldBe LocalDate.of(2026, 3, 1)
        CreditCardCycle.cycleStart(LocalDate.of(2026, 5, 1), 31) shouldBe LocalDate.of(2026, 5, 1)
    }

    @Test
    fun `cycle crosses the new year`() {
        CreditCardCycle.cycleStart(LocalDate.of(2027, 1, 2), 25) shouldBe LocalDate.of(2026, 12, 26)
    }

    @Test
    fun `days until due`() {
        val details = CreditCardDetails(creditLimit = null, statementDay = 1, paymentDueDay = 26)
        CreditCardCycle.daysUntilDue(LocalDate.of(2026, 10, 23), details) shouldBe 3
        CreditCardCycle.daysUntilDue(LocalDate.of(2026, 10, 26), details) shouldBe 0
        CreditCardCycle.daysUntilDue(LocalDate.of(2026, 10, 27), details) shouldBe 30
        CreditCardCycle.daysUntilDue(LocalDate.of(2026, 10, 27), null) shouldBe null
    }
}
