package br.com.riseedit.core.color

import br.com.riseedit.core.ColorAdjust
import br.com.riseedit.core.ColorAdjustMath
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Per-channel statistics of a frame in YCbCr (BT.709): mean and standard deviation of Y, Cb, Cr,
 * plus Y percentiles (2/50/98) and mean chroma magnitude.
 */
data class FrameStats(
    val meanY: Float,
    val stdY: Float,
    val meanCb: Float,
    val meanCr: Float,
    val stdCb: Float,
    val stdCr: Float,
    val p02: Float,
    val p50: Float,
    val p98: Float,
    val chroma: Float
) {
    /** Feature vector the matcher drives to the reference (weights tuned for perception). */
    fun features() = floatArrayOf(
        meanY * 2f,
        stdY * 2.5f,
        meanCb * 4f,
        meanCr * 4f,
        chroma * 3f,
        (p98 - p02) * 1.5f
    )

    companion object {
        /** [rgb] = interleaved r,g,b triples in 0..1. */
        fun of(rgb: FloatArray): FrameStats {
            val n = rgb.size / 3
            require(n > 0)
            val ys = FloatArray(n)
            var sy = 0.0
            var sb = 0.0
            var sr = 0.0
            var syy = 0.0
            var sbb = 0.0
            var srr = 0.0
            var sc = 0.0
            for (i in 0 until n) {
                val r = rgb[i * 3]
                val g = rgb[i * 3 + 1]
                val b = rgb[i * 3 + 2]
                val y = 0.2126f * r + 0.7152f * g + 0.0722f * b
                val cb = (b - y) / 1.8556f
                val cr = (r - y) / 1.5748f
                ys[i] = y
                sy += y
                sb += cb
                sr += cr
                syy += y * y
                sbb += cb * cb
                srr += cr * cr
                sc += sqrt((cb * cb + cr * cr).toDouble())
            }
            fun sd(s: Double, ss: Double) =
                sqrt((ss / n - (s / n) * (s / n)).coerceAtLeast(0.0)).toFloat()
            ys.sort()
            fun pct(p: Float) = ys[((n - 1) * p).toInt().coerceIn(0, n - 1)]
            return FrameStats(
                (sy / n).toFloat(),
                sd(
                    sy,
                    syy
                ),
                (sb / n).toFloat(), (sr / n).toFloat(), sd(sb, sbb), sd(sr, srr),
                pct(0.02f), pct(0.5f), pct(0.98f), (sc / n).toFloat()
            )
        }
    }
}

/**
 * Shot matching: finds basic adjustments (exposure, contrast, temperature, tint, saturation) that
 * make the CURRENT clip's frame match the REFERENCE frame statistics. Solved with damped
 * Gauss–Newton on the CPU mirror ([ColorAdjustMath.apply]) so the result is exactly what the
 * shader renders. Result is a normal editable [ColorAdjust] (only basic fields change).
 */
object ColorMatch {
    /** Parameters solved, in order. */
    private val ranges = listOf(-2f..2f, -0.8f..0.8f, -1f..1f, -1f..1f, -1f..1f)

    private fun params(a: ColorAdjust) =
        floatArrayOf(a.exposure, a.contrast, a.warmth, a.tint, a.saturation)
    private fun with(a: ColorAdjust, p: FloatArray) =
        a.copy(exposure = p[0], contrast = p[1], warmth = p[2], tint = p[3], saturation = p[4])

    /** Applies [a] to every pixel of [raw] (CPU mirror) and measures. */
    fun statsAfter(a: ColorAdjust, raw: FloatArray): FrameStats {
        val out = FloatArray(raw.size)
        val px = FloatArray(3)
        for (i in 0 until raw.size / 3) {
            px[0] = raw[i * 3]
            px[1] = raw[i * 3 + 1]
            px[2] = raw[i * 3 + 2]
            val c = ColorAdjustMath.apply(a, px)
            out[i * 3] = c[0]
            out[i * 3 + 1] = c[1]
            out[i * 3 + 2] = c[2]
        }
        return FrameStats.of(out)
    }

    /** Distance between two stat sets (0 = identical). */
    fun distance(a: FrameStats, b: FrameStats): Float {
        val x = a.features()
        val y = b.features()
        var s = 0f
        for (i in x.indices) s += (x[i] - y[i]) * (x[i] - y[i])
        return sqrt(s)
    }

