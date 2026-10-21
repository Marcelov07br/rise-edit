package br.com.riseedit.core.color

import br.com.riseedit.core.ColorAdjust
import br.com.riseedit.core.ColorCurves
import br.com.riseedit.core.ColorWheels
import br.com.riseedit.core.CurvePoint
import br.com.riseedit.core.HslBands
import br.com.riseedit.core.Wheel
import kotlin.math.abs
import kotlin.math.roundToInt

/** Groups that animate independently (one diamond per Ajustar tab). */
enum class ColorKeyGroup(val id: String, val label: String) {
    BASIC("basic", "Básico"),
    CURVES("curves", "Curvas"),
    HSL("hsl", "HSL"),
    WHEELS("wheels", "Rodas"),
    LUT("lut", "LUT"),
    QUALIFIER("qualifier", "Seleção"),
    VIGNETTE("vignette", "Vinheta");

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id }
    }
}

/**
 * Snapshot of a group's values at [timeMs] (clip SOURCE time, survives trims/speed).
 * Only the fields of the owning group are read; [lutIntensity] is used by [ColorKeyGroup.LUT].
 */
data class ColorKey(val timeMs: Long, val adjust: ColorAdjust, val lutIntensity: Float = 1f)

/**
 * Per-clip color animation. Evaluation is pure (same in preview, paused overlay and export).
 * Between keys: smoothstep ease (C1, zero velocity at keys). Curves interpolate point-wise when
 * both keys have the same point count, otherwise they step (hold) until the next key.
 * LUT intensity is quantized to [LUT_STEP] so the baked LUT atlas is cached per step.
 */
data class ColorKeyframes(val tracks: Map<ColorKeyGroup, List<ColorKey>> = emptyMap()) {
    val isEmpty get() = tracks.values.all { it.isEmpty() }
    val isAnimated get() = tracks.values.any { it.size > 1 }

    fun keys(g: ColorKeyGroup) = tracks[g].orEmpty()

    /** All key times (any group), sorted, distinct. */
    fun times(): List<Long> = tracks.values.flatten().map { it.timeMs }.distinct().sorted()

    fun hasKeyAt(g: ColorKeyGroup, timeMs: Long, frameMs: Long = FRAME_MS) =
        keys(g).any { abs(it.timeMs - timeMs) < frameMs / 2 + 1 }

    /** Adds or replaces (same frame) the key of [g] at [timeMs] with the current values. */
    fun upsert(
        g: ColorKeyGroup,
        timeMs: Long,
        adjust: ColorAdjust,
        lutIntensity: Float,
        frameMs: Long = FRAME_MS
    ): ColorKeyframes {
        val kept = keys(g).filterNot { abs(it.timeMs - timeMs) < frameMs / 2 + 1 }
        return copy(
            tracks =
            tracks + (g to (kept + ColorKey(timeMs, adjust, lutIntensity)).sortedBy { it.timeMs })
        )
    }

    fun remove(g: ColorKeyGroup, timeMs: Long, frameMs: Long = FRAME_MS): ColorKeyframes {
        val kept = keys(g).filterNot { abs(it.timeMs - timeMs) < frameMs / 2 + 1 }
        return copy(tracks = if (kept.isEmpty()) tracks - g else tracks + (g to kept))
    }

    fun clear(g: ColorKeyGroup) = copy(tracks = tracks - g)

    /** Every key moved by [deltaMs] (negative keys clamp to 0). */
    fun shifted(deltaMs: Long) = if (deltaMs == 0L) {
        this
    } else {
        copy(
            tracks = tracks.mapValues { (_, k) ->
                k.map { it.copy(timeMs = (it.timeMs + deltaMs).coerceAtLeast(0)) }
            }
        )
    }

