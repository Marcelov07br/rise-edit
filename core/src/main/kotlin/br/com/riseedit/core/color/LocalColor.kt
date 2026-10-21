package br.com.riseedit.core.color

import br.com.riseedit.core.ColorAdjustMath
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow

/**
 * Qualifier (secondary correction): a soft key on the ORIGINAL pixel color (hue/sat/luma ranges
 * with softness) and adjustments applied only inside it. Ranges 0..1 except [hue] (degrees).
 * [active] is false until the user picks a color; a qualifier with all-zero adjustments is neutral.
 */
data class Qualifier(
    val active: Boolean = false,
    val hue: Float = 0f,
    /** Half-width of the hue range in degrees (0..180). */
    val hueWidth: Float = 25f,
    val hueSoft: Float = 0.5f,
    val satLow: Float = 0.15f,
    val satHigh: Float = 1f,
    val satSoft: Float = 0.3f,
    val lumLow: Float = 0.05f,
    val lumHigh: Float = 0.95f,
    val lumSoft: Float = 0.3f,
    /** -1..1 = ±60°. */
    val hueShift: Float = 0f,
    val saturation: Float = 0f,
    val lightness: Float = 0f,
    /** -2..2 EV. */
    val exposure: Float = 0f,
    val temperature: Float = 0f
) {
    val hasAdjust get() = hueShift != 0f || saturation != 0f || lightness != 0f || exposure != 0f || temperature != 0f
    /** Neutral = no effect on the image (mask view aside). */
    val isNeutral get() = !active || !hasAdjust

    companion object {
        /** Qualifier centered on a picked (original) color; ranges keep the user's widths. */
        fun pick(q: Qualifier, rgb: FloatArray): Qualifier {
            val hsv = ColorAdjustMath.rgbToHsv(rgb)
            val l = luma(rgb)
            val sw = 0.35f; val lw = 0.3f
            return q.copy(
                active = true, hue = hsv[0] * 360f,
                satLow = (hsv[1] - sw).coerceIn(0f, 1f), satHigh = (hsv[1] + sw).coerceIn(0f, 1f).let { if (it > 0.95f) 1f else it },
                lumLow = (l - lw).coerceIn(0f, 1f), lumHigh = (l + lw).coerceIn(0f, 1f)
            )
        }

        fun toMap(q: Qualifier): Map<String, Any> = mapOf(
            "on" to q.active, "h" to q.hue.toDouble(), "hw" to q.hueWidth.toDouble(), "hs" to q.hueSoft.toDouble(),
            "sl" to q.satLow.toDouble(), "sh" to q.satHigh.toDouble(), "ss" to q.satSoft.toDouble(),
            "ll" to q.lumLow.toDouble(), "lh" to q.lumHigh.toDouble(), "ls" to q.lumSoft.toDouble(),
            "dHue" to q.hueShift.toDouble(), "dSat" to q.saturation.toDouble(), "dLum" to q.lightness.toDouble(),
            "dExp" to q.exposure.toDouble(), "dTemp" to q.temperature.toDouble()
        )

        fun fromMap(m: Map<String, Any?>?): Qualifier {
            if (m == null) return Qualifier()
            val d = Qualifier()
            fun f(k: String, def: Float) = (m[k] as? Number)?.toFloat() ?: def
            return Qualifier(
                m["on"] as? Boolean ?: false, f("h", d.hue).mod(360f), f("hw", d.hueWidth).coerceIn(0f, 180f),
                f("hs", d.hueSoft).coerceIn(0f, 1f),
                f("sl", d.satLow).coerceIn(0f, 1f), f("sh", d.satHigh).coerceIn(0f, 1f), f("ss", d.satSoft).coerceIn(0f, 1f),
                f("ll", d.lumLow).coerceIn(0f, 1f), f("lh", d.lumHigh).coerceIn(0f, 1f), f("ls", d.lumSoft).coerceIn(0f, 1f),
                f("dHue", 0f).coerceIn(-1f, 1f), f("dSat", 0f).coerceIn(-1f, 1f), f("dLum", 0f).coerceIn(-1f, 1f),
                f("dExp", 0f).coerceIn(-2f, 2f), f("dTemp", 0f).coerceIn(-1f, 1f)
            )
        }

        private fun luma(c: FloatArray) = 0.2126f * c[0] + 0.7152f * c[1] + 0.0722f * c[2]
    }
}

