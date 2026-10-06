package com.ivy.planned.installments

import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.LocalDate

class InstallmentScheduleTest {

    @Test
    fun `splits evenly into monthly payments`() {
        val plan = InstallmentSchedule.split(400.0, 4, LocalDate.of(2026, 10, 6), InstallmentInterval.Monthly)
        plan.map { it.amount } shouldBe listOf(100.0, 100.0, 100.0, 100.0)
        plan.map { it.date } shouldBe listOf(
            LocalDate.of(2026, 10, 6),
            LocalDate.of(2026, 11, 6),
            LocalDate.of(2026, 12, 6),
            LocalDate.of(2027, 1, 6),
        )
        plan.map { it.number } shouldBe listOf(1, 2, 3, 4)
    }

    @Test
    fun `last payment takes the rounding remainder`() {
        val plan = InstallmentSchedule.split(100.0, 3, LocalDate.of(2026, 10, 6), InstallmentInterval.Monthly)
        plan.map { it.amount } shouldBe listOf(33.33, 33.33, 33.34)
        plan.sumOf { it.amount * 100 }.toLong() shouldBe 10_000L
    }

    @Test
    fun `odd fils add up exactly`() {
        val plan = InstallmentSchedule.split(1234.57, 4, LocalDate.of(2026, 10, 6), InstallmentInterval.Monthly)
        plan.map { it.amount } shouldBe listOf(308.64, 308.64, 308.64, 308.65)
    }

    @Test
    fun `every two weeks`() {
        val plan = InstallmentSchedule.split(
            200.0,
            4,
            LocalDate.of(2026, 10, 6),
            InstallmentInterval.EveryTwoWeeks
        )
        plan.last().date shouldBe LocalDate.of(2026, 11, 17)
    }

    @Test
    fun `month end clamps`() {
        val plan = InstallmentSchedule.split(300.0, 3, LocalDate.of(2027, 1, 31), InstallmentInterval.Monthly)
        plan.map { it.date } shouldBe listOf(
            LocalDate.of(2027, 1, 31),
            LocalDate.of(2027, 2, 28),
            LocalDate.of(2027, 3, 31),
        )
    }

    @Test
    fun titles() {
        InstallmentSchedule.title(InstallmentProvider.Tabby, " Amazon ", 2, 4) shouldBe "Tabby · Amazon (2/4)"
        InstallmentSchedule.title(InstallmentProvider.Other, "IKEA", 1, 3) shouldBe "IKEA (1/3)"
        InstallmentSchedule.title(InstallmentProvider.Tamara, "", 1, 4) shouldBe "Tamara (1/4)"
    }
}
