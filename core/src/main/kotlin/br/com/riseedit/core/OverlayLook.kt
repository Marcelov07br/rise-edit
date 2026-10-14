package br.com.riseedit.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * How an overlay merges with the video below. Media3 composites with plain alpha, so each mode
 * derives the overlay's alpha from its own pixels (luma or chroma key) in a shader.
 */
enum class OverlayBlend(val label: String) {
    NORMAL("Normal"),
    LIGHTEN("Clarear"),
    DARKEN("Escurecer"),
    CHROMA_GREEN("Chroma verde")
}

enum class OverlayMask(val label: String) {
    NONE("Nenhuma"),
    CIRCLE("Círculo"),
    ROUNDED("Arredondada"),
    LINEAR("Linear")
}

/** CPU reference of the overlay shader; the GLSL in the app mirrors these formulas. */
object OverlayLook {
    /** Mask coverage at texture coordinate (u, v) in 0..1, [feather] in 0..1. */
    fun mask(
        shape: OverlayMask,
        u: Float,
        v: Float,
        feather: Float,
        aspect: Float,
        invert: Boolean = false
    ): Float {
        val f = max(0.001f, feather * 0.5f)
        val coverage =
            when (shape) {
                OverlayMask.NONE -> 1f
                OverlayMask.CIRCLE -> {
                    val dx = (u - 0.5f) * max(1f, aspect)
                    val dy = (v - 0.5f) * max(1f, 1 / aspect)
                    val r = 0.5f * min(max(1f, aspect), max(1f, 1 / aspect))
                    smooth(r, r - f, sqrt(dx * dx + dy * dy))
                }
                OverlayMask.ROUNDED -> {
                    val radius = 0.15f
                    val qx = max(abs(u - 0.5f) - (0.5f - radius), 0f)
                    val qy = max(abs(v - 0.5f) - (0.5f - radius), 0f)
                    smooth(radius, radius - f, sqrt(qx * qx + qy * qy))
                }
                OverlayMask.LINEAR -> smooth(0.5f + f, 0.5f - f, 1 - v)
            }
        return if (invert && shape != OverlayMask.NONE) 1 - coverage else coverage
    }

    /** Alpha factor from the overlay's own color for [blend]. */
    fun blendAlpha(blend: OverlayBlend, r: Float, g: Float, b: Float): Float {
        val luma = 0.2126f * r + 0.7152f * g + 0.0722f * b
        return when (blend) {
            OverlayBlend.NORMAL -> 1f
            OverlayBlend.LIGHTEN -> smooth(0.05f, 0.6f, luma)
            OverlayBlend.DARKEN -> 1 - smooth(0.4f, 0.95f, luma)
            OverlayBlend.CHROMA_GREEN -> 1 - smooth(0.08f, 0.25f, g - max(r, b))
        }
    }

    /** GLSL smoothstep(edge0, edge1, x). */
    internal fun smooth(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }
}