/**
 * Vignette, applied last (after the LUT) in frame coordinates. [amount] -1..1 (negative darkens),
 * [size] 0..1 (inner radius), [softness] 0..1, [roundness] -1..1 (1 = circle, 0 = frame-shaped
 * ellipse, -1 = rounded rectangle), center [cx],[cy] 0..1 with y down (top = 0).
 */
data class Vignette(
    val amount: Float = 0f,
    val size: Float = 0.5f,
    val softness: Float = 0.5f,
    val roundness: Float = 0f,
    val cx: Float = 0.5f,
    val cy: Float = 0.5f
) {
    val isNeutral get() = amount == 0f

    companion object {
        fun toMap(v: Vignette): Map<String, Any> = mapOf(
            "amount" to v.amount.toDouble(), "size" to v.size.toDouble(), "soft" to v.softness.toDouble(),
            "round" to v.roundness.toDouble(), "cx" to v.cx.toDouble(), "cy" to v.cy.toDouble()
        )

        fun fromMap(m: Map<String, Any?>?): Vignette {
            if (m == null) return Vignette()
            val d = Vignette()
            fun f(k: String, def: Float) = (m[k] as? Number)?.toFloat() ?: def
            return Vignette(
                f("amount", 0f).coerceIn(-1f, 1f), f("size", d.size).coerceIn(0f, 1f), f("soft", d.softness).coerceIn(0f, 1f),
                f("round", d.roundness).coerceIn(-1f, 1f), f("cx", d.cx).coerceIn(0f, 1f), f("cy", d.cy).coerceIn(0f, 1f)
            )
        }
    }
}

