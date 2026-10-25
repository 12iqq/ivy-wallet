package com.ivy.insights.utilities

import com.ivy.data.db.entity.UtilityBillEntity
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.LocalDate

class UtilityCalculatorTest {

    private val today = LocalDate.of(2026, 10, 25)

    private fun bill(
        end: LocalDate,
        electricity: Double,
        water: Double,
        account: String = "020000123456",
        arrears: Double = 0.0,
    ): UtilityBill = UtilityBill(
        account = account,
        start = end.minusMonths(1).plusDays(1),
        end = end,
        electricity = electricity,
        water = water,
        gas = 20.0,
        municipality = 10.0,
        vat = 5.0,
        others = 0.0,
        arrears = arrears,
        total = electricity + water + 35.0 + arrears,
        due = null,
        currency = "AED",
    )

    @Test
    fun `report lists bills newest first with averages and totals`() {
        val report = UtilityCalculator.report(
            listOf(
                bill(LocalDate.of(2026, 8, 17), electricity = 400.0, water = 70.0),
                bill(LocalDate.of(2025, 12, 16), electricity = 150.0, water = 40.0),
                bill(LocalDate.of(2026, 10, 17), electricity = 300.0, water = 60.0, arrears = 100.0),
            ),
            today
        )!!

        report.bills.map { it.end } shouldBe listOf(
            LocalDate.of(2026, 10, 17),
            LocalDate.of(2026, 8, 17),
            LocalDate.of(2025, 12, 16),
        )
        report.averageElectricity shouldBe 850.0 / 3
        report.averageWater shouldBe 170.0 / 3
        report.averageGas shouldBe 20.0
        // arrears were billed before: counting them again would double them
        report.averageCharges shouldBe (395.0 + 505.0 + 225.0) / 3
        report.thisYear shouldBe 395.0 + 505.0
        report.allTime shouldBe 395.0 + 505.0 + 225.0
    }

    @Test
    fun `no bills, no report`() {
        UtilityCalculator.report(emptyList(), today) shouldBe null
    }

    @Test
    fun `bill length counts both ends`() {
        bill(LocalDate.of(2026, 10, 17), electricity = 1.0, water = 1.0)
            .copy(start = LocalDate.of(2026, 9, 17))
            .days shouldBe 31
    }

    @Test
    fun `accounts billed most recently come first`() {
        UtilityCalculator.accounts(
            listOf(
                bill(LocalDate.of(2026, 8, 17), 1.0, 1.0, account = "A"),
                bill(LocalDate.of(2026, 10, 17), 1.0, 1.0, account = "B"),
                bill(LocalDate.of(2026, 9, 17), 1.0, 1.0, account = "A"),
            )
        ) shouldBe listOf("B", "A")
    }

    @Test
    fun `change since the previous bill`() {
        UtilityCalculator.changePercent(110.0, 100.0) shouldBe 10
        UtilityCalculator.changePercent(281.45, 335.20) shouldBe -16
        UtilityCalculator.changePercent(20.0, 0.0) shouldBe null
    }

    @Test
    fun `stored bills become report bills`() {
        val entity = UtilityBillEntity(
            id = "SEWA:020000123456:2026-10-17",
            provider = "SEWA",
            account = "020000123456",
            periodStart = "2026-09-17",
            periodEnd = "2026-10-17",
            electricity = 281.45,
            water = 61.30,
            gas = 20.0,
            municipality = 18.70,
            vat = 16.55,
            others = 0.0,
            arrears = 0.0,
            total = 398.0,
            dueDate = "2026-11-04",
            currency = "AED",
        )

        val bill = UtilityCalculator.toBill(entity)!!
        bill.start shouldBe LocalDate.of(2026, 9, 17)
        bill.due shouldBe LocalDate.of(2026, 11, 4)
        bill.rest shouldBe 18.70 + 16.55
        UtilityCalculator.toBill(entity.copy(periodEnd = "2026-13-01")) shouldBe null
    }
}
