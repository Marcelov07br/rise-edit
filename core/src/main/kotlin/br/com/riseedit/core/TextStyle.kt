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

/** 0.25.0: per word (or per line) motion, on top of the whole-text in/out animation. */
enum class WordAnimation(val label: String) {
    NONE("Nenhuma"),
    POP("Pop"),
    KARAOKE("Karaokê"),
    TYPE("Digitação"),
    RISE("Subir + fade"),
    BOUNCE("Bounce"),
    KEYWORD("Zoom na palavra-chave")
}

enum class WordUnit(val label: String) { WORD("Por palavra"), LINE("Por linha") }

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
    val animationMs: Long = 400,
    /** 0.25.0 */
    val wordAnimation: WordAnimation = WordAnimation.NONE,
    val wordUnit: WordUnit = WordUnit.WORD,
    /** Duration of each word's/line's own motion. */
    val wordMs: Long = 260,
    /** Bottom color of a vertical fill gradient (0 = solid [TextLayer.color]). */
    val gradientColor: Int = 0,
    /** Rounded box behind the spoken word (highlight color) — "caixa por palavra". */
    val wordBox: Boolean = false
) {
    init {
        require(opacity in 0f..1f && glow in 0f..1f && background in 0f..1f && shadow in 0f..1f)
        require(strokeWidth in 0f..0.5f && lineHeight in 0.6f..3f && letterSpacing in -0.2f..1f)
        require(animationMs in 0..5000 && wordMs in 40..2000)
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
        // 0.25.0: ease-out cubic (fast start, soft landing) instead of smoothstep.
        val e = Easing.outCubic(progress)
        val direction = if (entering) 1f else -1f
        return when (animation) {
            TextAnimation.NONE -> TextFrame(1f, 0f, 1f, 1f)
            TextAnimation.FADE -> TextFrame(e, 0f, 1f, 1f)
            TextAnimation.SLIDE_UP -> TextFrame(e, -(1 - e) * SLIDE * direction, 1f, 1f)
            TextAnimation.SLIDE_DOWN -> TextFrame(e, (1 - e) * SLIDE * direction, 1f, 1f)
            TextAnimation.POP -> TextFrame(min(1f, progress * 2), 0f, 0.6f + 0.4f * Easing.spring(progress), 1f)
            TextAnimation.ZOOM -> TextFrame(e, 0f, 1.4f - 0.4f * e, 1f)
            TextAnimation.TYPEWRITER -> TextFrame(1f, 0f, 1f, Easing.inOutSine(progress))
        }
    }

    /** Ease-out-back from 0.6 to 1 whose overshoot is clamped so it never wobbles. */
    internal fun popScale(progress: Float): Float {
        val t = progress.coerceIn(0f, 1f) - 1
        val back = 1 + 2.2f * t * t * t + 1.2f * t * t
        return min(POP_OVERSHOOT, max(0f, 0.6f + 0.4f * back))
    }
}

/** 0.25.0: no hard linear motion anywhere. */
object Easing {
    fun outCubic(t: Float): Float {
        val u = 1 - t.coerceIn(0f, 1f)
        return 1 - u * u * u
    }

    fun inOutSine(t: Float): Float = (0.5 - 0.5 * kotlin.math.cos(kotlin.math.PI * t.coerceIn(0f, 1f))).toFloat()

    /**
     * Critically-ish damped spring (ζ = 0.72, ~4 % overshoot, settles at t = 1): the low-bounce
     * "pop". Exactly 0 at t = 0 and 1 from t = 1 on.
     */
    fun spring(t: Float): Float {
        if (t <= 0f) return 0f
        if (t >= 1f) return 1f
        val zeta = 0.72
        val omega = 7.5
        val wd = omega * kotlin.math.sqrt(1 - zeta * zeta)
        val x = t.toDouble()
        val decay = kotlin.math.exp(-zeta * omega * x)
        val v = 1 - decay * (kotlin.math.cos(wd * x) + zeta / kotlin.math.sqrt(1 - zeta * zeta) * kotlin.math.sin(wd * x))
        // Blend to exactly 1 in the last 10 % so there is no jump when the motion ends.
        val tail = ((x - 0.9) / 0.1).coerceIn(0.0, 1.0)
        return (v * (1 - tail) + tail).toFloat()
    }

