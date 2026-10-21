package br.com.riseedit.core.color

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** Camera log encodings decoded with their published formulas, then tone-mapped to Rec.709. */
enum class LogProfile(val label: String) {
    NONE("Nenhum"),
    SLOG3("S-Log3"),
    VLOG("V-Log"),
    APPLE_LOG("Apple Log"),
    GENERIC("Log genérico");

    /** Encoded 0..1 → scene-linear (0.18 = middle grey). */
    @Suppress("MagicNumber")
    fun toLinear(v: Double): Double = when (this) {
        NONE -> v
        SLOG3 -> {
            val code = v * 1023.0
            if (code >= 171.2102946929) {
                10.0.pow((code - 420.0) / 261.5) * (0.18 + 0.01) - 0.01
            } else {
                (code - 95.0) * 0.01125000 / (171.2102946929 - 95.0)
            }
        }
        VLOG -> if (v < 0.181) (v - 0.125) / 5.6 else 10.0.pow((v - 0.598206) / 0.241514) - 0.00873
        APPLE_LOG -> {
            val r0 = -0.05641088
            val rt = 0.01
            val c = 47.28711236
            val b = 0.00964052
            val g = 0.08550479
            val beta = 0.69336945
            val pt = c * (rt - r0) * (rt - r0)
            when {
                v >= pt -> 2.0.pow((v - beta) / g) - b
                v >= 0 -> sqrt(v / c) + r0
                else -> r0
            }
        }
        // Middle grey at ~0.40, ~14 stops: a neutral starting point when the curve is unknown.
        GENERIC -> 0.18 * 2.0.pow((v - 0.40) * 14.0)
    }
}

/** Fixed tone curves (no sliders): control points interpolated monotonically. */
enum class ToneCurve(val label: String, val points: List<Pair<Double, Double>>) {
    LINEAR("Linear", listOf(0.0 to 0.0, 1.0 to 1.0)),
    S_SOFT("S suave", listOf(0.0 to 0.0, 0.25 to 0.21, 0.5 to 0.5, 0.75 to 0.79, 1.0 to 1.0)),
    S_STRONG("S forte", listOf(0.0 to 0.0, 0.25 to 0.16, 0.5 to 0.5, 0.75 to 0.85, 1.0 to 1.0)),
    FADE("Desbotado", listOf(0.0 to 0.08, 0.5 to 0.52, 1.0 to 0.95)),
    LIFT("Sombras +", listOf(0.0 to 0.0, 0.2 to 0.28, 0.6 to 0.65, 1.0 to 1.0)),
    HIGHLIGHTS("Altas −", listOf(0.0 to 0.0, 0.5 to 0.5, 0.8 to 0.74, 1.0 to 0.9));

    fun apply(x: Double): Double = Curves.monotone(points, x)
}

data class ColorGrade(
    val log: LogProfile = LogProfile.NONE,
    val curve: ToneCurve = ToneCurve.LINEAR,
    /** content:// .cube imported by the user; parsed at render time. */
    val lutUri: String? = null,
    val lutName: String? = null,
    val lutIntensity: Float = 1f,
    /** true: grade (log/LUT/curve) runs BEFORE the adjust sliders; default after (0.12 order). */
    val lutBeforeAdjust: Boolean = false
) {
    init {
        require(lutIntensity in 0f..1f)
    }

    val isIdentity get() = log == LogProfile.NONE && curve == ToneCurve.LINEAR && lutUri == null

    /** Pixel pipeline shared by export, preview and meters: log → LUT → curve. */
    fun apply(rgb: DoubleArray, lut: CubeLut?): DoubleArray {
        var out = if (log ==
            LogProfile.NONE
        ) {
            rgb.copyOf()
        } else {
            DoubleArray(3) { logToRec709(rgb[it]) }
        }
        if (lut != null && lutIntensity > 0f) {
            val mapped = lut.sample(out[0], out[1], out[2])
            out = DoubleArray(3) { out[it] + (mapped[it] - out[it]) * lutIntensity }
        }
        if (curve != ToneCurve.LINEAR) out = DoubleArray(3) { curve.apply(out[it]) }
        return DoubleArray(3) { out[it].coerceIn(0.0, 1.0) }
    }

    /** RGB cube (r fastest) of [size]³ entries, for the GPU LUT. */
    fun cube(size: Int, lut: CubeLut?): FloatArray {
        val data = FloatArray(size * size * size * 3)
        val step = 1.0 / (size - 1)
        var i = 0
        for (b in 0 until size) {
            for (g in 0 until size) {
                for (r in 0 until size) {
                    val o = apply(doubleArrayOf(r * step, g * step, b * step), lut)
                    data[i++] = o[0].toFloat()
                    data[i++] = o[1].toFloat()
                    data[i++] = o[2].toFloat()
                }
            }
        }
        return data
    }

    private fun logToRec709(v: Double): Double {
        val linear = max(0.0, log.toLinear(v))
        val white = 8.0
        val mapped = linear * (1 + linear / (white * white)) / (1 + linear)
        return rec709(min(1.0, mapped))
    }

    private fun rec709(l: Double) = if (l < 0.018) 4.5 * l else 1.099 * l.pow(0.45) - 0.099
}

object Curves {
    /** Monotone cubic (Fritsch–Carlson) through sorted points, clamped outside. */
    fun monotone(points: List<Pair<Double, Double>>, x: Double): Double {
        if (x <= points.first().first) return points.first().second
        if (x >= points.last().first) return points.last().second
        val n = points.size
        val d = DoubleArray(n - 1) {
            (points[it + 1].second - points[it].second) /
                (points[it + 1].first - points[it].first)
        }
        val m = DoubleArray(n) { i ->
            when (i) {
                0 -> d[0]
                n - 1 -> d[n - 2]
                else -> if (d[i - 1] * d[i] <= 0) 0.0 else (d[i - 1] + d[i]) / 2
            }
        }
        for (i in 0 until n - 1) {
            if (d[i] == 0.0) {
                m[i] = 0.0
                m[i + 1] = 0.0
            } else {
                val a = m[i] / d[i]
                val b = m[i + 1] / d[i]
                val s = a * a + b * b
                if (s > 9) {
                    val t = 3 / sqrt(s)
                    m[i] = t * a * d[i]
                    m[i + 1] = t * b * d[i]
                }
            }
        }
        val k = points.indices.last { points[it].first <= x }.coerceAtMost(n - 2)
        val (x0, y0) = points[k]
        val (x1, y1) = points[k + 1]
        val h = x1 - x0
        val t = (x - x0) / h
        val t2 = t * t
        val t3 = t2 * t
        return (2 * t3 - 3 * t2 + 1) * y0 + (t3 - 2 * t2 + t) * h * m[k] +
            (-2 * t3 + 3 * t2) * y1 + (t3 - t2) * h * m[k + 1]
    }
}