    /**
     * Values at [sourceMs]: groups without keys keep [base]/[grade]; a group with keys follows them
     * (a single key acts as a constant). Editing a slider while a group is keyed should go through
     * [upsert] at the playhead, otherwise the edit is overridden by the track.
     */
    fun at(sourceMs: Long, base: ColorAdjust, grade: ColorGrade): Pair<ColorAdjust, ColorGrade> {
        if (isEmpty) return base to grade
        var a = base
        var g = grade
        for ((group, keys) in tracks) {
            if (keys.isEmpty()) continue
            val v = sample(keys, sourceMs)
            when (group) {
                ColorKeyGroup.BASIC -> a = a.copy(
                    brightness = v.adjust.brightness, contrast = v.adjust.contrast,
                    saturation = v.adjust.saturation,
                    warmth = v.adjust.warmth,
                    exposure = v.adjust.exposure,
                    tint = v.adjust.tint,
                    shadows = v.adjust.shadows,
                    highlights = v.adjust.highlights,
                    vibrance = v.adjust.vibrance
                )
                ColorKeyGroup.CURVES -> a = a.copy(curves = v.adjust.curves)
                ColorKeyGroup.HSL -> a = a.copy(hsl = v.adjust.hsl)
                ColorKeyGroup.WHEELS -> a = a.copy(wheels = v.adjust.wheels)
                ColorKeyGroup.QUALIFIER -> a = a.copy(qualifier = v.adjust.qualifier)
                ColorKeyGroup.VIGNETTE -> a = a.copy(vignette = v.adjust.vignette)
                ColorKeyGroup.LUT -> g = g.copy(lutIntensity = quantize(v.lutIntensity))
            }
        }
        return a to g
    }

