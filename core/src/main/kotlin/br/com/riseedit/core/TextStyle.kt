package br.com.riseedit.core

import kotlin.math.max
import kotlin.math.min

enum class TextAlign { LEFT, CENTER, RIGHT }

enum class TextAnimation(val label: String) {
    NONE("Nenhuma"),
    FADE("Fade"),
    SLIDE_UP("Subir"),
    SLIDE_DOWN("Descer"),
    POP("Pop"),
    ZOOM("Zoom"),
    TYPEWRITER("Digitar")
}

/**
 * Look of a text layer. Sizes are fractions of the font size so the same style renders
 * identically at any output resolution. Fill color lives in [TextLayer.color].
 */
data class TextStyle(
    val font: String = "default",
    val bold: Boolean = true,
    val italic: Boolean = false,
    val align: TextAlign = TextAlign.CENTER,
    val letterSpacing: Float = 0f,
    val lineHeight: Float = 1.2f,
    val opacity: Float = 1f,
    val strokeColor: Int = BLACK,
    val strokeWidth: Float = 0f,
    val glowColor: Int = WHITE,
    val glow: Float = 0f,
    val backgroundColor: Int = BLACK,
    val background: Float = 0f,
    val backgroundRadius: Float = 0.25f,
    val shadowColor: Int = BLACK,
    val shadow: Float = 0f,
    val shadowBlur: Float = 0.12f,
    val shadowDistance: Float = 0.08f,
    val animationIn: TextAnimation = TextAnimation.FADE,
    val animationOut: TextAnimation = TextAnimation.FADE,
    val animationMs: Long = 400
) {
    init {
        require(opacity in 0f..1f && glow in 0f..1f && background in 0f..1f && shadow in 0f..1f)
        require(strokeWidth in 0f..0.5f && lineHeight in 0.6f..3f && letterSpacing in -0.2f..1f)
        require(animationMs in 0..5000)
    }

    companion object {
        const val BLACK = 0xFF000000.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()

        /** Projects saved before styles existed: optional 250 ms fade, otherwise defaults. */
        fun legacy(animated: Boolean) = TextStyle(
            animationIn = if (animated) TextAnimation.FADE else TextAnimation.NONE,
            animationOut = if (animated) TextAnimation.FADE else TextAnimation.NONE,
            animationMs = 250
        )
    }
}

/** Animated state of a text at one instant. [offsetY] is in NDC (y up); [reveal] is 0..1 chars. */
data class TextFrame(val alpha: Float, val offsetY: Float, val scale: Float, val reveal: Float)

object TextMotion {
    private const val SLIDE = 0.12f
    private const val POP_OVERSHOOT = 1.08f

    fun at(style: TextStyle, localMs: Long, durationMs: Long): TextFrame {
        if (localMs < 0 || localMs >= durationMs) return TextFrame(0f, 0f, 1f, 0f)
        val span = min(style.animationMs, durationMs / 2).coerceAtLeast(1)
        val enter = (localMs.toFloat() / span).coerceIn(0f, 1f)
        val exit = ((durationMs - localMs).toFloat() / span).coerceIn(0f, 1f)
        val a = contribution(style.animationIn, enter, entering = true)
        val b = contribution(style.animationOut, exit, entering = false)
        return TextFrame(
            a.alpha * b.alpha,
            a.offsetY + b.offsetY,
            a.scale * b.scale,
            min(a.reveal, b.reveal)
        )
    }

    private fun contribution(
        animation: TextAnimation,
        progress: Float,
        entering: Boolean
    ): TextFrame {
        val e = TransformPath.ease(progress.toDouble()).toFloat()
        val direction = if (entering) 1f else -1f
        return when (animation) {
            TextAnimation.NONE -> TextFrame(1f, 0f, 1f, 1f)
            TextAnimation.FADE -> TextFrame(e, 0f, 1f, 1f)
            TextAnimation.SLIDE_UP -> TextFrame(e, -(1 - e) * SLIDE * direction, 1f, 1f)
            TextAnimation.SLIDE_DOWN -> TextFrame(e, (1 - e) * SLIDE * direction, 1f, 1f)
            TextAnimation.POP -> TextFrame(min(1f, progress * 2), 0f, popScale(progress), 1f)
            TextAnimation.ZOOM -> TextFrame(e, 0f, 1.4f - 0.4f * e, 1f)
            TextAnimation.TYPEWRITER -> TextFrame(1f, 0f, 1f, progress)
        }
    }

    /** Ease-out-back from 0.6 to 1 whose overshoot is clamped so it never wobbles. */
    internal fun popScale(progress: Float): Float {
        val t = progress.coerceIn(0f, 1f) - 1
        val back = 1 + 2.2f * t * t * t + 1.2f * t * t
        return min(POP_OVERSHOOT, max(0f, 0.6f + 0.4f * back))
    }
}
