package br.com.riseedit.core

import br.com.riseedit.core.color.ColorGrade
import br.com.riseedit.core.color.ColorKeyframes
import br.com.riseedit.core.editing.TrimEdge
import java.util.UUID

/**
 * Anything a future (0.17) mask can be attached to: a time-bounded item drawn over what is below
 * it. Masks will be a separate tool (shape/feather/invert/tracking) referenced by [maskId]; the
 * renderer will multiply the item's [AdjustmentLayer.opacity]-style mix by the mask. Not implemented yet:
 * [maskId] is always null and is neither drawn nor serialized.
 */
interface MaskTarget {
    val id: String
    val startMs: Long
    val durationMs: Long
    val maskId: String? get() = null
}

/**
 * "Camada de ajuste": a timeline item that applies a full color look (every group: basic, curves,
 * HSL, wheels, qualifier, vignette, LUT/log, color keys) to the COMPOSITED result of everything
 * below it (main video + video overlays) during [startMs, startMs + durationMs). Texts are drawn
 * above. Pipeline: per-clip (take) color → composition → adjustment layers bottom→top
 * ([AdjustmentStack]). [colorKeys] times are layer-local (0 = layer start).
 */
data class AdjustmentLayer(
    override val id: String = UUID.randomUUID().toString(),
    override val startMs: Long = 0,
    override val durationMs: Long = 3000,
    /** Stacking order among layers (0 = lowest); same track → later start is above. */
    val track: Int = 0,
    val name: String = "Ajuste",
    val adjust: ColorAdjust = ColorAdjust(),
    val grade: ColorGrade = ColorGrade(),
    val colorKeys: ColorKeyframes = ColorKeyframes(),
    /** Mix between what is below (0) and the adjusted result (1). */
    val opacity: Float = 1f
) : MaskTarget {
    init {
        require(startMs >= 0 && durationMs >= MIN_MS && opacity in 0f..1f && track >= 0)
    }

    val endMs get() = startMs + durationMs

    /** Active at project time [timeMs] (end exclusive). */
    fun covers(timeMs: Long) = timeMs >= startMs && timeMs < endMs

    /** Look at project time [timeMs] (keys applied). */
    fun colorAt(timeMs: Long) = colorKeys.at((timeMs - startMs).coerceIn(0, durationMs), adjust, grade)

    val isNeutral get() = adjust.isNeutral && grade.isIdentity && colorKeys.isEmpty

    /**
     * The layer seen as a clip (source time = layer-local time, 1:1) so every per-clip color tool
     * (ColorKeyEdits, copy/paste, presets, .cube) works unchanged; read back with [withColorOf].
     */
    fun asClip() = Clip(
        id = id, uri = URI, name = name, sourceDurationMs = durationMs,
        adjustments = adjust, grade = grade, colorKeys = colorKeys
    )

    fun withColorOf(c: Clip) = copy(adjust = c.adjustments, grade = c.grade, colorKeys = c.colorKeys)

    /** Trim by dragging an edge [deltaMs]; the opposite edge stays put. Keys keep project time. */
    fun trimmed(edge: TrimEdge, deltaMs: Long): AdjustmentLayer = when (edge) {
        TrimEdge.START -> {
            val start = (startMs + deltaMs).coerceIn(0, endMs - MIN_MS)
            val shift = start - startMs
            copy(startMs = start, durationMs = endMs - start, colorKeys = colorKeys.shifted(-shift))
        }
        TrimEdge.END -> copy(durationMs = (durationMs + deltaMs).coerceAtLeast(MIN_MS))
    }

    companion object {
        const val MIN_MS = 100L
        const val URI = "adjustment://layer"
        const val VERSION = 1

        /** New layer at the playhead: 3 s or until the project ends (at least [MIN_MS]). */
        fun at(project: Project, timeMs: Long, track: Int = 0): AdjustmentLayer {
            val total = project.durationMs.coerceAtLeast(MIN_MS)
            val start = timeMs.coerceIn(0, (total - MIN_MS).coerceAtLeast(0))
            val n = project.adjustmentLayers.size + 1
            return AdjustmentLayer(
                startMs = start, durationMs = minOf(3000, total - start).coerceAtLeast(MIN_MS),
                track = track, name = "Ajuste $n"
            )
        }

        fun toMap(l: AdjustmentLayer): Map<String, Any> = buildMap {
            put("v", VERSION); put("id", l.id); put("start", l.startMs); put("duration", l.durationMs)
            put("track", l.track); put("name", l.name); put("opacity", l.opacity.toDouble())
            put("color", ColorAdjust.toMap(l.adjust))
            if (!l.colorKeys.isEmpty) put("colorKeys", ColorKeyframes.toMap(l.colorKeys))
        }

        /** Grade is (de)serialized by the app (it owns the LUT JSON); see ProjectStore. */
        @Suppress("UNCHECKED_CAST")
        fun fromMap(m: Map<String, Any?>, grade: ColorGrade = ColorGrade()): AdjustmentLayer? {
            val id = m["id"] as? String ?: return null
            val start = (m["start"] as? Number)?.toLong()?.coerceAtLeast(0) ?: return null
            val duration = (m["duration"] as? Number)?.toLong()?.coerceAtLeast(MIN_MS) ?: return null
            return AdjustmentLayer(
                id, start, duration, ((m["track"] as? Number)?.toInt() ?: 0).coerceAtLeast(0),
                (m["name"] as? String) ?: "Ajuste",
                (m["color"] as? Map<String, Any?>)?.let(ColorAdjust::fromMap) ?: ColorAdjust(),
                grade,
                ColorKeyframes.fromMap(m["colorKeys"] as? Map<String, Any?>),
                ((m["opacity"] as? Number)?.toFloat() ?: 1f).coerceIn(0f, 1f)
            )
        }
    }
}