    companion object {
        const val VERSION = 1
        const val FRAME_MS = 33L
        const val LUT_STEP = 0.02f

        fun quantize(v: Float) = ((v / LUT_STEP).roundToInt() * LUT_STEP).coerceIn(0f, 1f)

        /** Smoothstep easing u²(3−2u). */
        fun ease(u: Float) = u * u * (3f - 2f * u)

        internal fun sample(keys: List<ColorKey>, t: Long): ColorKey {
            if (t <= keys.first().timeMs) return keys.first()
            if (t >= keys.last().timeMs) return keys.last()
            val i = keys.indexOfLast { it.timeMs <= t }.coerceAtMost(keys.size - 2)
            val a = keys[i]
            val b = keys[i + 1]
            val span = (b.timeMs - a.timeMs).toFloat()
            if (span <= 0f) return b
            val u = ease((t - a.timeMs) / span)
            return ColorKey(t, lerp(a.adjust, b.adjust, u), lerp(a.lutIntensity, b.lutIntensity, u))
        }

        fun lerp(a: Float, b: Float, u: Float) = a + (b - a) * u

        fun lerp(a: ColorAdjust, b: ColorAdjust, u: Float) = ColorAdjust(
            lerp(a.brightness, b.brightness, u), lerp(a.contrast, b.contrast, u),
            lerp(a.saturation, b.saturation, u), lerp(a.warmth, b.warmth, u),
            lerp(a.exposure, b.exposure, u), lerp(a.tint, b.tint, u),
            lerp(a.shadows, b.shadows, u), lerp(a.highlights, b.highlights, u),
            lerp(a.vibrance, b.vibrance, u),
            lerp(a.curves, b.curves, u),
            HslBands(a.hsl.values.indices.map { lerp(a.hsl.values[it], b.hsl.values[it], u) }),
            ColorWheels(
                lerp(a.wheels.lift, b.wheels.lift, u),
                lerp(a.wheels.gamma, b.wheels.gamma, u),
                lerp(a.wheels.gain, b.wheels.gain, u)
            ),
            lerp(a.qualifier, b.qualifier, u),
            Vignette(
                lerp(a.vignette.amount, b.vignette.amount, u),
                lerp(a.vignette.size, b.vignette.size, u),
                lerp(a.vignette.softness, b.vignette.softness, u),
                lerp(a.vignette.roundness, b.vignette.roundness, u),
                lerp(a.vignette.cx, b.vignette.cx, u),
                lerp(a.vignette.cy, b.vignette.cy, u)
            )
        )

        /** Hue center takes the short way around the circle; activity steps with the key. */
        private fun lerp(a: Qualifier, b: Qualifier, u: Float): Qualifier {
            var dh = (b.hue - a.hue) % 360f
            if (dh > 180f) dh -= 360f
            if (dh < -180f) dh += 360f
            return Qualifier(
                if (u < 1f) a.active else b.active, ((a.hue + dh * u) % 360f + 360f) % 360f,
                lerp(a.hueWidth, b.hueWidth, u), lerp(a.hueSoft, b.hueSoft, u),
                lerp(
                    a.satLow,
                    b.satLow,
                    u
                ),
                lerp(a.satHigh, b.satHigh, u), lerp(a.satSoft, b.satSoft, u),
                lerp(
                    a.lumLow,
                    b.lumLow,
                    u
                ),
                lerp(a.lumHigh, b.lumHigh, u), lerp(a.lumSoft, b.lumSoft, u),
                lerp(
                    a.hueShift,
                    b.hueShift,
                    u
                ),
                lerp(a.saturation, b.saturation, u), lerp(a.lightness, b.lightness, u),
                lerp(a.exposure, b.exposure, u), lerp(a.temperature, b.temperature, u)
            )
        }

        private fun lerp(a: Wheel, b: Wheel, u: Float) =
            Wheel(lerp(a.x, b.x, u), lerp(a.y, b.y, u), lerp(a.luma, b.luma, u))

        /** Point-wise when counts match; otherwise step (hold [a] until the next key). */
        fun lerpPoints(a: List<CurvePoint>, b: List<CurvePoint>, u: Float): List<CurvePoint> =
            if (a.size != b.size) {
                (if (u < 1f) a else b)
            } else {
                a.indices.map { CurvePoint(lerp(a[it].x, b[it].x, u), lerp(a[it].y, b[it].y, u)) }
            }

        private fun lerp(a: ColorCurves, b: ColorCurves, u: Float) = ColorCurves(
            lerpPoints(a.master, b.master, u),
            lerpPoints(a.red, b.red, u),
            lerpPoints(a.green, b.green, u),
            lerpPoints(a.blue, b.blue, u)
        )

        fun toMap(k: ColorKeyframes): Map<String, Any> = mapOf(
            "v" to VERSION,
            "tracks" to k.tracks.filterValues { it.isNotEmpty() }.entries.associate { (g, keys) ->
                g.id to
                    keys.map {
                        mapOf(
                            "t" to it.timeMs,
                            "color" to ColorAdjust.toMap(it.adjust),
                            "lut" to it.lutIntensity.toDouble()
                        )
                    }
            }
        )

        /**
         * v1 decoder. Missing → empty (projects ≤ 0.13.x). Unknown groups are dropped; keys with
         * missing color decode via [ColorAdjust.fromMap] (which itself migrates v2/v3 color).
         */
        @Suppress("UNCHECKED_CAST")
        fun fromMap(m: Map<String, Any?>?): ColorKeyframes {
            if (m == null) return ColorKeyframes()
            val tracks = m["tracks"] as? Map<String, Any?> ?: return ColorKeyframes()
            val out = LinkedHashMap<ColorKeyGroup, List<ColorKey>>()
            for ((id, raw) in tracks) {
                val g = ColorKeyGroup.of(id) ?: continue
                val keys = (raw as? Iterable<*>)?.mapNotNull { e ->
                    val o = e as? Map<String, Any?> ?: return@mapNotNull null
                    val t = (o["t"] as? Number)?.toLong() ?: return@mapNotNull null
                    val c =
                        (o["color"] as? Map<String, Any?>)?.let(ColorAdjust::fromMap)
                            ?: ColorAdjust.NEUTRAL
                    ColorKey(t, c, ((o["lut"] as? Number)?.toFloat() ?: 1f).coerceIn(0f, 1f))
                }?.sortedBy { it.timeMs }.orEmpty()
                if (keys.isNotEmpty()) out[g] = keys
            }
            return ColorKeyframes(out)
        }
    }
}

