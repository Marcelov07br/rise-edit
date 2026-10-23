package br.com.riseedit.core.editing

import br.com.riseedit.core.Clip
import br.com.riseedit.core.MAX_SPEED
import br.com.riseedit.core.MIN_SPEED
import br.com.riseedit.core.SpeedPoint
import kotlin.math.ln
import kotlin.math.exp
import kotlin.math.roundToLong

/**
 * 0.21 "Curvas de velocidade". The editable curve is a few control points (x = 0..1 over the
 * clip's trimmed source range, speed in ×) interpolated in log-speed (so 0.25×→4× is symmetric)
 * with a smoothstep ease. It is baked into the existing step [Clip.speedPoints] every [STEP_MS]
 * of source time, so preview (Rápida follows speedAt per tick; Fiel/export use the official
 * Media3 SpeedProvider through ClipSpeed) needs no new playback path.
 */
object SpeedCurve {
    const val STEP_MS = 100L

    data class Knot(val x: Float, val speed: Double)

    enum class Preset(val label: String, val knots: List<Knot>) {
        CONSTANT("Constante", listOf(Knot(0f, 1.0), Knot(1f, 1.0))),
        MONTAGE("Montagem", listOf(Knot(0f, 1.0), Knot(0.3f, 3.0), Knot(0.5f, 0.5), Knot(0.7f, 3.0), Knot(1f, 1.0))),
        HERO("Herói", listOf(Knot(0f, 1.0), Knot(0.35f, 1.0), Knot(0.5f, 0.3), Knot(0.65f, 1.0), Knot(1f, 1.0))),
        BULLET("Bullet", listOf(Knot(0f, 2.0), Knot(0.4f, 0.2), Knot(0.6f, 0.2), Knot(1f, 2.0))),
        RAMP_UP("Rampa ↑", listOf(Knot(0f, 0.5), Knot(1f, 3.0))),
        RAMP_DOWN("Rampa ↓", listOf(Knot(0f, 3.0), Knot(1f, 0.5)))
    }

    fun at(knots: List<Knot>, x: Float): Double {
        require(knots.isNotEmpty())
        val k = knots.sortedBy { it.x }
        if (x <= k.first().x) return k.first().speed
        if (x >= k.last().x) return k.last().speed
        val i = k.indexOfLast { it.x <= x }
        val a = k[i]
        val b = k[i + 1]
        val t = ((x - a.x) / (b.x - a.x)).coerceIn(0f, 1f)
        val e = t * t * (3 - 2 * t)
        return exp(ln(a.speed) + (ln(b.speed) - ln(a.speed)) * e)
    }

    /** Bakes [knots] into step speed points across the clip's trimmed source range. */
    fun bake(clip: Clip, knots: List<Knot>, stepMs: Long = STEP_MS): List<SpeedPoint> {
        val len = clip.endMs - clip.startMs
        val out = mutableListOf<SpeedPoint>()
        var s = clip.startMs
        while (s < clip.endMs) {
            val mid = ((s + minOf(s + stepMs, clip.endMs)) / 2 - clip.startMs).toFloat() / len
            val v = ((at(knots, mid) * 100).roundToLong() / 100.0).coerceIn(MIN_SPEED, MAX_SPEED)
            if (out.lastOrNull()?.speed != v) out += SpeedPoint(s, v)
            s += stepMs
        }
        return if (out.size == 1 && out[0].speed == 1.0) emptyList() else out
    }

    /** Reads the clip's current speed back into [n] editable knots (for editing a baked curve). */
    fun knotsOf(clip: Clip, n: Int = 5): List<Knot> = (0 until n).map { i ->
        val x = i / (n - 1f)
        val src = clip.startMs + ((clip.endMs - clip.startMs - 1) * x).roundToLong()
        Knot(x, clip.speedAt(src))
    }

    fun apply(clip: Clip, knots: List<Knot>): Clip = clip.copy(speedPoints = bake(clip, knots))
}
