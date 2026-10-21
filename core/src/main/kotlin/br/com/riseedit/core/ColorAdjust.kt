package br.com.riseedit.core

import br.com.riseedit.core.color.Curves
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** One editable curve point, both axes 0..1. */
data class CurvePoint(val x: Float, val y: Float)

/** Editable curves: master first, then per channel. Evaluated with a monotone spline. */
data class ColorCurves(
    val master: List<CurvePoint> = IDENTITY,
    val red: List<CurvePoint> = IDENTITY,
    val green: List<CurvePoint> = IDENTITY,
    val blue: List<CurvePoint> = IDENTITY
) {
    val isIdentity get() = listOf(master, red, green, blue).all { it == IDENTITY }

    fun channel(i: Int) = when (i) {
        0 -> master
        1 -> red
        2 -> green
        else -> blue
    }
    fun withChannel(i: Int, pts: List<CurvePoint>) = when (i) {
        0 -> copy(master = normalize(pts))
        1 -> copy(red = normalize(pts))
        2 -> copy(green = normalize(pts))
        else -> copy(blue = normalize(pts))
    }

    /** channel c (0=r,1=g,2=b) of x through master then that channel's curve. */
    fun eval(c: Int, x: Float): Float {
        val m = sample(master, x)
        return sample(channel(c + 1), m)
    }

    /** n entries × rgb, uploaded as a 1D texture (n×1). */
    fun table(n: Int = 256): FloatArray {
        val out = FloatArray(n * 3)
        for (i in 0 until n) for (c in 0..2) out[i * 3 + c] = eval(c, i / (n - 1f))
        return out
    }

    companion object {
        val IDENTITY = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f))
        const val MAX_POINTS = 12

        fun sample(pts: List<CurvePoint>, x: Float): Float = if (pts == IDENTITY) {
            x.coerceIn(0f, 1f)
        } else {
            Curves.monotone(pts.map { it.x.toDouble() to it.y.toDouble() }, x.toDouble())
                .toFloat().coerceIn(0f, 1f)
        }

        /** Sorted, clamped, strictly increasing x, endpoints kept, at most [MAX_POINTS]. */
        fun normalize(pts: List<CurvePoint>): List<CurvePoint> {
            val sorted = pts.map {
                CurvePoint(it.x.coerceIn(0f, 1f), it.y.coerceIn(0f, 1f))
            }.sortedBy { it.x }
            val out = mutableListOf<CurvePoint>()
            for (p in sorted) if (out.isEmpty() || p.x - out.last().x >= 0.01f) out += p
            if (out.size < 2) return IDENTITY
            return out.take(MAX_POINTS)
        }
    }
}

/** 8 hue bands × (hue shift, saturation, luminance), each -1..1. */
data class HslBands(val values: List<Float> = List(BANDS * 3) { 0f }) {
    init {
        require(values.size == BANDS * 3)
    }
    val isNeutral get() = values.all { it == 0f }
    fun hue(b: Int) = values[b * 3]
    fun sat(b: Int) = values[b * 3 + 1]
    fun lum(b: Int) = values[b * 3 + 2]
    fun with(b: Int, k: Int, v: Float) = HslBands(values.toMutableList().also { it[b * 3 + k] = v })

    companion object {
        const val BANDS = 8
        val NAMES =
            listOf("Vermelho", "Laranja", "Amarelo", "Verde", "Ciano", "Azul", "Roxo", "Magenta")

        /** Band centers in degrees. */
        val CENTERS = floatArrayOf(0f, 30f, 60f, 120f, 180f, 240f, 270f, 300f)
    }
}

/** A color wheel: (x, y) inside the unit disk is the tint, [luma] the master offset. -1..1. */
data class Wheel(val x: Float = 0f, val y: Float = 0f, val luma: Float = 0f) {
    /** RGB push for this wheel: hue from the angle, strength from the radius. */
    fun rgb(): FloatArray {
        val r = x
        val g = -0.5f * x + 0.866f * y
        val b = -0.5f * x - 0.866f * y
        return floatArrayOf(r + luma, g + luma, b + luma)
    }
    companion object {
        fun fromPolar(angleRad: Float, radius: Float, luma: Float) =
            Wheel(cos(angleRad) * radius, sin(angleRad) * radius, luma)
    }
}