/** Slider edits on a clip that may have color keys (auto-key on keyed groups). */
object ColorKeyEdits {
    /** Fields of [g] equal in [a] and [b]? */
    fun same(g: ColorKeyGroup, a: ColorAdjust, b: ColorAdjust) = when (g) {
        ColorKeyGroup.BASIC -> a.basic == b.basic
        ColorKeyGroup.CURVES -> a.curves == b.curves
        ColorKeyGroup.HSL -> a.hsl == b.hsl
        ColorKeyGroup.WHEELS -> a.wheels == b.wheels
        ColorKeyGroup.QUALIFIER -> a.qualifier == b.qualifier
        ColorKeyGroup.VIGNETTE -> a.vignette == b.vignette
        ColorKeyGroup.LUT -> true
    }

    /**
     * The user changed the shown values to [edited] at [sourceMs]. Keyed groups that changed get a
     * key at the playhead; the base [br.com.riseedit.core.Clip.adjustments] takes [edited] (so
     * un-keyed groups behave as before and removing every key keeps the last look).
     */
    fun editAdjust(
        clip: br.com.riseedit.core.Clip,
        sourceMs: Long,
        edited: ColorAdjust
    ): br.com.riseedit.core.Clip {
        val (shown, grade) = clip.colorAt(sourceMs)
        var keys = clip.colorKeys
        for (g in ColorKeyGroup.entries) {
            if (g == ColorKeyGroup.LUT || keys.keys(g).isEmpty() || same(g, shown, edited)) continue
            keys = keys.upsert(g, sourceMs, edited, grade.lutIntensity)
        }
        return clip.copy(adjustments = edited, colorKeys = keys)
    }

    fun editGrade(
        clip: br.com.riseedit.core.Clip,
        sourceMs: Long,
        edited: ColorGrade
    ): br.com.riseedit.core.Clip {
        val (shown, grade) = clip.colorAt(sourceMs)
        var keys = clip.colorKeys
        if (keys.keys(ColorKeyGroup.LUT).isNotEmpty() &&
            edited.lutIntensity != grade.lutIntensity
        ) {
            keys = keys.upsert(ColorKeyGroup.LUT, sourceMs, shown, edited.lutIntensity)
        }
        return clip.copy(grade = edited, colorKeys = keys)
    }

    /** Diamond: adds a key with the current (evaluated) values, or removes the one at the playhead. */
    fun toggle(
        clip: br.com.riseedit.core.Clip,
        g: ColorKeyGroup,
        sourceMs: Long
    ): br.com.riseedit.core.Clip {
        val (shown, grade) = clip.colorAt(sourceMs)
        val keys = if (clip.colorKeys.hasKeyAt(g, sourceMs)) {
            clip.colorKeys.remove(g, sourceMs)
        } else {
            clip.colorKeys.upsert(g, sourceMs, shown, grade.lutIntensity)
        }
        // Removing the last key of a group bakes its current value into the base.
        return if (keys.keys(g).isEmpty() &&
            g != ColorKeyGroup.LUT
        ) {
            clip.copy(
                adjustments = shown.let { s ->
                    when (g) {
                        ColorKeyGroup.BASIC -> clip.adjustments.copy(
                            brightness = s.brightness,
                            contrast = s.contrast,
                            saturation = s.saturation,
                            warmth = s.warmth,
                            exposure = s.exposure,
                            tint = s.tint,
                            shadows = s.shadows,
                            highlights = s.highlights,
                            vibrance = s.vibrance
                        )
                        ColorKeyGroup.CURVES -> clip.adjustments.copy(curves = s.curves)
                        ColorKeyGroup.HSL -> clip.adjustments.copy(hsl = s.hsl)
                        ColorKeyGroup.QUALIFIER -> clip.adjustments.copy(qualifier = s.qualifier)
                        ColorKeyGroup.VIGNETTE -> clip.adjustments.copy(vignette = s.vignette)
                        else -> clip.adjustments.copy(wheels = s.wheels)
                    }
                },
                colorKeys = keys
            )
        } else if (keys.keys(g).isEmpty()) {
            clip.copy(
                grade = clip.grade.copy(lutIntensity = grade.lutIntensity),
                colorKeys = keys
            )
        } else {
            clip.copy(colorKeys = keys)
        }
    }
}
