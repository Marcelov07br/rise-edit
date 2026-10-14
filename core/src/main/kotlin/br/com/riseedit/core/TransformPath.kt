package br.com.riseedit.core

import kotlin.math.abs

/**
 * Smooth keyframe interpolation shared by preview and export.
 *
 * Position follows a cubic Hermite path: Catmull-Rom tangents (time-aware) through interior
 * keyframes and zero tangents at the first/last keyframe, so motion eases in and out and passes
 * smoothly through every key. Scale, rotation and opacity ease in/out per segment; rotation takes
 * the shortest angular path.
 */
object TransformPath {
    fun at(
        keys: List<TransformKeyframe>,
        timeMs: Long,
        base: TransformKeyframe = TransformKeyframe(timeMs)
    ): TransformKeyframe {
        if (keys.isEmpty()) return base.copy(sourceMs = timeMs)
        val sorted = if (keys.zipWithNext().all { (a, b) ->
                a.sourceMs <= b.sourceMs
            }
        ) {
            keys
        } else {
            keys.sortedBy { it.sourceMs }
        }
        if (timeMs <= sorted.first().sourceMs) return sorted.first().copy(sourceMs = timeMs)
        if (timeMs >= sorted.last().sourceMs) return sorted.last().copy(sourceMs = timeMs)
        val i = sorted.indexOfLast { it.sourceMs <= timeMs }.coerceAtMost(sorted.size - 2)
        val a = sorted[i]
        val b = sorted[i + 1]
        val span = (b.sourceMs - a.sourceMs).toDouble()
        if (span <= 0) return b.copy(sourceMs = timeMs)
        val u = (timeMs - a.sourceMs) / span
        val prev = sorted.getOrNull(i - 1)
        val next = sorted.getOrNull(i + 2)
        val x = hermite(
            a.x,
            b.x,
            tangent(prev, a, b) { it.x } * span,
            tangent(a, b, next) { it.x } * span,
            u
        )
        val y = hermite(
            a.y,
            b.y,
            tangent(prev, a, b) { it.y } * span,
            tangent(a, b, next) { it.y } * span,
            u
        )
        val e = ease(u)
        return TransformKeyframe(
            timeMs,
            x.toFloat(),
            y.toFloat(),
            lerp(a.scale, b.scale, e),
            a.rotation + shortestDelta(a.rotation, b.rotation) * e.toFloat(),
            lerp(a.opacity, b.opacity, e)
        )
    }

    /** Tangent at [mid] scaled to the segment [mid]→[after]; zero at path ends. */
    private fun tangent(
        before: TransformKeyframe?,
        mid: TransformKeyframe,
        after: TransformKeyframe?,
        value: (TransformKeyframe) -> Float
    ): Double = 0.0.takeIf { before == null || after == null } ?: run {
        val dt = (after!!.sourceMs - before!!.sourceMs).toDouble()
        if (dt <= 0) 0.0 else (value(after) - value(before)) / dt
    }

    private fun hermite(p0: Float, p1: Float, slope0: Double, slope1: Double, u: Double): Double {
        val u2 = u * u
        val u3 = u2 * u
        val h00 = 2 * u3 - 3 * u2 + 1
        val h10 = u3 - 2 * u2 + u
        val h01 = -2 * u3 + 3 * u2
        val h11 = u3 - u2
        return h00 * p0 + h10 * slope0 + h01 * p1 + h11 * slope1
    }

    /** Smooth ease-in-out (cubic smoothstep). */
    fun ease(u: Double): Double = u * u * (3 - 2 * u)

    /** Signed smallest rotation from [from] to [to], in degrees within (-180, 180]. */
    fun shortestDelta(from: Float, to: Float): Float {
        val d = ((to - from) % 360f + 540f) % 360f - 180f
        return if (d == -180f && to > from) 180f else d
    }

    private fun lerp(a: Float, b: Float, t: Double) = (a + (b - a) * t).toFloat()

    /** True if [keys] has a keyframe within [toleranceMs] of [timeMs]. */
    fun hasKeyAt(
        keys: List<TransformKeyframe>,
        timeMs: Long,
        toleranceMs: Long = KEY_TOLERANCE_MS
    ) = keys.any { abs(it.sourceMs - timeMs) <= toleranceMs }

    /** Replaces a keyframe within tolerance of [key]'s time or inserts it, keeping order. */
    fun upsert(
        keys: List<TransformKeyframe>,
        key: TransformKeyframe,
        toleranceMs: Long = KEY_TOLERANCE_MS
    ): List<TransformKeyframe> {
        val existing = keys.firstOrNull { abs(it.sourceMs - key.sourceMs) <= toleranceMs }
        val time = existing?.sourceMs ?: key.sourceMs
        return (
            keys.filterNot {
                it === existing
            } + key.copy(sourceMs = time)
            ).sortedBy { it.sourceMs }
    }

    fun remove(
        keys: List<TransformKeyframe>,
        timeMs: Long,
        toleranceMs: Long = KEY_TOLERANCE_MS
    ): List<TransformKeyframe> = keys.filterNot { abs(it.sourceMs - timeMs) <= toleranceMs }

    const val KEY_TOLERANCE_MS = 40L
}