data class ColorWheels(
    val lift: Wheel = Wheel(),
    val gamma: Wheel = Wheel(),
    val gain: Wheel = Wheel()
) {
    val isNeutral get() = this == ColorWheels()
}

/**
 * Single per-clip color adjustment model (v3). The first four fields keep their pre-0.12 meaning
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
    val vibrance: Float = 0f,
    val curves: ColorCurves = ColorCurves(),
    val hsl: HslBands = HslBands(),
    val wheels: ColorWheels = ColorWheels(),
    /** Secondary correction keyed on one color (0.15). */
    val qualifier: br.com.riseedit.core.color.Qualifier = br.com.riseedit.core.color.Qualifier(),
    /** Frame-space vignette, applied last (0.15). */
    val vignette: br.com.riseedit.core.color.Vignette = br.com.riseedit.core.color.Vignette(),
    /**
     * Masks (0.17) EVALUATED at the current time, attached by `Clip.colorAt` /
     * `AdjustmentLayer.colorAt` so every renderer gets them with the color. Never stored or
     * serialized here (the source of truth is the owner's MaskSet) and ignored by neutrality.
     */
    val masks: List<br.com.riseedit.core.mask.Mask> = emptyList()
) {
    val isNeutral get() = this == NEUTRAL ||
        copy(masks = emptyList()) == NEUTRAL ||
        (
            copy(qualifier = Qualifier0, vignette = Vignette0, masks = emptyList()) == NEUTRAL &&
                qualifier.isNeutral &&
                vignette.isNeutral
            )

    /** Global part (everything except qualifier/vignette) is neutral. */
    val isGlobalNeutral get() = copy(
        qualifier = Qualifier0,
        vignette = Vignette0,
        masks = emptyList()
    ) ==
        NEUTRAL

    /** Only the "basic" sliders (what filters and the v1/v2 format cover). */
    val basic get() = ColorAdjust(
        brightness, contrast, saturation, warmth, exposure, tint, shadows, highlights, vibrance
    )

    /** A filter only owns brightness/contrast/saturation/temperature; everything else is kept. */
    /** Replaces only the basic sliders, keeping curves/HSL/wheels/qualifier/vignette. */
    fun withBasic(b: ColorAdjust) = copy(
        brightness = b.brightness,
        contrast = b.contrast,
        saturation = b.saturation,
        warmth = b.warmth,
        exposure = b.exposure,
        tint = b.tint,
        shadows = b.shadows,
        highlights = b.highlights,
        vibrance = b.vibrance
    )

    fun withFilter(f: ColorAdjust) = copy(
        brightness = f.brightness,
        contrast = f.contrast,
        saturation = f.saturation,
        warmth = f.warmth
    )

    fun matchesFilter(f: ColorAdjust) = brightness == f.brightness &&
        contrast == f.contrast &&
        saturation == f.saturation &&
        warmth == f.warmth

    /** Scalar uniforms in shader order; see [ColorAdjustMath.GLSL]. */
    fun uniforms(): Map<String, Float> = mapOf(
        "uExposure" to exposure, "uBrightness" to brightness, "uTemp" to warmth, "uTint" to tint,
        "uContrast" to contrast, "uShadows" to shadows, "uHighlights" to highlights,
        "uSaturation" to saturation, "uVibrance" to vibrance
    )

    /** vec3 uniforms: HSL bands uHsl0..7, wheels uLift/uGamma/uGain. */
    fun vectorUniforms(): Map<String, FloatArray> {
        val m = LinkedHashMap<String, FloatArray>()
        for (b in 0 until HslBands.BANDS) {
            m["uHsl$b"] =
                floatArrayOf(hsl.hue(b), hsl.sat(b), hsl.lum(b))
        }
        m["uLift"] = wheels.lift.rgb()
        m["uGamma"] = wheels.gamma.rgb()
        m["uGain"] =
            wheels.gain.rgb()
        return m
    }

    companion object {
        const val VERSION = 4
        val NEUTRAL = ColorAdjust()
        private val Qualifier0 = br.com.riseedit.core.color.Qualifier()
        private val Vignette0 = br.com.riseedit.core.color.Vignette()

        /** v1 (<= 0.11.x): JSON array [brightness, contrast, saturation, warmth]. */
        fun fromLegacy(values: List<Double>?): ColorAdjust {
            if (values == null || values.size < 4) return NEUTRAL
            return ColorAdjust(
                values[0].toFloat(),
                values[1].toFloat(),
                values[2].toFloat(),
                values[3].toFloat()
            )
        }

        private fun pts(p: List<CurvePoint>) = p.flatMap {
            listOf(it.x.toDouble(), it.y.toDouble())
        }
        private fun wheel(w: Wheel) = listOf(w.x.toDouble(), w.y.toDouble(), w.luma.toDouble())

        fun toMap(a: ColorAdjust): Map<String, Any> = mapOf(
            "v" to VERSION, "brightness" to a.brightness.toDouble(),
            "contrast" to a.contrast.toDouble(),
            "saturation" to a.saturation.toDouble(), "temperature" to a.warmth.toDouble(),
            "exposure" to a.exposure.toDouble(), "tint" to a.tint.toDouble(),
            "shadows" to a.shadows.toDouble(), "highlights" to a.highlights.toDouble(),
            "vibrance" to a.vibrance.toDouble(),
            "curves" to mapOf(
                "master" to pts(a.curves.master),
                "red" to pts(a.curves.red),
                "green" to pts(a.curves.green),
                "blue" to pts(a.curves.blue)
            ),
            "hsl" to a.hsl.values.map { it.toDouble() },
            "wheels" to
                mapOf(
                    "lift" to wheel(a.wheels.lift),
                    "gamma" to wheel(a.wheels.gamma),
                    "gain" to wheel(a.wheels.gain)
                ),
            "qualifier" to br.com.riseedit.core.color.Qualifier.toMap(a.qualifier),
            "vignette" to br.com.riseedit.core.color.Vignette.toMap(a.vignette)
        )

        private fun nums(v: Any?): List<Float>? = when (v) {
            is List<*> -> v.map { (it as? Number)?.toFloat() ?: 0f }
            is Iterable<*> -> v.map { (it as? Number)?.toFloat() ?: 0f }
            else -> null
        }

        @Suppress("UNCHECKED_CAST")
        private fun obj(v: Any?): Map<String, Any?>? = v as? Map<String, Any?>

        /**
         * Decodes v2, v3 or v4. v3 has no qualifier/vignette → neutral (migration). v2 (0.12.x) has no curves/HSL/wheels → neutral (migration).
         * Unknown future keys are ignored, missing keys are neutral. Nested values may be
         * Lists/Maps (callers convert JSON first).
         */
        fun fromMap(m: Map<String, Any?>): ColorAdjust {
            fun f(k: String) = (m[k] as? Number)?.toFloat() ?: 0f
            val c = obj(m["curves"])
            fun curve(k: String) = nums(c?.get(k))?.chunked(2)?.filter { it.size == 2 }
                ?.map { CurvePoint(it[0], it[1]) }?.let(ColorCurves::normalize)
                ?: ColorCurves.IDENTITY
            val hsl = nums(m["hsl"])?.takeIf { it.size == HslBands.BANDS * 3 }
                ?.map { it.coerceIn(-1f, 1f) }?.let(::HslBands) ?: HslBands()
            val w = obj(m["wheels"])
            fun wheel(k: String) =
                nums(w?.get(k))?.takeIf { it.size == 3 }?.let { Wheel(it[0], it[1], it[2]) }
                    ?: Wheel()
            return ColorAdjust(
                f("brightness"), f("contrast"), f("saturation"), f("temperature"),
                f("exposure"), f("tint"), f("shadows"), f("highlights"), f("vibrance"),
                ColorCurves(curve("master"), curve("red"), curve("green"), curve("blue")),
                hsl,
                ColorWheels(wheel("lift"), wheel("gamma"), wheel("gain")),
                br.com.riseedit.core.color.Qualifier.fromMap(obj(m["qualifier"])),
                br.com.riseedit.core.color.Vignette.fromMap(obj(m["vignette"]))
            )
        }
    }
}

