package com.ivy.insights

import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ReportsCalculatorTest {

    private val today = LocalDate.of(2026, 10, 10)
    private val october = ReportPeriod.month(YearMonth.of(2026, 10))

    private fun expense(day: LocalDate, amount: Double, category: String? = "food", title: String? = null) =
        FlowRecord(day, FlowKind.Expense, amount, category, title = title)

    private val records = listOf(
        expense(LocalDate.of(2026, 10, 1), 100.0, "food", "Carrefour"),
        expense(LocalDate.of(2026, 10, 3), 50.0, "food", "carrefour "),
        expense(LocalDate.of(2026, 10, 3), 250.0, "car", "ENOC"),
        expense(LocalDate.of(2026, 10, 9), 100.0, null, "Keeta"),
        expense(LocalDate.of(2026, 9, 15), 400.0, "food", "Carrefour"),
        FlowRecord(LocalDate.of(2026, 10, 1), FlowKind.Income, 7500.0, "work", title = "Salary"),
    )

    @Test
    fun `summary averages a running month over the days so far`() {
        val summary = ReportsCalculator.summary(records, FlowKind.Expense, october, PeriodMode.Month, today)
        summary.total shouldBe 500.0
        summary.count shouldBe 4
        summary.previousTotal shouldBe 400.0
        summary.changePercent shouldBe 25
        summary.dailyAverage shouldBe 50.0 // 500 over 10 days
    }

    @Test
    fun `summary of a finished month uses all its days`() {
        val september = ReportPeriod.month(YearMonth.of(2026, 9))
        val summary = ReportsCalculator.summary(records, FlowKind.Expense, september, PeriodMode.Month, today)
        summary.dailyAverage shouldBe 400.0 / 30
        summary.changePercent shouldBe null
    }

    @Test
    fun `category shares add up and uncategorized gets its own key`() {
        val shares = ReportsCalculator.categoryShares(records, FlowKind.Expense, october)
        shares.map { it.key } shouldBe listOf("car", "food", ReportsCalculator.UNCATEGORIZED)
        shares.map { it.amount } shouldBe listOf(250.0, 150.0, 100.0)
        shares.sumOf { it.share } shouldBe 1.0
        shares[1].count shouldBe 2
    }

    @Test
    fun `top titles merge spellings`() {
        val titles = ReportsCalculator.topTitles(records, FlowKind.Expense, october)
        titles.first() shouldBe TitleTotal("ENOC", 250.0, 1)
        titles[1].amount shouldBe 150.0
        titles[1].count shouldBe 2
    }

    @Test
    fun `top titles for one category`() {
        ReportsCalculator.topTitles(records, FlowKind.Expense, october, categoryKey = "car")
            .map { it.title } shouldBe listOf("ENOC")
    }

    @Test
    fun `cumulative pace stops at today in the current month`() {
        val pace = ReportsCalculator.cumulativeByDay(records, FlowKind.Expense, YearMonth.of(2026, 10), today)
        pace.size shouldBe 10
        pace[0] shouldBe 100.0
        pace[2] shouldBe 400.0
        pace.last() shouldBe 500.0
        ReportsCalculator.cumulativeByDay(records, FlowKind.Expense, YearMonth.of(2026, 9), today).size shouldBe 30
    }

    @Test
    fun `months of year`() {
        val months = ReportsCalculator.monthsOfYear(records, FlowKind.Expense, 2026)
        months.size shouldBe 12
        months[8] shouldBe 400.0
        months[9] shouldBe 500.0
    }

    @Test
    fun `transactions newest first`() {
        val list = ReportsCalculator.transactions(records, FlowKind.Expense, october)
        list.first().title shouldBe "Keeta"
        list.size shouldBe 4
    }
}
