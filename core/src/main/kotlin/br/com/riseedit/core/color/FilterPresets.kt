package br.com.riseedit.core.color

import br.com.riseedit.core.ColorAdjust
import kotlin.math.abs

/**
 * A look applied by the Filtros panel. A filter only owns brightness/contrast/saturation/temperature
 * (see [ColorAdjust.withFilter]); every other adjustment of the clip is kept.
 */
data class FilterPreset(
    val id: String,
    val name: String,
    val adjust: ColorAdjust,
    val category: String? = null
) {
    val isNone get() = adjust.matchesFilter(ColorAdjust())

    /** The filter's 4 fields scaled by [intensity] (0..1). */
    fun at(intensity: Float): ColorAdjust {
        val k = intensity.coerceIn(0f, 1f)
        return ColorAdjust(
            adjust.brightness * k,
            adjust.contrast * k,
            adjust.saturation * k,
            adjust.warmth * k
        )
    }
}

/** Selected filter + intensity recognised from a clip's adjustments. */
data class FilterMatch(val preset: FilterPreset, val intensity: Float)

/** Extensible registry: add a [FilterPreset] to [builtIn] (or [register] at runtime). "Nenhum" stays first. */
object FilterRegistry {
    val NONE = FilterPreset("none", "Nenhum", ColorAdjust())

    private val builtIn = listOf(
        FilterPreset("cinema", "Cinema", ColorAdjust(-0.03f, 0.15f, -0.15f, 0.25f), "Filme"),
        FilterPreset("teal", "Noir quente", ColorAdjust(-0.05f, 0.3f, -0.45f, 0.2f), "Filme"),
        FilterPreset("vivid", "Vivo", ColorAdjust(0.03f, 0.1f, 0.25f, 0.1f), "Cor"),
        FilterPreset("pop", "Pop", ColorAdjust(0.04f, 0.2f, 0.45f, 0f), "Cor"),
        FilterPreset("warm", "Quente", ColorAdjust(0.02f, 0.05f, 0.1f, 0.5f), "Cor"),
        FilterPreset("cold", "Frio", ColorAdjust(0f, 0.05f, -0.05f, -0.45f), "Cor"),
        FilterPreset("fade", "Desbotado", ColorAdjust(0.06f, -0.25f, -0.2f, 0.05f), "Retrô"),
        FilterPreset("bw", "P&B", ColorAdjust(saturation = -1f), "P&B"),
        FilterPreset("bwhard", "P&B forte", ColorAdjust(0f, 0.4f, -1f, 0f), "P&B")
    )
    private val extra = mutableListOf<FilterPreset>()

    val all: List<FilterPreset> get() = listOf(NONE) + builtIn + extra
    val categories: List<String> get() = all.mapNotNull { it.category }.distinct()

    fun register(preset: FilterPreset) {
        require(all.none { it.id == preset.id }) { "duplicate filter ${preset.id}" }
        extra += preset
    }

    fun byId(id: String) = all.firstOrNull { it.id == id }

    /** Which filter (and at what intensity) the clip's 4 basic fields correspond to, or null (custom). */
    fun match(a: ColorAdjust): FilterMatch? {
        if (a.matchesFilter(ColorAdjust())) return FilterMatch(NONE, 1f)
        val v = floatArrayOf(a.brightness, a.contrast, a.saturation, a.warmth)
        for (p in all) {
            if (p.isNone) continue
            val f =
                floatArrayOf(
                    p.adjust.brightness,
                    p.adjust.contrast,
                    p.adjust.saturation,
                    p.adjust.warmth
                )
            val i = f.indices.maxByOrNull { abs(f[it]) } ?: continue
            if (f[i] == 0f) continue
            val k = v[i] / f[i]
            if (k <= 0f || k > 1.0001f) continue
            if (f.indices.all {
                    abs(v[it] - f[it] * k) < 1e-3f
                }
            ) {
                return FilterMatch(p, k.coerceIn(0f, 1f))
            }
        }
        return null
    }
}