/** One adjustment pass to render after composition. */
data class AdjustmentPass(val layerId: String, val adjust: ColorAdjust, val grade: ColorGrade, val opacity: Float)

/** Order and time windows of adjustment layers (pure; preview, paused overlay and export). */
object AdjustmentStack {
    /** Bottom → top: track ascending, then start ascending (later start above), then list order. */
    fun ordered(layers: List<AdjustmentLayer>): List<AdjustmentLayer> =
        layers.withIndex().sortedWith(compareBy({ it.value.track }, { it.value.startMs }, { it.index })).map { it.value }

    /** Passes active at [timeMs], bottom → top; neutral or zero-opacity layers are skipped. */
    fun at(layers: List<AdjustmentLayer>, timeMs: Long): List<AdjustmentPass> =
        ordered(layers).filter { it.covers(timeMs) && it.opacity > 0f }.mapNotNull { l ->
            val (a, g) = l.colorAt(timeMs)
            if (a.isNeutral && g.isIdentity) null else AdjustmentPass(l.id, a, g, l.opacity)
        }

    /** CPU mirror for one pixel (tests/meters): [take] = color after per-clip color + composition. */
    fun applyCpu(passes: List<AdjustmentPass>, rgb: FloatArray, u: Float = 0.5f, v: Float = 0.5f, aspect: Float = 16f / 9f): FloatArray {
        var c = rgb
        for (p in passes) {
            var o = ColorAdjustMath.apply(p.adjust, c)
            o = br.com.riseedit.core.color.LocalColorMath.vignette(p.adjust.vignette, o, u, v, aspect)
            c = FloatArray(3) { c[it] + (o[it] - c[it]) * p.opacity }
        }
        return c
    }

    /** Ripple/move helpers: keep layers inside the project after edits. */
    fun clamp(layers: List<AdjustmentLayer>, projectMs: Long): List<AdjustmentLayer> = layers.mapNotNull { l ->
        if (projectMs <= 0) return@mapNotNull l
        if (l.startMs >= projectMs) null
        else if (l.endMs > projectMs) l.copy(durationMs = (projectMs - l.startMs).coerceAtLeast(AdjustmentLayer.MIN_MS))
        else l
    }

    fun move(l: AdjustmentLayer, startMs: Long) = l.copy(startMs = startMs.coerceAtLeast(0))

}
