package com.ivy.desktop.reports

import org.junit.Test
import java.time.YearMonth
import kotlin.test.assertEquals

class FormatTest {

    @Test
    fun `short amounts for the side of a chart`() {
        assertEquals("0", Format.compact(0.0))
        assertEquals("500", Format.compact(500.0))
        assertEquals("2.5K", Format.compact(2_500.0))
        assertEquals("25K", Format.compact(25_000.0))
        assertEquals("-50K", Format.compact(-50_000.0))
        assertEquals("1.2M", Format.compact(1_200_000.0))
    }

    @Test
    fun `a whole month`() {
        assertEquals("July 2026", Format.month(YearMonth.of(2026, 7)))
    }
}