/**
 * Single source of the color formulas. [GLSL] runs in preview and export (ColorAdjustEffect);
 * [apply] is its line-by-line CPU mirror for meters/tests. Change both together.
 * Order: exposure → temp/tint → contrast → shadows/highlights → sat/vibrance → HSL bands →
 * lift/gamma/gain → curves → qualifier (keyed on the input color). Vignette: [br.com.riseedit.core.color.LocalColorMath]. The grade LUT runs before or after (ColorGrade.lutBeforeAdjust).
 */
object ColorAdjustMath {
    private const val PIVOT = 0.5f

    fun apply(a: ColorAdjust, rgb: FloatArray): FloatArray {
        var r = rgb[0]
        var g = rgb[1]
        var b = rgb[2]
        val gain = exp(a.exposure * ln(2f))
        r = r * gain + a.brightness
        g = g * gain + a.brightness
        b = b * gain + a.brightness
        r *= 1f + a.warmth * 0.18f
        b *= 1f - a.warmth * 0.18f
        g *= 1f - a.tint * 0.18f
        val k = (1f + a.contrast) / (1.0001f - a.contrast)
        r = (r - PIVOT) * k + PIVOT
        g = (g - PIVOT) * k + PIVOT
        b = (b - PIVOT) * k + PIVOT
        val l0 = luma(clamp(r), clamp(g), clamp(b))
        val ws = 1f - smooth(0f, 0.5f, l0)
        val wh = smooth(0.5f, 1f, l0)
        val lift = (a.shadows * ws + a.highlights * wh) * 0.25f
        r += lift
        g += lift
        b += lift
        val l = luma(r, g, b)
        val chroma = max(r, max(g, b)) - min(r, min(g, b))
        val s = (1f + a.saturation) * (1f + a.vibrance * (1f - clamp(chroma)))
        r = clamp(l + (r - l) * s)
        g = clamp(l + (g - l) * s)
        b = clamp(l + (b - l) * s)
        var c = floatArrayOf(r, g, b)
        if (!a.hsl.isNeutral) c = hsl(a.hsl, c)
        if (!a.wheels.isNeutral) c = wheels(a, c)
        if (!a.curves.isIdentity) c = FloatArray(3) { a.curves.eval(it, c[it]) }
        if (!a.qualifier.isNeutral) {
            c =
                br.com.riseedit.core.color.LocalColorMath.qualify(a.qualifier, rgb, c)
        }
        return FloatArray(3) { clamp(c[it]) }
    }

