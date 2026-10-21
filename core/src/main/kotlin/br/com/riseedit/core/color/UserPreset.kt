package br.com.riseedit.core.color

import br.com.riseedit.core.ColorAdjust

/**
 * A user-saved look ("Meus" in Filtros). [groups] says which parts it owns (ids of
 * ColorKeyGroup minus LUT: basic/curves/hsl/wheels); applying only touches those.
 */
data class UserPreset(
    val id: String,
    val name: String,
    val adjust: ColorAdjust,
    val groups: Set<ColorKeyGroup>
) {
    fun applyTo(a: ColorAdjust): ColorAdjust {
        var out = a
        if (ColorKeyGroup.BASIC in groups) {
            out = out.copy(
                brightness = adjust.brightness,
                contrast = adjust.contrast,
                saturation = adjust.saturation,
                warmth = adjust.warmth,
                exposure = adjust.exposure,
                tint = adjust.tint,
                shadows = adjust.shadows,
                highlights = adjust.highlights, vibrance = adjust.vibrance
            )
        }
        if (ColorKeyGroup.CURVES in groups) out = out.copy(curves = adjust.curves)
        if (ColorKeyGroup.HSL in groups) out = out.copy(hsl = adjust.hsl)
        if (ColorKeyGroup.WHEELS in groups) out = out.copy(wheels = adjust.wheels)
        if (ColorKeyGroup.QUALIFIER in groups) out = out.copy(qualifier = adjust.qualifier)
        if (ColorKeyGroup.VIGNETTE in groups) out = out.copy(vignette = adjust.vignette)
        return out
    }

    fun matches(a: ColorAdjust) = applyTo(a) == a

    /** Only the owned groups, others neutral (what is stored / shown in the thumbnail). */
    fun owned(): ColorAdjust = UserPreset(id, name, adjust, groups).applyTo(ColorAdjust.NEUTRAL)

    companion object {
        const val VERSION = 1
        val GROUPS =
            listOf(
                ColorKeyGroup.BASIC,
                ColorKeyGroup.CURVES,
                ColorKeyGroup.HSL,
                ColorKeyGroup.WHEELS,
                ColorKeyGroup.QUALIFIER,
                ColorKeyGroup.VIGNETTE
            )

        /** Presets saved before 0.15 without a group list owned only these. */
        val LEGACY_GROUPS = GROUPS.take(4)

        fun toMap(p: UserPreset): Map<String, Any> = mapOf(
            "v" to VERSION,
            "id" to p.id,
            "name" to p.name,
            "groups" to p.groups.map { it.id },
            "color" to ColorAdjust.toMap(p.owned())
        )

        @Suppress("UNCHECKED_CAST")
        fun fromMap(m: Map<String, Any?>): UserPreset? {
            val id = m["id"] as? String ?: return null
            val name = (m["name"] as? String)?.ifBlank { null } ?: return null
            val groups =
                (m["groups"] as? Iterable<*>)?.mapNotNull {
                    (it as? String)?.let(ColorKeyGroup::of)
                }
                    ?.filter { it in GROUPS }?.toSet()?.ifEmpty { null } ?: LEGACY_GROUPS.toSet()
            val color = (m["color"] as? Map<String, Any?>)?.let(ColorAdjust::fromMap) ?: return null
            return UserPreset(id, name, color, groups)
        }
    }
}
