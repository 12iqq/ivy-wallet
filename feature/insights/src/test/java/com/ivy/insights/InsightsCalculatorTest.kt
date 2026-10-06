package com.ivy.insights

import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class InsightsCalculatorTest {

    private val today = LocalDate.of(2026, 10, 6)

    private fun expense(date: LocalDate, amount: Double, category: String? = "food") =
        FlowRecord(date, FlowKind.Expense, amount, category)

    private fun income(date: LocalDate, amount: Double) = FlowRecord(date, FlowKind.Income, amount, "work")

    @Test
    fun `monthly totals cover the last months oldest first`() {
        val totals = InsightsCalculator.monthlyTotals(
            listOf(
                expense(LocalDate.of(2026, 10, 1), 50.0),
                expense(LocalDate.of(2026, 9, 30), 20.0),
                income(LocalDate.of(2026, 9, 27), 7500.0),
                expense(LocalDate.of(2025, 1, 1), 999.0), // too old
            ),
            today,
            months = 3
        )
        totals.map { it.month } shouldBe listOf(YearMonth.of(2026, 8), YearMonth.of(2026, 9), YearMonth.of(2026, 10))
        totals[1].income shouldBe 7500.0
        totals[1].expense shouldBe 20.0
        totals[2].expense shouldBe 50.0
        totals[0].expense shouldBe 0.0
    }

    @Test
    fun `net worth includes everything before the window`() {
        val points = InsightsCalculator.netWorth(
            listOf(
                BalanceDelta(LocalDate.of(2020, 1, 1), 1000.0),
                BalanceDelta(LocalDate.of(2026, 9, 15), -200.0),
                BalanceDelta(LocalDate.of(2026, 10, 2), 50.0),
            ),
            today,
            months = 2
        )
        points.map { it.value } shouldBe listOf(800.0, 850.0)
    }

    @Test
    fun `category changes rank by the bigger month`() {
        val changes = InsightsCalculator.categoryChanges(
            listOf(
                expense(LocalDate.of(2026, 10, 2), 100.0, "food"),
                expense(LocalDate.of(2026, 9, 2), 300.0, "car"),
                expense(LocalDate.of(2026, 9, 3), 80.0, "food"),
                income(LocalDate.of(2026, 10, 1), 5000.0),
            ),
            today
        )
        changes shouldBe listOf(
            CategoryChange("car", 0.0, 300.0),
            CategoryChange("food", 100.0, 80.0),
        )
    }

    @Test
    fun `average ignores the current month and empty months`() {
        val totals = listOf(
            MonthTotals(YearMonth.of(2026, 7), 0.0, 0.0),
            MonthTotals(YearMonth.of(2026, 8), 7500.0, 3000.0),
            MonthTotals(YearMonth.of(2026, 9), 7500.0, 5000.0),
            MonthTotals(YearMonth.of(2026, 10), 0.0, 100.0),
        )
        InsightsCalculator.averageMonthlySpend(totals) shouldBe 4000.0
    }
}
