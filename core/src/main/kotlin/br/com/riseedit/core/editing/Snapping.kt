package br.com.riseedit.core.editing

import kotlin.math.abs

/** Magnet snapping on a single axis (timeline microseconds or preview coordinates). */
object Snapping {
    data class Result(val value: Double, val target: Double?)

    fun snap(value: Double, targets: Iterable<Double>, threshold: Double): Result {
        val best = targets.minByOrNull { abs(it - value) }
        return if (best != null &&
            abs(best - value) <= threshold
        ) {
            Result(best, best)
        } else {
            Result(value, null)
        }
    }

    /** Snaps whichever edge of [start, start+length] is closest to a target; returns the new start. */
    fun snapSpan(
        start: Double,
        length: Double,
        targets: Iterable<Double>,
        threshold: Double
    ): Result {
        val head = snap(start, targets, threshold)
        val tail = snap(start + length, targets, threshold)
        val headDistance = head.target?.let { abs(it - start) } ?: Double.MAX_VALUE
        val tailDistance = tail.target?.let { abs(it - start - length) } ?: Double.MAX_VALUE
        return when {
            head.target == null && tail.target == null -> Result(start, null)
            headDistance <= tailDistance -> head
            else -> Result(tail.value - length, tail.target)
        }
    }
}

/** Snap guides for the preview frame, in normalized device coordinates (-1..1, y up). */
object FrameSnap {
    enum class Guide { CENTER, MIN, MAX }

    data class Axis(val center: Double, val guide: Guide?)

    /** Snaps an item's center or edges (half extent [half]) to the frame center or borders. */
    fun axis(center: Double, half: Double, threshold: Double = 0.04): Axis {
        val candidates =
            listOf(
                Triple(0.0, center, Guide.CENTER),
                Triple(-1.0 + half, center, Guide.MIN),
                Triple(1.0 - half, center, Guide.MAX)
            )
        val best = candidates.minBy { abs(it.first - it.second) }
        return if (abs(best.first - center) <=
            threshold
        ) {
            Axis(best.first, best.third)
        } else {
            Axis(center, null)
        }
    }

    /** Snaps rotation (degrees) to multiples of 90 within [threshold] degrees. */
    fun angle(degrees: Float, threshold: Float = 4f): Pair<Float, Boolean> {
        val nearest = Math.round(degrees / 90f) * 90f
        return if (abs(nearest - degrees) <= threshold) nearest to true else degrees to false
    }
}

/** Zoom limits and ruler ticks for the timeline viewport. */
object TimelineZoom {
    const val MAX_VISIBLE_US = 600_000_000L

    /** Lowest pixels-per-second: fits the whole project, but never more than 10 minutes. */
    fun minPixelsPerSecond(usableWidthPx: Double, durationUs: Long): Double {
        val visibleUs = durationUs.coerceIn(1_000_000L, MAX_VISIBLE_US)
        return usableWidthPx.coerceAtLeast(1.0) * 1_000_000 / visibleUs
    }

    private val steps =
        longArrayOf(
            100_000, 200_000, 500_000, 1_000_000, 2_000_000, 5_000_000, 10_000_000, 15_000_000,
            30_000_000, 60_000_000, 120_000_000, 300_000_000, 600_000_000
        )

    /** Smallest "nice" tick step whose spacing is at least [minSpacingPx]. */
    fun tickStepUs(pixelsPerSecond: Double, minSpacingPx: Double): Long =
        steps.firstOrNull { it * pixelsPerSecond / 1_000_000 >= minSpacingPx } ?: steps.last()

    fun label(timeUs: Double, stepUs: Long): String {
        val totalMs = timeUs.toLong() / 1000
        val seconds = totalMs / 1000
        return when {
            stepUs < 1_000_000 -> "%d.%ds".format(seconds, (totalMs % 1000) / 100)
            seconds < 60 -> "${seconds}s"
            else -> "%d:%02d".format(seconds / 60, seconds % 60)
        }
    }
}
