package br.com.riseedit.core.keys

import kotlin.math.abs
import kotlin.math.roundToLong

/** Which clock a track's key times are measured on. */
enum class TimeDomain {
    /** Source media time of the clip (survives trims and speed changes). */
    SOURCE,

    /** Time since the item starts on the output timeline (overlays and texts). */
    LOCAL_OUTPUT
}

/** How a segment travels from its key to the next one. */
sealed interface Easing {
    /** Keeps the key value until the next key. */
    data object Hold : Easing

    data object Linear : Easing

    /** Cubic Hermite with Catmull-Rom tangents (zero at path ends): passes smoothly through keys. */
    data object Smooth : Easing

    /** CSS-style timing curve from (0,0) to (1,1); x1/x2 must lie in 0..1. */
    data class CubicBezier(val x1: Double, val y1: Double, val x2: Double, val y2: Double) :
        Easing {
        init {
            require(x1 in 0.0..1.0 && x2 in 0.0..1.0)
        }

        fun progress(u: Double): Double {
            if (u <= 0) return 0.0
            if (u >= 1) return 1.0
            // Newton steps on x(t) with bisection fallback.
            var t = u
            repeat(8) {
                val err = bez(t, x1, x2) - u
                if (abs(err) < 1e-9) return bez(t, y1, y2)
                val d = dBez(t, x1, x2)
                if (abs(d) < 1e-9) return@repeat
                t = (t - err / d).coerceIn(0.0, 1.0)
            }
            var lo = 0.0
            var hi = 1.0
            repeat(40) {
                t = (lo + hi) / 2
                if (bez(t, x1, x2) < u) lo = t else hi = t
            }
            return bez(t, y1, y2)
        }

        private fun bez(t: Double, a: Double, b: Double): Double {
            val m = 1 - t
            return 3 * m * m * t * a + 3 * m * t * t * b + t * t * t
        }

        private fun dBez(t: Double, a: Double, b: Double): Double {
            val m = 1 - t
            return 3 * m * m * a + 6 * m * t * (b - a) + 3 * t * t * (1 - b)
        }

        companion object {
            /** Exactly the legacy cubic smoothstep u²(3−2u). */
            val SMOOTHSTEP = CubicBezier(1.0 / 3, 0.0, 2.0 / 3, 1.0)
        }
    }
}

/** Value arithmetic needed to interpolate a parameter. */
interface TrackValue<T> {
    fun toDouble(value: T): Double
    fun fromDouble(value: Double): T

    /** Signed distance from [a] to [b]; angles override this to take the shortest path. */
    fun delta(a: T, b: T): Double = toDouble(b) - toDouble(a)

    object Scalar : TrackValue<Float> {
        override fun toDouble(value: Float) = value.toDouble()
        override fun fromDouble(value: Double) = value.toFloat()
    }

    object Angle : TrackValue<Float> {
        override fun toDouble(value: Float) = value.toDouble()
        override fun fromDouble(value: Double) = value.toFloat()
        override fun delta(a: Float, b: Float): Double {
            val d = ((b - a) % 360f + 540f) % 360f - 180f
            return (if (d == -180f && b > a) 180f else d).toDouble()
        }
    }
}

/** One key: [easing] shapes the segment that starts here. */
data class Key<T>(val timeMs: Long, val value: T, val easing: Easing = Easing.Smooth)

/** Animation of a single parameter. Evaluation is pure and identical for preview and export. */
data class ParameterTrack<T>(
    val domain: TimeDomain,
    val keys: List<Key<T>> = emptyList(),
    val values: TrackValue<T>
) {
    private val sorted = if (keys.zipWithNext().all { (a, b) ->
            a.timeMs <= b.timeMs
        }
    ) {
        keys
    } else {
        keys.sortedBy { it.timeMs }
    }

    val isAnimated get() = keys.size > 1

    fun at(timeMs: Long, base: T): T = at(timeMs.toDouble(), base)

    fun at(timeMs: Double, base: T): T {
        if (sorted.isEmpty()) return base
        if (timeMs <= sorted.first().timeMs) return sorted.first().value
        if (timeMs >= sorted.last().timeMs) return sorted.last().value
        val i = sorted.indexOfLast { it.timeMs <= timeMs }.coerceAtMost(sorted.size - 2)
        val a = sorted[i]
        val b = sorted[i + 1]
        val span = (b.timeMs - a.timeMs).toDouble()
        if (span <= 0) return b.value
        val u = (timeMs - a.timeMs) / span
        val start = values.toDouble(a.value)
        val delta = values.delta(a.value, b.value)
        return values.fromDouble(
            when (val e = a.easing) {
                Easing.Hold -> start
                Easing.Linear -> start + delta * u
                is Easing.CubicBezier -> start + delta * e.progress(u)
                Easing.Smooth -> {
                    val prev = sorted.getOrNull(i - 1)
                    val next = sorted.getOrNull(i + 2)
                    hermite(
                        start,
                        start + delta,
                        tangent(prev, b) * span,
                        tangent(a, next) * span,
                        u
                    )
                }
            }
        )
    }

    /** Catmull-Rom slope at the key between [before] and [after]; zero at path ends. */
    private fun tangent(before: Key<T>?, after: Key<T>?): Double {
        if (before == null || after == null) return 0.0
        val dt = (after.timeMs - before.timeMs).toDouble()
        return if (dt <=
            0
        ) {
            0.0
        } else {
            (values.toDouble(after.value) - values.toDouble(before.value)) / dt
        }
    }

    /** Sets the value at [timeMs] (snapped to [grid]), replacing a key on the same frame. */
    fun upsert(timeMs: Long, value: T, grid: FrameGrid, easing: Easing? = null): ParameterTrack<T> {
        val frame = grid.frameOf(timeMs)
        val existing = sorted.firstOrNull { grid.frameOf(it.timeMs) == frame }
        val key = Key(
            existing?.timeMs ?: grid.timeOf(frame),
            value,
            easing ?: existing?.easing ?: Easing.Smooth
        )
        return copy(keys = (sorted.filterNot { it === existing } + key).sortedBy { it.timeMs })
    }

    fun remove(timeMs: Long, grid: FrameGrid): ParameterTrack<T> {
        val frame = grid.frameOf(timeMs)
        return copy(keys = sorted.filterNot { grid.frameOf(it.timeMs) == frame })
    }

    fun hasKeyAt(timeMs: Long, grid: FrameGrid) = sorted.any { grid.sameFrame(it.timeMs, timeMs) }

    companion object {
        private fun hermite(p0: Double, p1: Double, m0: Double, m1: Double, u: Double): Double {
            val u2 = u * u
            val u3 = u2 * u
            return (2 * u3 - 3 * u2 + 1) * p0 + (u3 - 2 * u2 + u) * m0 + (-2 * u3 + 3 * u2) * p1 +
                (u3 - u2) * m1
        }
    }
}

/** Frame quantization for key times: two times are the same key when they land on the same frame. */
data class FrameGrid(val fps: Int = 30) {
    init {
        require(fps in 1..240)
    }

    fun frameOf(timeMs: Long): Long = (timeMs * fps / 1000.0).roundToLong()
    fun timeOf(frame: Long): Long = (frame * 1000.0 / fps).roundToLong()
    fun quantize(timeMs: Long): Long = timeOf(frameOf(timeMs))
    fun sameFrame(a: Long, b: Long) = frameOf(a) == frameOf(b)
}
