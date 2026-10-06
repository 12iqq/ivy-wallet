package com.ivy.accounts

import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.LocalDate

class NextDayOfMonthTest {

    @Test
    fun `later this month`() {
        nextDayOfMonth(LocalDate.of(2026, 10, 6), 25) shouldBe LocalDate.of(2026, 10, 25)
    }

    @Test
    fun `today counts`() {
        nextDayOfMonth(LocalDate.of(2026, 10, 25), 25) shouldBe LocalDate.of(2026, 10, 25)
    }

    @Test
    fun `already passed goes to next month`() {
        nextDayOfMonth(LocalDate.of(2026, 10, 26), 25) shouldBe LocalDate.of(2026, 11, 25)
    }

    @Test
    fun `day 31 clamps to short months`() {
        nextDayOfMonth(LocalDate.of(2026, 2, 10), 31) shouldBe LocalDate.of(2026, 2, 28)
        nextDayOfMonth(LocalDate.of(2026, 3, 31), 31) shouldBe LocalDate.of(2026, 3, 31)
        nextDayOfMonth(LocalDate.of(2026, 4, 1), 31) shouldBe LocalDate.of(2026, 4, 30)
    }

    @Test
    fun `year rollover`() {
        nextDayOfMonth(LocalDate.of(2026, 12, 28), 5) shouldBe LocalDate.of(2027, 1, 5)
    }
}
