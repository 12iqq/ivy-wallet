package com.ivy.desktop.reports

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/** The amounts along the side of a chart: round numbers from [min] to [max], [step] apart. */
data class AxisScale(
    val min: Double,
    val max: Double,
    val step: Double,
)

object Axis {
    private const val STEPS = 4
    private const val TOLERANCE = 1e-9
    private val NiceSteps = listOf(1.0, 2.0, 2.5, 5.0, 10.0)

    /** Round numbers that cover [low] to [high], and 0, in about four steps of at least 1. */
    fun scale(low: Double, high: Double): AxisScale {
        val bottom = minOf(low, 0.0)
        val top = maxOf(high, 0.0)
        val raw = ((top - bottom) / STEPS).takeIf { it > 0 } ?: 1.0
        val magnitude = 10.0.pow(floor(log10(raw)))
        val nice = NiceSteps.first { it * magnitude >= raw * (1 - TOLERANCE) } * magnitude
        val step = maxOf(nice, 1.0)
        val min = floor(bottom / step) * step
        val max = (ceil(top / step) * step).takeIf { it > min } ?: (min + step)
        return AxisScale(min = min, max = max, step = step)
    }

    /** The amounts to write along the side: [AxisScale.min], then every [AxisScale.step] up to [AxisScale.max]. */
    fun ticks(scale: AxisScale): List<Double> {
        val count = ((scale.max - scale.min) / scale.step).roundToInt()
        return (0..count).map { scale.min + it * scale.step }
    }
}
