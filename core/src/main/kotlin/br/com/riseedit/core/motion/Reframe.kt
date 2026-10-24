package br.com.riseedit.core.motion

import br.com.riseedit.core.FrameFormat
import br.com.riseedit.core.TransformKeyframe
import kotlin.math.abs
import kotlin.math.exp

/** One analyzed frame: subject center (0..1 of the source width), null = nothing detected. */
data class SubjectSample(val sourceMs: Long, val centerX: Float?, val weight: Float = 1f)

/**
 * 0.24.0 automatic reframe (16:9 → 9:16 etc.). Pure: detection runs in the app (ML Kit); this
 * turns noisy per-frame centers into a calm camera path and a few editable keyframes.
 *
 * 1. gaps (no detection) hold the neighbors (linear between, hold at the ends; all empty → center);
 * 2. Gaussian smoothing over [smoothMs] (zero-phase, so the crop does not lag the subject);
 * 3. dead zone: the path only moves when the subject leaves ±[deadZone] of the window;
 * 4. keyframes: Douglas–Peucker on the path with [tolerance], plus first/last.
 */
object Reframe {
    data class Options(
        val smoothMs: Long = 900,
        val deadZone: Float = 0.06f,
        val tolerance: Float = 0.015f,
        val scale: Float = 1f
    )

    fun fillGaps(samples: List<SubjectSample>): List<Pair<Long, Float>> {
        val sorted = samples.sortedBy { it.sourceMs }
        val known = sorted.withIndex().filter { it.value.centerX != null }
        if (known.isEmpty()) return sorted.map { it.sourceMs to 0.5f }
        return sorted.mapIndexed { i, s ->
            s.centerX?.let { return@mapIndexed s.sourceMs to it }
            val prev = known.lastOrNull { it.index < i }
            val next = known.firstOrNull { it.index > i }
            val x = when {
                prev == null -> next!!.value.centerX!!
                next == null -> prev.value.centerX!!
                else -> {
                    val a = prev.value
                    val b = next.value
                    val t = (s.sourceMs - a.sourceMs).toFloat() / (b.sourceMs - a.sourceMs).coerceAtLeast(1)
                    a.centerX!! + (b.centerX!! - a.centerX) * t
                }
            }
            s.sourceMs to x
        }
    }

    fun smooth(path: List<Pair<Long, Float>>, smoothMs: Long): List<Pair<Long, Float>> {
        if (path.size < 3 || smoothMs <= 0) return path
        val sigma = smoothMs / 2.0
        return path.map { (t, _) ->
            var sum = 0.0
            var total = 0.0
            for ((u, x) in path) {
                val d = (u - t).toDouble()
                if (abs(d) > 3 * sigma) continue
                val w = exp(-d * d / (2 * sigma * sigma))
                sum += w * x
                total += w
            }
            t to (sum / total).toFloat()
        }
    }

    fun deadZone(path: List<Pair<Long, Float>>, zone: Float): List<Pair<Long, Float>> {
        if (path.isEmpty()) return path
        var camera = path.first().second
        return path.map { (t, x) ->
            if (x > camera + zone) camera = x - zone
            if (x < camera - zone) camera = x + zone
            t to camera
        }
    }

    /** Douglas–Peucker indices (always keeps the ends). */
    fun simplify(path: List<Pair<Long, Float>>, tolerance: Float): List<Pair<Long, Float>> {
        if (path.size <= 2) return path
        val keep = BooleanArray(path.size).also { it[0] = true; it[path.lastIndex] = true }
        fun rec(a: Int, b: Int) {
            if (b <= a + 1) return
            val (ta, xa) = path[a]
            val (tb, xb) = path[b]
            var worst = -1
            var dist = tolerance
            for (i in a + 1 until b) {
                val (t, x) = path[i]
                val f = (t - ta).toFloat() / (tb - ta).coerceAtLeast(1)
                val d = abs(x - (xa + (xb - xa) * f))
                if (d > dist) { dist = d; worst = i }
            }
            if (worst >= 0) { keep[worst] = true; rec(a, worst); rec(worst, b) }
        }
        rec(0, path.lastIndex)
        return path.filterIndexed { i, _ -> keep[i] }
    }

    /** Camera centers (0..1) after the whole pipeline, clamped so the crop stays inside. */
    fun path(samples: List<SubjectSample>, sourceRatio: Float, outRatio: Float, o: Options = Options()):
        List<Pair<Long, Float>> {
        val visible = (outRatio / sourceRatio / o.scale).coerceIn(0f, 1f)
        val clamped = { x: Float -> x.coerceIn(visible / 2, 1 - visible / 2) }
        val filled = fillGaps(samples).map { it.first to clamped(it.second) }
        return deadZone(smooth(filled, o.smoothMs), o.deadZone * visible).map { it.first to clamped(it.second) }
    }

    /** Editable crop keyframes (TransformKeyframe x in ClipTransform NDC; y/rotation untouched). */
    fun keyframes(
        samples: List<SubjectSample>,
        sourceRatio: Float,
        outRatio: Float,
        base: TransformKeyframe = TransformKeyframe(0),
        o: Options = Options()
    ): List<TransformKeyframe> {
        val p = simplify(path(samples, sourceRatio, outRatio, o.copy(scale = base.scale)), o.tolerance)
        return p.map { (t, c) ->
            base.copy(sourceMs = t, x = FrameFormat.reframeX(sourceRatio, outRatio / base.scale, c) * base.scale)
        }
    }

    /** Sample times: every [stepMs] over [startMs, endMs], at most [max] samples. */
    fun sampleTimes(startMs: Long, endMs: Long, stepMs: Long = 200, max: Int = 600): List<Long> {
        val span = (endMs - startMs).coerceAtLeast(1)
        val step = maxOf(stepMs, span / max)
        return (startMs until endMs step step).toList().ifEmpty { listOf(startMs) }
    }
}