    /** Band weight: 1 at the center, 0 at ±45°, smooth. */
    fun bandWeight(hueDeg: Float, center: Float): Float {
        var d = abs(hueDeg - center) % 360f
        if (d > 180f) d = 360f - d
        return 1f - smooth(0f, 45f, d)
    }

    private fun hsl(bands: HslBands, c: FloatArray): FloatArray {
        val (h, s, v) = rgbToHsv(c)
        var dh = 0f
        var ds = 0f
        var dl = 0f
        for (i in 0 until HslBands.BANDS) {
            val w = bandWeight(h * 360f, HslBands.CENTERS[i])
            dh += bands.hue(i) * w
            ds += bands.sat(i) * w
            dl += bands.lum(i) * w
        }
        val amt = smooth(0f, 0.2f, s)
        val nh = ((h + dh * amt * (30f / 360f)) % 1f + 1f) % 1f
        val ns = clamp(s * (1f + ds * amt))
        val nv = clamp(v * (1f + dl * amt * 0.5f))
        return hsvToRgb(nh, ns, nv)
    }

    private fun wheels(a: ColorAdjust, c: FloatArray): FloatArray {
        val lf = a.wheels.lift.rgb()
        val gm = a.wheels.gamma.rgb()
        val gn = a.wheels.gain.rgb()
        return FloatArray(3) {
            val x = c[it] * (1f + gn[it] * 0.5f) + lf[it] * 0.25f * (1f - c[it])
            max(x, 0f).pow(1f / max(1f + gm[it] * 0.5f, 0.1f))
        }
    }