    /** 0 → peak → 0 bump with a small soft rebound (bounce on the spoken word). */
    fun bump(t: Float): Float {
        if (t <= 0f || t >= 1f) return 0f
        val x = t.toDouble()
        return (kotlin.math.sin(kotlin.math.PI * x * 1.5) * kotlin.math.exp(-3.2 * x) * 1.25).toFloat().coerceAtLeast(-0.12f)
    }
}

/** Motion of one word/line at an instant. [dy] is in font sizes (y down); [highlight] 0..1. */
data class WordFrame(
    val alpha: Float = 1f,
    val dy: Float = 0f,
    val scale: Float = 1f,
    val highlight: Float = 0f
) {
    companion object {
        val STILL = WordFrame()
    }
}

object WordMotion {
    /** Local [start, end) ms of each unit. Plain text: units enter one after another. */
    fun timings(style: TextStyle, units: Int, durationMs: Long, words: List<CaptionWord>?): List<LongRange> {
        if (units <= 0) return emptyList()
        if (words != null && words.size == units) return words.map { it.startMs until it.endMs }
        val spread = if (style.wordAnimation == WordAnimation.KARAOKE || style.wordAnimation == WordAnimation.BOUNCE ||
            style.wordAnimation == WordAnimation.KEYWORD
        ) {
            durationMs // spoken-like: spread over the whole text
        } else {
            minOf(durationMs / 2, units * 120L).coerceAtLeast(1)
        }
        val step = spread / units
        return List(units) { i -> (i * step) until ((i + 1) * step).coerceAtLeast(i * step + 1) }
    }

    /** Index of the keyword: the longest word (ties → the first). */
    fun keyword(texts: List<String>): Int =
        texts.withIndex().maxByOrNull { (_, w) -> w.count { it.isLetterOrDigit() } }?.index ?: -1

    fun at(style: TextStyle, unit: LongRange, localMs: Long, keyword: Boolean = false): WordFrame {
        val since = localMs - unit.first
        val t = since.toFloat() / style.wordMs
        val active = localMs in unit
        val spoken = localMs >= unit.first
        return when (style.wordAnimation) {
            WordAnimation.NONE -> WordFrame.STILL
            WordAnimation.POP -> if (!spoken) {
                WordFrame(alpha = 0f)
            } else {
                WordFrame(alpha = minOf(1f, t * 3f), scale = 0.55f + 0.45f * Easing.spring(t))
            }
            WordAnimation.RISE -> if (!spoken) {
                WordFrame(alpha = 0f, dy = 0.35f)
            } else {
                val e = Easing.outCubic(t)
                WordFrame(alpha = e, dy = (1 - e) * 0.35f)
            }
            WordAnimation.TYPE -> WordFrame(alpha = if (spoken) Easing.outCubic(since / 60f) else 0f)
            WordAnimation.KARAOKE -> WordFrame(
                alpha = if (spoken) 1f else 0.62f,
                highlight = if (spoken) Easing.outCubic(since / 120f) else 0f
            )
            WordAnimation.BOUNCE -> WordFrame(
                scale = 1f + 0.2f * Easing.bump(t),
                dy = -0.12f * Easing.bump(t),
                highlight = if (active) 1f else 0f
            )
            WordAnimation.KEYWORD -> if (keyword) {
                val inT = if (spoken) Easing.spring(t) else 0f
                WordFrame(scale = 1f + 0.2f * inT, highlight = inT)
            } else {
                WordFrame.STILL
            }
        }
    }

    /** Extra room (in font sizes) the bitmap needs around the text for word motion. */
    fun margin(style: TextStyle) = when (style.wordAnimation) {
        WordAnimation.NONE -> 0f
        WordAnimation.KEYWORD -> 0.55f
        WordAnimation.BOUNCE, WordAnimation.RISE -> 0.4f
        else -> 0.3f
    }
}
