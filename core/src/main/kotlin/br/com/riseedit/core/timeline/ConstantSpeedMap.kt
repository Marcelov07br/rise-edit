package br.com.riseedit.core.timeline

import java.math.BigDecimal
import java.math.RoundingMode

/** Decimal arithmetic avoids Double losing integer microseconds above 2^53. */
data class ConstantSpeedMap(override val sourceRange: TimeRange, val speed: Double) : TimeMap {
    init {
        require(speed.isFinite() && speed > 0) { "Speed must be finite and positive" }
    }

    private val decimalSpeed = BigDecimal.valueOf(speed)
    override val targetRange = TimeRange(
        0,
        BigDecimal.valueOf(
            sourceRange.durationUs
        ).divide(decimalSpeed, 0, RoundingMode.HALF_UP).longValueExact()
    )

    override fun sourceToTarget(sourceUs: Long): Long {
        val bounded = sourceUs.coerceIn(sourceRange.startUs, sourceRange.endUs)
        if (bounded == sourceRange.endUs) return targetRange.endUs
        return BigDecimal.valueOf(bounded - sourceRange.startUs)
            .divide(decimalSpeed, 0, RoundingMode.HALF_UP)
            .min(BigDecimal.valueOf(targetRange.endUs)).longValueExact()
    }

    override fun targetToSource(targetUs: Long): Long {
        val bounded = targetUs.coerceIn(0, targetRange.endUs)
        if (bounded == targetRange.endUs) return sourceRange.endUs
        val offset = BigDecimal.valueOf(bounded).multiply(decimalSpeed)
            .setScale(0, RoundingMode.HALF_UP)
            .min(BigDecimal.valueOf(sourceRange.durationUs)).longValueExact()
        return sourceRange.startUs + offset
    }

    override fun speedAt(targetUs: Long): Double = speed
}
