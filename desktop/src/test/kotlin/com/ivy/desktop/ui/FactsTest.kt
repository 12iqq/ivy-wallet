package com.ivy.desktop.ui

import com.ivy.desktop.SampleData
import com.ivy.desktop.data.LedgerBuilder
import com.ivy.desktop.reports.HighlightsCalculator
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FactsTest {

    private val highlights = requireNotNull(
        HighlightsCalculator.of(
            LedgerBuilder.build(
                SampleData.backup(),
                SampleData.zone,
                SampleData.today.atTime(18, 0).toInstant(SampleData.zone),
            ),
            SampleData.today,
        ),
    )

    @Test
    fun `fun facts the data can answer`() {
        val facts = facts(highlights, "AED", SampleData.today)

        assertEquals("Rent", facts.first { it.label == "Where most of it goes" }.value)
        assertEquals("6,500.00 AED", facts.first { it.label == "Your biggest purchase" }.value)
        // purchase times that are all the same say nothing about the time of day
        assertTrue(facts.none { it.label == "Your busiest hour" })
    }
}
