package br.com.riseedit.core.editing

/** A bounded time window. Pixels belong to the viewport, not a giant scrolling layout. */
data class TimelineViewport(
    val pixelsPerSecond: Double,
    val originUs: Double,
    val widthPx: Double,
    val minPixelsPerSecond: Double,
    val maxPixelsPerSecond: Double,
    val edgeInsetPx: Double
) {
    init {
        require(minPixelsPerSecond > 0 && maxPixelsPerSecond >= minPixelsPerSecond)
        require(
            pixelsPerSecond.isFinite() && pixelsPerSecond in minPixelsPerSecond..maxPixelsPerSecond
        )
        require(originUs.isFinite() && widthPx.isFinite() && widthPx > 0)
        require(edgeInsetPx.isFinite() && edgeInsetPx >= 0)
    }

    fun timeAtPx(x: Double): Double = originUs + x * 1_000_000 / pixelsPerSecond
    fun pxAtTime(timeUs: Double): Double = (timeUs - originUs) * pixelsPerSecond / 1_000_000

    fun zoomed(factor: Double, focusPx: Double, durationUs: Long): TimelineViewport {
        require(factor.isFinite() && factor > 0 && focusPx.isFinite())
        val limits = fitted(durationUs)
        val anchor = limits.timeAtPx(focusPx)
        val scale = (limits.pixelsPerSecond * factor)
            .coerceIn(limits.minPixelsPerSecond, limits.maxPixelsPerSecond)
        return limits.copy(pixelsPerSecond = scale, originUs = anchor - focusPx * 1_000_000 / scale)
            .bounded(durationUs)
    }

    /** Zoom-out limit follows the project: whole timeline fits, capped at 10 minutes visible. */
    fun fitted(durationUs: Long): TimelineViewport {
        val usable = (widthPx - 2 * edgeInsetPx).coerceAtLeast(widthPx / 2)
        val minimum = TimelineZoom.minPixelsPerSecond(usable, durationUs)
            .coerceAtMost(maxPixelsPerSecond)
        return copy(
            minPixelsPerSecond = minimum,
            pixelsPerSecond = pixelsPerSecond.coerceIn(minimum, maxPixelsPerSecond)
        )
    }

    fun panned(deltaPx: Double, durationUs: Long): TimelineViewport {
        require(deltaPx.isFinite())
        return copy(originUs = originUs - deltaPx * 1_000_000 / pixelsPerSecond).bounded(durationUs)
    }

    fun resized(width: Double, durationUs: Long): TimelineViewport =
        copy(widthPx = width.coerceAtLeast(1.0)).fitted(durationUs).bounded(durationUs)

    fun bounded(durationUs: Long): TimelineViewport {
        require(durationUs >= 0)
        val minimum = -edgeInsetPx * 1_000_000 / pixelsPerSecond
        val maximum = maxOf(
            minimum,
            durationUs - (widthPx - edgeInsetPx) * 1_000_000 / pixelsPerSecond
        )
        return copy(originUs = originUs.coerceIn(minimum, maximum))
    }
}