    fun rgbToHsv(c: FloatArray): FloatArray {
        val mx = max(c[0], max(c[1], c[2]))
        val mn = min(c[0], min(c[1], c[2]))
        val d = mx - mn
        val h = when {
            d < 1e-5f -> 0f
            mx == c[0] -> (((c[1] - c[2]) / d) / 6f + 1f) % 1f
            mx == c[1] -> ((c[2] - c[0]) / d + 2f) / 6f
            else -> ((c[0] - c[1]) / d + 4f) / 6f
        }
        return floatArrayOf(h, if (mx > 1e-5f) d / mx else 0f, mx)
    }

    fun hsvToRgb(h: Float, s: Float, v: Float): FloatArray = FloatArray(3) { i ->
        val n = floatArrayOf(5f, 3f, 1f)[i]
        val k = (n + h * 6f) % 6f
        v - v * s * max(0f, min(k, min(4f - k, 1f)))
    }

    private fun luma(r: Float, g: Float, b: Float) = 0.2126f * r + 0.7152f * g + 0.0722f * b
    private fun clamp(v: Float) = v.coerceIn(0f, 1f)
    private fun smooth(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** Fragment body; the effect adds the LUT (before/after) and the curves texture. */
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
uniform float uUseHsl;
uniform float uUseWheels;
uniform vec3 uHsl0; uniform vec3 uHsl1; uniform vec3 uHsl2; uniform vec3 uHsl3;
uniform vec3 uHsl4; uniform vec3 uHsl5; uniform vec3 uHsl6; uniform vec3 uHsl7;
uniform vec3 uLift;
uniform vec3 uGamma;
uniform vec3 uGain;
float lumaOf(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }
vec3 rgbToHsv(vec3 c) {
  float mx = max(c.r, max(c.g, c.b)); float mn = min(c.r, min(c.g, c.b)); float d = mx - mn;
  float h = 0.0;
  if (d >= 1e-5) {
    if (mx == c.r) h = mod((c.g - c.b) / d / 6.0 + 1.0, 1.0);
    else if (mx == c.g) h = ((c.b - c.r) / d + 2.0) / 6.0;
    else h = ((c.r - c.g) / d + 4.0) / 6.0;
  }
  return vec3(h, mx > 1e-5 ? d / mx : 0.0, mx);
}
vec3 hsvToRgb(vec3 c) {
  vec3 k = mod(vec3(5.0, 3.0, 1.0) + c.x * 6.0, 6.0);
  return c.z - c.z * c.y * max(vec3(0.0), min(k, min(4.0 - k, vec3(1.0))));
}
float bandW(float h, float center) {
  float d = mod(abs(h - center), 360.0);
  if (d > 180.0) d = 360.0 - d;
  return 1.0 - smoothstep(0.0, 45.0, d);
}
vec3 hslBands(vec3 c) {
  vec3 hsv = rgbToHsv(c);
  float h = hsv.x * 360.0;
  vec3 d = uHsl0 * bandW(h, 0.0) + uHsl1 * bandW(h, 30.0) + uHsl2 * bandW(h, 60.0)
    + uHsl3 * bandW(h, 120.0) + uHsl4 * bandW(h, 180.0) + uHsl5 * bandW(h, 240.0)
    + uHsl6 * bandW(h, 270.0) + uHsl7 * bandW(h, 300.0);
  float amt = smoothstep(0.0, 0.2, hsv.y);
  float nh = mod(mod(hsv.x + d.x * amt * (30.0 / 360.0), 1.0) + 1.0, 1.0);
  float ns = clamp(hsv.y * (1.0 + d.y * amt), 0.0, 1.0);
  float nv = clamp(hsv.z * (1.0 + d.z * amt * 0.5), 0.0, 1.0);
  return hsvToRgb(vec3(nh, ns, nv));
}
vec3 liftGammaGain(vec3 c) {
  vec3 x = c * (1.0 + uGain * 0.5) + uLift * 0.25 * (1.0 - c);
  return pow(max(x, vec3(0.0)), 1.0 / max(1.0 + uGamma * 0.5, vec3(0.1)));
}
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
  c = clamp(vec3(l) + (c - vec3(l)) * s, 0.0, 1.0);
  if (uUseHsl > 0.5) c = hslBands(c);
  if (uUseWheels > 0.5) c = liftGammaGain(c);
  return clamp(c, 0.0, 1.0);
}
"""
}
