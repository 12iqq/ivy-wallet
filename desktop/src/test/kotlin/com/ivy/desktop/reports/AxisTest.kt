package com.ivy.desktop.reports

import org.junit.Test
import kotlin.test.assertEquals

class AxisTest {

    @Test
    fun `round steps that cover the biggest month`() {
        assertEquals(AxisScale(min = 0.0, max = 30_000.0, step = 10_000.0), Axis.scale(0.0, 26_500.0))
        assertEquals(AxisScale(min = 0.0, max = 15_000.0, step = 5_000.0), Axis.scale(0.0, 11_000.0))
        assertEquals(AxisScale(min = 0.0, max = 1_000.0, step = 250.0), Axis.scale(0.0, 990.0))
    }

    @Test
    fun `a balance that went below zero`() {
        val scale = Axis.scale(-2_000.0, 127_000.0)

        assertEquals(AxisScale(min = -50_000.0, max = 150_000.0, step = 50_000.0), scale)
        assertEquals(listOf(-50_000.0, 0.0, 50_000.0, 100_000.0, 150_000.0), Axis.ticks(scale))
    }

    @Test
    fun `nothing to show still gets an axis`() {
        val scale = Axis.scale(0.0, 0.0)

        assertEquals(AxisScale(min = 0.0, max = 1.0, step = 1.0), scale)
        assertEquals(listOf(0.0, 1.0), Axis.ticks(scale))
    }
}