    /**
     * [raw] = current clip's ORIGINAL pixels (subsampled, ≤ ~4k px is plenty), [base] = its current
     * adjust (kept for curves/HSL/etc.), [reference] = stats of the reference as it is rendered.
     * Returns [base] with the solved basic fields.
     */
    fun solve(
        raw: FloatArray,
        base: ColorAdjust,
        reference: FrameStats,
        iterations: Int = 12
    ): ColorAdjust {
        var p = initialGuess(raw, base, reference)
        var cur = statsAfter(with(base, p), raw)
        var err = distance(cur, reference)
        var lambda = 0.05f
        val target = reference.features()
        repeat(iterations) {
            val f0 = cur.features()
            val r = FloatArray(f0.size) { target[it] - f0[it] }
            // numeric Jacobian
            val h = 0.02f
            val jac = Array(f0.size) { FloatArray(p.size) }
            for (j in p.indices) {
                val q = p.copyOf()
                q[j] = (q[j] + h)
                val fj = statsAfter(with(base, q), raw).features()
                for (i in f0.indices) jac[i][j] = (fj[i] - f0[i]) / h
            }
            var improved = false
            for (attempt in 0 until 4) {
                val step = solveDamped(jac, r, lambda) ?: break
                val q = FloatArray(p.size) { (p[it] + step[it]).coerceIn(ranges[it]) }
                val s = statsAfter(with(base, q), raw)
                val e = distance(s, reference)
                if (e < err) {
                    p = q
                    cur = s
                    err = e
                    lambda = (lambda * 0.5f).coerceAtLeast(1e-4f)
                    improved =
                        true
                    break
                }
                lambda *= 4f
            }
            if (!improved || err < 1e-3f) return with(base, round(p))
        }
        return with(base, round(p))
    }

    /** Blends from [base] towards [matched] by [intensity] (0..1), basic fields only. */
    fun blend(base: ColorAdjust, matched: ColorAdjust, intensity: Float): ColorAdjust {
        val k = intensity.coerceIn(0f, 1f)
        val a = params(base)
        val b = params(matched)
        return with(base, FloatArray(a.size) { a[it] + (b[it] - a[it]) * k })
    }

    private fun round(p: FloatArray) = FloatArray(p.size) { (Math.round(p[it] * 1000f) / 1000f) }

    private fun initialGuess(raw: FloatArray, base: ColorAdjust, ref: FrameStats): FloatArray {
        val p = params(base)
        val now = statsAfter(base, raw)
        if (now.meanY > 1e-3f && ref.meanY > 1e-3f) {
            p[0] = (p[0] + (ln(ref.meanY / now.meanY) / ln(2f)) * 0.7f).coerceIn(ranges[0])
        }
        if (now.stdY > 1e-3f) {
            val k = ref.stdY / now.stdY
            p[1] = (p[1] + ((k - 1f) / (k + 1f)) * 0.5f).coerceIn(ranges[1])
        }
        if (abs(ref.chroma) > 1e-4f &&
            now.chroma > 1e-4f
        ) {
            p[4] =
                (p[4] + (ref.chroma / now.chroma - 1f) * 0.5f).coerceIn(ranges[4])
        }
        return p
    }

    /** (JᵀJ + λ·diag) δ = Jᵀr, Gaussian elimination; null if singular. */
    // detekt: Gaussian elimination with partial pivoting.
    @Suppress("NestedBlockDepth")
    private fun solveDamped(j: Array<FloatArray>, r: FloatArray, lambda: Float): FloatArray? {
        val n = j[0].size
        val a = Array(n) { DoubleArray(n + 1) }
        for (x in 0 until n) {
            for (y in 0 until n) {
                var s = 0.0
                for (i in j.indices) s += j[i][x].toDouble() * j[i][y]
                a[x][y] = s
            }
            a[x][x] += lambda * (a[x][x] + 1e-3)
            var s = 0.0
            for (i in j.indices) s += j[i][x].toDouble() * r[i]
            a[x][n] = s
        }
        for (c in 0 until n) {
            var piv = c
            for (k in c + 1 until n) if (abs(a[k][c]) > abs(a[piv][c])) piv = k
            if (abs(a[piv][c]) < 1e-12) return null
            val t = a[c]
            a[c] = a[piv]
            a[piv] = t
            for (k in 0 until n) {
                if (k != c) {
                    val f = a[k][c] / a[c][c]
                    for (m in c..n) a[k][m] -= f * a[c][m]
                }
            }
        }
        return FloatArray(n) { (a[it][n] / a[it][it]).toFloat() }
    }
}
