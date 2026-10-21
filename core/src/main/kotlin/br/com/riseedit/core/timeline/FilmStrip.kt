package br.com.riseedit.core.timeline

import br.com.riseedit.core.Clip
import kotlin.math.ceil
import kotlin.math.floor

/**
 * 0.18.1: timeline thumbnails anchored to SOURCE time. Tile k always shows the frame at source
 * time k·step, so trimming an edge only reveals/hides real tiles (no stretching or re-indexing),
 * and the step only changes with zoom (snapped to fixed values so frames stay cached).
 */
object FilmStrip {
    data class Tile(val sourceMs: Long, val xPx: Double, val widthPx: Double)

    private val steps = longArrayOf(
        40, 100, 200, 250, 500, 1000, 2000, 5000, 10_000, 15_000, 30_000, 60_000, 120_000, 300_000
    )

    /** Source ms per tile so a tile is at least [targetPx] wide at [pxPerSecond] and clip speed. */
    fun stepMs(clip: Clip, pxPerSecond: Double, targetPx: Double): Long {
        val speed = clip.speedAt(clip.startMs).coerceAtLeast(0.01)
        val desired = targetPx / pxPerSecond * 1000.0 * speed
        return steps.firstOrNull { it >= desired } ?: steps.last()
    }

    /** Output (project) ms since the clip start at which [sourceMs] plays; honors speed points. */
    fun outputAt(clip: Clip, sourceMs: Long): Double {
        val s = sourceMs.coerceIn(clip.startMs, clip.endMs)
        val cuts = (
            listOf(clip.startMs) +
                clip.speedPoints.map { it.sourceMs }.filter {
                    it in
                        (clip.startMs + 1) until clip.endMs
                } +
                clip.endMs
            ).distinct().sorted()
        var out = 0.0
        for ((a, b) in cuts.zipWithNext()) {
            if (s <= a) break
            out += (minOf(s, b) - a) / clip.speedAt(a)
        }
        return out
    }

    /**
     * Tiles of [clip] whose box starts at x = 0 (the clip's left edge, may be off screen) and that
     * intersect [visibleFromPx, visibleToPx] (same coords). The first/last tiles may start before
     * 0 or end after the clip: the caller clips them at the clip's edges.
     */
    fun tiles(
        clip: Clip,
        pxPerSecond: Double,
        targetPx: Double,
        visibleFromPx: Double = Double.NEGATIVE_INFINITY,
        visibleToPx: Double = Double.POSITIVE_INFINITY
    ): List<Tile> {
        val step = stepMs(clip, pxPerSecond, targetPx)
        val first = floor(clip.startMs.toDouble() / step).toLong()
        val last = ceil(clip.endMs.toDouble() / step).toLong() - 1
        val pps = pxPerSecond / 1000.0
        fun x(source: Long): Double {
            val inside = outputAt(clip, source)
            val speed = clip.speedAt(clip.startMs)
            // Before the clip start (first partial tile) extrapolate with the start speed.
            return if (source <
                clip.startMs
            ) {
                (source - clip.startMs) / speed * pps
            } else {
                inside * pps
            }
        }
        return (first..last).mapNotNull { k ->
            val s = k * step
            val e = s + step
            val x0 = x(s)
            val x1 = if (e >
                clip.endMs
            ) {
                x(clip.endMs) + (e - clip.endMs) / clip.speedAt(clip.endMs - 1) * pps
            } else {
                x(e)
            }
            if (x1 < visibleFromPx || x0 > visibleToPx) null else Tile(s, x0, x1 - x0)
        }
    }
}
