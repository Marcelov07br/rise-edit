package br.com.riseedit.core

/**
 * 0.25.0: a complete look (fill, spoken-word color, style incl. word motion) shown in the
 * Estilo grid. Applying a look keeps the text, timing and position of the layer.
 */
data class TextLook(
    val name: String,
    val color: Int,
    val highlightColor: Int,
    val style: TextStyle
) {
    fun applyTo(layer: TextLayer): TextLayer = layer.copy(
        color = color,
        // Plain text has no word timings: a spoken-word color only makes sense on captions.
        highlightColor = if (layer.isCaption) highlightColor else 0,
        style = style
    )

    fun matches(layer: TextLayer) = layer.style == style && layer.color == color
}

object TextLooks {
    private const val WHITE = TextStyle.WHITE
    private const val BLACK = TextStyle.BLACK
    private const val YELLOW = 0xFFFFCC00.toInt()
    private const val PINK = 0xFFFF2D55.toInt()
    private const val GREEN = 0xFF34C759.toInt()
    private const val CYAN = 0xFF00C7BE.toInt()
    private val base = TextStyle(animationIn = TextAnimation.FADE, animationOut = TextAnimation.FADE, animationMs = 240)

    private val flowBase = TextStyle(animationIn = TextAnimation.NONE, animationOut = TextAnimation.NONE)

    val all: List<TextLook> = listOf(
        TextLook("Clássica", WHITE, YELLOW, base.copy(font = "inter", strokeWidth = 0.1f)),
        TextLook(
            "Caixa por palavra", WHITE, PINK,
            base.copy(font = "montserrat", wordBox = true, wordAnimation = WordAnimation.POP, backgroundRadius = 0.35f)
        ),
        TextLook("Contorno grosso", WHITE, YELLOW, base.copy(font = "montserrat", strokeWidth = 0.28f)),
        TextLook(
            "Sombra", WHITE, YELLOW,
            base.copy(font = "poppins", shadow = 0.9f, shadowBlur = 0.2f, shadowDistance = 0.1f)
        ),
        TextLook(
            "Gradiente", 0xFFFFC107.toInt(), WHITE,
            base.copy(font = "outfit", gradientColor = 0xFFFF4081.toInt(), strokeWidth = 0.06f)
        ),
        TextLook(
            "Podcast", WHITE, GREEN,
            base.copy(
                font = "manrope", background = 0.82f, backgroundRadius = 0.35f,
                wordAnimation = WordAnimation.RISE, wordUnit = WordUnit.LINE
            )
        ),
        // Channel identity: Inter, white, slightly open tracking, short fade/scale entrance.
        TextLook(
            "Canal", WHITE, 0,
            base.copy(
                font = "inter", letterSpacing = 0.03f, shadow = 0.35f, shadowBlur = 0.25f,
                animationIn = TextAnimation.ZOOM, animationMs = 260
            )
        ),
        TextLook(
            "Karaokê", 0xB3FFFFFF.toInt(), YELLOW,
            base.copy(font = "montserrat", strokeWidth = 0.08f, wordAnimation = WordAnimation.KARAOKE)
        ),
        TextLook(
            "Digitação", WHITE, CYAN,
            base.copy(font = "spacegrotesk", background = 0.7f, wordAnimation = WordAnimation.TYPE, wordMs = 90)
        ),
        TextLook(
            "Elegante", WHITE, 0xFFF5D08A.toInt(),
            base.copy(font = "cormorant", italic = true, shadow = 0.5f, wordAnimation = WordAnimation.RISE)
        ),
        TextLook(
            "Editorial", BLACK, 0xFFB3261E.toInt(),
            base.copy(font = "dmserif", bold = false, background = 0.95f, backgroundColor = 0xFFF5F0E6.toInt())
        ),
        TextLook("Neon", WHITE, CYAN, base.copy(font = "sora", glow = 0.9f, glowColor = CYAN)),
        TextLook(
            "Palavra-chave", WHITE, YELLOW,
            base.copy(font = "jakarta", strokeWidth = 0.1f, wordAnimation = WordAnimation.KEYWORD)
        ),
        TextLook(
            "Bounce", WHITE, PINK,
            base.copy(font = "outfit", strokeWidth = 0.12f, wordAnimation = WordAnimation.BOUNCE)
        ),
        TextLook("Minimal", WHITE, 0, base.copy(font = "dmsans", bold = false, shadow = 0.35f)),
        TextLook(
            "Livro", WHITE, 0xFFF5D08A.toInt(),
            base.copy(font = "caslon", shadow = 0.6f, shadowBlur = 0.3f)
        ),
        // 0.26.0 flow presets (own layout + motion; hard cut / crossfade instead of fade in/out).
        TextLook(
            "Cluster", WHITE, CYAN,
            flowBase.copy(
                font = "montserrat", weight = 900, secondaryFont = "caveat", secondaryWeight = 700,
                caps = CapsRule.LOWER, letterSpacing = -0.04f, flow = FlowKind.CLUSTER, maxWords = 5,
                shadow = 0.4f, shadowBlur = 0.17f, shadowDistance = 0.07f
            )
        ),
        TextLook(
            "Hiper-escala", WHITE, 0xFFFFD700.toInt(),
            flowBase.copy(
                font = "montserrat", weight = 800, flow = FlowKind.HYPER, maxWords = 3,
                shadow = 0.5f, shadowBlur = 0.09f, shadowDistance = 0.05f
            )
        ),
        TextLook(
            "Serifa cinema", WHITE, 0,
            flowBase.copy(
                font = "poppinsmedium", bold = false, secondaryFont = "playfair", secondaryWeight = 700,
                letterSpacing = 0.03f, flow = FlowKind.CINEMA, maxWords = 5, glow = 0.15f
            )
        ),
        TextLook(
            "Velocidade", WHITE, 0xFFF4D03F.toInt(),
            flowBase.copy(
                font = "oswald", weight = 700, caps = CapsRule.EMPHASIS_UPPER, flow = FlowKind.SPEED, maxWords = 3,
                shadow = 0.3f, shadowBlur = 0.07f, shadowDistance = 0.035f
            )
        )
    )
}

/** 0.25.0: where a look edit on a caption goes. Plain text is always individual. */
enum class LookScope(val label: String) { ALL("Todas as legendas"), ONLY_THIS("Só esta") }

object CaptionScope {
    /** Same caption track: same [TextLayer.captionGroup], or both ungrouped captions. */
    fun sameTrack(a: TextLayer, b: TextLayer): Boolean =
        a.isCaption && b.isCaption && a.captionGroup == b.captionGroup

    /** [target] wearing the look of [from]: style, colors, size and position (keeps text/timing). */
    fun withLook(target: TextLayer, from: TextLayer): TextLayer = target.copy(
        style = from.style,
        color = from.color,
        highlightColor = from.highlightColor,
        size = from.size,
        x = from.x,
        y = from.y
    )

    /** Texts after committing [edited]: one list, so the caller makes it a single undo step. */
    fun apply(texts: List<TextLayer>, edited: TextLayer, scope: LookScope): List<TextLayer> {
        val all = scope == LookScope.ALL && edited.isCaption
        val out = texts.map {
            when {
                it.id == edited.id -> edited
                all && sameTrack(it, edited) -> withLook(it, edited)
                else -> it
            }
        }
        return if (texts.any { it.id == edited.id }) out else out + edited
    }

    /** How many layers an ALL edit of [edited] touches (incl. itself). */
    fun count(texts: List<TextLayer>, edited: TextLayer): Int =
        if (!edited.isCaption) 1 else texts.count { it.id == edited.id || sameTrack(it, edited) }
}
