package com.ivy.desktop.ui

import com.ivy.desktop.SampleData
import com.ivy.desktop.data.LedgerBuilder
import com.ivy.desktop.reports.Format
import com.ivy.desktop.reports.Reports
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChartTipsTest {

    private val overview = Reports.overview(
        LedgerBuilder.build(
            SampleData.backup(),
            SampleData.zone,
            SampleData.today.atTime(18, 0).toInstant(SampleData.zone),
        ),
        SampleData.today,
    )

    @Test
    fun `each month's tip has its income, spending and what was left`() {
        val tips = incomeSpendingTips(overview, "AED", SampleData.today)

        assertEquals(12, tips.size)
        assertEquals("November 2025", tips.first().title)
        assertEquals("October 2026, so far", tips.last().title)
        val october = overview.months.last()
        assertEquals(listOf("Income", "Spending"), tips.last().rows.take(2).map { it.label })
        assertEquals(Format.money(october.income, "AED"), tips.last().rows[0].value)
        assertEquals(Format.money(october.expense, "AED"), tips.last().rows[1].value)
        val left = if (october.income >= october.expense) "Saved" else "Spent more than came in"
        assertEquals(left, tips.last().rows[2].label)
    }

    @Test
    fun `each month's balance, and how it changed`() {
        val tips = balanceTips(overview, "AED", SampleData.today)

        assertEquals("Today", tips.last().title)
        assertEquals("End of September 2026", tips[10].title)
        // nothing to compare the first month with
        assertEquals(1, tips.first().rows.size)
        assertTrue(tips.last().rows[1].label.endsWith("from Sep"))
    }
}
