package com.ivy.desktop.reports

import com.ivy.desktop.SampleData
import com.ivy.desktop.data.LedgerBuilder
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HighlightsTest {

    private val ledger = LedgerBuilder.build(
        SampleData.backup(),
        SampleData.zone,
        SampleData.today.atTime(18, 0).toInstant(SampleData.zone),
    )
    private val highlights = requireNotNull(HighlightsCalculator.of(ledger, SampleData.today))

    @Test
    fun `totals since the first transaction`() {
        assertEquals(ledger.since, highlights.since)
        assertEquals(highlights.spent, highlights.years.sumOf { it.expense }, 0.01)
        assertEquals(highlights.received, highlights.years.sumOf { it.income }, 0.01)
        assertEquals(highlights.count, highlights.years.sumOf { it.count })
        assertEquals(listOf(2025, 2026), highlights.years.map { it.year })
    }

    @Test
    fun `where the money goes`() {
        assertEquals("Rent", highlights.categories.first().name)
        assertEquals(6_500.0, highlights.biggest?.amount)
        val often = assertNotNull(highlights.mostOften)
        assertTrue(often.count >= assertNotNull(highlights.mostSpentAt).count)
        assertEquals("Rent", highlights.mostSpentAt?.title)
    }

    @Test
    fun `when the money goes`() {
        assertEquals(7, highlights.weekdays.size)
        assertEquals(highlights.spent, highlights.weekdays.sum(), 0.01)
        assertEquals(24, highlights.hours.size)
        // the made-up purchases are all at noon
        assertEquals(1, highlights.hoursSeen)
        assertTrue(highlights.noSpendDays in 0..highlights.days)
        val priciest = assertNotNull(highlights.priciestMonth)
        assertTrue(priciest.expense >= assertNotNull(highlights.cheapestMonth).expense)
        // rent pulls the average up, so half the purchases cost less than it
        assertTrue(highlights.typicalPurchase > 0 && highlights.typicalPurchase < highlights.averagePurchase)
    }
}
