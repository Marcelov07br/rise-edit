package br.com.riseedit.core

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Single per-clip color adjustment model (v2). The first four fields keep their pre-0.12 meaning
 * and positional order so old projects and presets open the same.
 * Ranges: exposure -2..2 EV; others roughly -1..1.
 */
data class ColorAdjust(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    /** Temperature (kept as `warmth` for source compatibility). */
    val warmth: Float = 0f,
    val exposure: Float = 0f,
    val tint: Float = 0f,
    val shadows: Float = 0f,
    val highlights: Float = 0f,
    val vibrance: Float = 0f
) {
    val isNeutral get() = this == NEUTRAL

    /** Uniform values in shader order; see [ColorAdjustMath.GLSL]. */
    fun uniforms(): Map<String, Float> = mapOf(
        "uExposure" to exposure, "uBrightness" to brightness, "uTemp" to warmth, "uTint" to tint,
        "uContrast" to contrast, "uShadows" to shadows, "uHighlights" to highlights,
        "uSaturation" to saturation, "uVibrance" to vibrance
    )

    companion object {
        const val VERSION = 2
        val NEUTRAL = ColorAdjust()

        /** v1 (<= 0.11.x): JSON array [brightness, contrast, saturation, warmth]. */
        fun fromLegacy(values: List<Double>?): ColorAdjust {
            if (values == null || values.size < 4) return NEUTRAL
            return ColorAdjust(
                values[0].toFloat(), values[1].toFloat(), values[2].toFloat(), values[3].toFloat()
            )
        }

        fun toMap(a: ColorAdjust): Map<String, Any> = mapOf(
            "v" to VERSION, "brightness" to a.brightness.toDouble(), "contrast" to a.contrast.toDouble(),
            "saturation" to a.saturation.toDouble(), "temperature" to a.warmth.toDouble(),
            "exposure" to a.exposure.toDouble(), "tint" to a.tint.toDouble(),
            "shadows" to a.shadows.toDouble(), "highlights" to a.highlights.toDouble(),
            "vibrance" to a.vibrance.toDouble()
        )

        /** Decodes a versioned object; unknown future keys are ignored, missing keys are neutral. */
        fun fromMap(m: Map<String, Any?>): ColorAdjust {
            fun f(k: String) = (m[k] as? Number)?.toFloat() ?: 0f
            return ColorAdjust(
                f("brightness"), f("contrast"), f("saturation"), f("temperature"),
                f("exposure"), f("tint"), f("shadows"), f("highlights"), f("vibrance")
            )
        }
    }
}

/**
 * Single source of the color formulas. [GLSL] runs in preview and export (ColorAdjustEffect);
 * [apply] is its line-by-line CPU mirror for meters/tests. Change both together.
 */
object ColorAdjustMath {
    private const val PIVOT = 0.5f

    fun apply(a: ColorAdjust, rgb: FloatArray): FloatArray {
        var r = rgb[0]; var g = rgb[1]; var b = rgb[2]
        // 1. exposure (EV) + brightness offset
        val gain = exp(a.exposure * ln(2f))
        r = r * gain + a.brightness; g = g * gain + a.brightness; b = b * gain + a.brightness
        // 2. temperature / tint
        r *= 1f + a.warmth * 0.18f; b *= 1f - a.warmth * 0.18f; g *= 1f - a.tint * 0.18f
        // 3. contrast around pivot
        val k = (1f + a.contrast) / (1.0001f - a.contrast)
        r = (r - PIVOT) * k + PIVOT; g = (g - PIVOT) * k + PIVOT; b = (b - PIVOT) * k + PIVOT
        // 4. shadows / highlights
        val l0 = luma(clamp(r), clamp(g), clamp(b))
        val ws = 1f - smooth(0f, 0.5f, l0)
        val wh = smooth(0.5f, 1f, l0)
        val lift = (a.shadows * ws + a.highlights * wh) * 0.25f
        r += lift; g += lift; b += lift
        // 5. saturation / vibrance
        val l = luma(r, g, b)
        val chroma = max(r, max(g, b)) - min(r, min(g, b))
        val s = (1f + a.saturation) * (1f + a.vibrance * (1f - clamp(chroma)))
        r = l + (r - l) * s; g = l + (g - l) * s; b = l + (b - l) * s
        return floatArrayOf(clamp(r), clamp(g), clamp(b))
    }

    private fun luma(r: Float, g: Float, b: Float) = 0.2126f * r + 0.7152f * g + 0.0722f * b
    private fun clamp(v: Float) = v.coerceIn(0f, 1f)
    private fun smooth(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** Fragment body; LUT step (6) is appended by the effect when a grade is active. */
    const val GLSL = """
uniform float uExposure;
uniform float uBrightness;
uniform float uTemp;
uniform float uTint;
uniform float uContrast;
uniform float uShadows;
uniform float uHighlights;
uniform float uSaturation;
uniform float uVibrance;
float lumaOf(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }
vec3 colorAdjust(vec3 c) {
  c = c * exp2(uExposure) + uBrightness;
  c *= vec3(1.0 + uTemp * 0.18, 1.0 - uTint * 0.18, 1.0 - uTemp * 0.18);
  float k = (1.0 + uContrast) / (1.0001 - uContrast);
  c = (c - 0.5) * k + 0.5;
  float l0 = lumaOf(clamp(c, 0.0, 1.0));
  float ws = 1.0 - smoothstep(0.0, 0.5, l0);
  float wh = smoothstep(0.5, 1.0, l0);
  c += (uShadows * ws + uHighlights * wh) * 0.25;
  float l = lumaOf(c);
  float chroma = max(c.r, max(c.g, c.b)) - min(c.r, min(c.g, c.b));
  float s = (1.0 + uSaturation) * (1.0 + uVibrance * (1.0 - clamp(chroma, 0.0, 1.0)));
  c = vec3(l) + (c - vec3(l)) * s;
  return clamp(c, 0.0, 1.0);
}
"""
}