/** CPU mirror of the qualifier/vignette GLSL ([GLSL]); change both together. */
object LocalColorMath {
    private fun smooth(e0: Float, e1: Float, x: Float): Float {
        if (e1 <= e0) return if (x >= e1) 1f else 0f
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** Key weight 0..1 of the original color [key]. */
    fun mask(q: Qualifier, key: FloatArray): Float {
        if (!q.active) return 0f
        val hsv = ColorAdjustMath.rgbToHsv(key)
        var d = abs(hsv[0] * 360f - q.hue) % 360f
        if (d > 180f) d = 360f - d
        val hs = q.hueSoft * 60f + 1f
        val wh = 1f - smooth(q.hueWidth, q.hueWidth + hs, d)
        val ss = q.satSoft * 0.3f + 0.001f
        val ws = smooth(q.satLow - ss, q.satLow, hsv[1]) * (1f - smooth(q.satHigh, q.satHigh + ss, hsv[1]))
        val ls = q.lumSoft * 0.3f + 0.001f
        val l = 0.2126f * key[0] + 0.7152f * key[1] + 0.0722f * key[2]
        val wl = smooth(q.lumLow - ls, q.lumLow, l) * (1f - smooth(q.lumHigh, q.lumHigh + ls, l))
        return wh * ws * wl
    }

    fun qualify(q: Qualifier, key: FloatArray, c: FloatArray): FloatArray {
        if (q.isNeutral) return c
        val w = mask(q, key)
        if (w <= 0f) return c
        val g = exp(q.exposure * ln(2f))
        var r = c[0] * g * (1f + q.temperature * 0.18f)
        var gg = c[1] * g
        var b = c[2] * g * (1f - q.temperature * 0.18f)
        r = r.coerceIn(0f, 1f); gg = gg.coerceIn(0f, 1f); b = b.coerceIn(0f, 1f)
        val hsv = ColorAdjustMath.rgbToHsv(floatArrayOf(r, gg, b))
        val nh = ((hsv[0] + q.hueShift / 6f) % 1f + 1f) % 1f
        val ns = (hsv[1] * (1f + q.saturation)).coerceIn(0f, 1f)
        val nv = (hsv[2] * (1f + q.lightness * 0.5f)).coerceIn(0f, 1f)
        val o = ColorAdjustMath.hsvToRgb(nh, ns, nv)
        return FloatArray(3) { c[it] + (o[it] - c[it]) * w }
    }

    /** Vignette factor at uv (0..1, y down) for a frame of [aspect] = w/h. */
    fun vignetteWeight(v: Vignette, u: Float, y: Float, aspect: Float): Float {
        val round = max(0f, v.roundness)
        val square = max(0f, -v.roundness)
        val dx = abs(u - v.cx) * 2f * (1f + (aspect - 1f) * round)
        val dy = abs(y - v.cy) * 2f
        val p = 2f + square * 6f
        val r = (dx.pow(p) + dy.pow(p)).pow(1f / p)
        val inner = v.size * 1.2f + 0.05f
        return smooth(inner, inner + v.softness * 1.2f + 0.01f, r)
    }

    fun vignette(v: Vignette, c: FloatArray, u: Float, y: Float, aspect: Float): FloatArray {
        if (v.isNeutral) return c
        val w = vignetteWeight(v, u, y, aspect) * abs(v.amount)
        return FloatArray(3) { if (v.amount < 0f) c[it] * (1f - w) else c[it] + (1f - c[it]) * w }
    }

    /** Shader functions; uniforms set by ColorPass from [uniforms]. */
    const val GLSL = """
uniform float uQOn;
uniform float uQMask;
uniform vec3 uQHue;
uniform vec3 uQSat;
uniform vec3 uQLum;
uniform vec3 uQAdj;
uniform vec2 uQAdj2;
uniform float uVOn;
uniform vec3 uVig;
uniform vec3 uVig2;
uniform float uAspect;
uniform float uFlipY;
float sstep(float e0, float e1, float x) { return e1 <= e0 ? step(e1, x) : smoothstep(e0, e1, x); }
float qualMask(vec3 k) {
  vec3 hsv = rgbToHsv(k);
  float d = mod(abs(hsv.x * 360.0 - uQHue.x), 360.0);
  if (d > 180.0) d = 360.0 - d;
  float wh = 1.0 - sstep(uQHue.y, uQHue.y + uQHue.z * 60.0 + 1.0, d);
  float ss = uQSat.z * 0.3 + 0.001;
  float ws = sstep(uQSat.x - ss, uQSat.x, hsv.y) * (1.0 - sstep(uQSat.y, uQSat.y + ss, hsv.y));
  float ls = uQLum.z * 0.3 + 0.001;
  float l = dot(k, vec3(0.2126, 0.7152, 0.0722));
  float wl = sstep(uQLum.x - ls, uQLum.x, l) * (1.0 - sstep(uQLum.y, uQLum.y + ls, l));
  return wh * ws * wl;
}
vec3 qualify(vec3 k, vec3 c) {
  float w = qualMask(k);
  vec3 x = clamp(c * exp2(uQAdj2.x) * vec3(1.0 + uQAdj2.y * 0.18, 1.0, 1.0 - uQAdj2.y * 0.18), 0.0, 1.0);
  vec3 hsv = rgbToHsv(x);
  float nh = mod(mod(hsv.x + uQAdj.x / 6.0, 1.0) + 1.0, 1.0);
  vec3 o = hsvToRgb(vec3(nh, clamp(hsv.y * (1.0 + uQAdj.y), 0.0, 1.0), clamp(hsv.z * (1.0 + uQAdj.z * 0.5), 0.0, 1.0)));
  return mix(c, o, w);
}
vec3 vignette(vec3 c, vec2 uv) {
  float y = uFlipY > 0.5 ? uv.y : 1.0 - uv.y;
  float rnd = max(0.0, uVig2.x);
  float sq = max(0.0, -uVig2.x);
  float dx = abs(uv.x - uVig2.y) * 2.0 * (1.0 + (uAspect - 1.0) * rnd);
  float dy = abs(y - uVig2.z) * 2.0;
  float p = 2.0 + sq * 6.0;
  float r = pow(pow(dx, p) + pow(dy, p), 1.0 / p);
  float inner = uVig.y * 1.2 + 0.05;
  float w = sstep(inner, inner + uVig.z * 1.2 + 0.01, r) * abs(uVig.x);
  return uVig.x < 0.0 ? c * (1.0 - w) : c + (vec3(1.0) - c) * w;
}
"""

    /** vec/float uniforms for [GLSL]. */
    fun uniforms(q: Qualifier, v: Vignette): Map<String, FloatArray> = mapOf(
        "uQHue" to floatArrayOf(q.hue, q.hueWidth, q.hueSoft),
        "uQSat" to floatArrayOf(q.satLow, q.satHigh, q.satSoft),
        "uQLum" to floatArrayOf(q.lumLow, q.lumHigh, q.lumSoft),
        "uQAdj" to floatArrayOf(q.hueShift, q.saturation, q.lightness),
        "uQAdj2" to floatArrayOf(q.exposure, q.temperature),
        "uVig" to floatArrayOf(v.amount, v.size, v.softness),
        "uVig2" to floatArrayOf(v.roundness, v.cx, v.cy)
    )

}
