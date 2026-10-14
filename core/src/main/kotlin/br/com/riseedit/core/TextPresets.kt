package br.com.riseedit.core

/** A named one-tap look. [apply] keeps everything the preset does not own (font, timing). */
data class TextPreset(val name: String, val color: Int? = null, val apply: (TextStyle) -> TextStyle)

object TextPresets {
    private const val BLACK = TextStyle.BLACK
    private const val WHITE = TextStyle.WHITE

    val palette =
        listOf(
            WHITE, BLACK, 0xFF9E9E9E.toInt(), 0xFFFF3B30.toInt(), 0xFFFF9500.toInt(),
            0xFFFFCC00.toInt(), 0xFF34C759.toInt(), 0xFF00C7BE.toInt(), 0xFF007AFF.toInt(),
            0xFF5856D6.toInt(), 0xFFAF52DE.toInt(), 0xFFFF2D55.toInt()
        )

    private fun plain(s: TextStyle) =
        s.copy(strokeWidth = 0f, glow = 0f, background = 0f, shadow = 0f)

    /** The "Aa" row of the Estilos tab. */
    val styles =
        listOf(
            TextPreset("Nenhum") { plain(it) },
            TextPreset("Contorno", WHITE) {
                plain(it).copy(strokeWidth = 0.12f, strokeColor = BLACK)
            },
            TextPreset("Sombra", WHITE) { plain(it).copy(shadow = 0.8f, shadowColor = BLACK) },
            TextPreset("Fundo", BLACK) { plain(it).copy(background = 1f, backgroundColor = WHITE) },
            TextPreset("Fundo escuro", WHITE) {
                plain(it).copy(background = 0.75f, backgroundColor = BLACK)
            },
            TextPreset("Amarelo", 0xFFFFCC00.toInt()) {
                plain(it).copy(strokeWidth = 0.12f, strokeColor = BLACK)
            },
            TextPreset("Vermelho", WHITE) {
                plain(it).copy(background = 1f, backgroundColor = 0xFFFF3B30.toInt())
            },
            TextPreset("Neon", WHITE) {
                plain(it).copy(glow = 0.8f, glowColor = 0xFF00C7BE.toInt())
            }
        )

    /** The Efeitos tab: stronger combinations. */
    val effects =
        listOf(
            TextPreset("Nenhum") { plain(it) },
            TextPreset("Neon rosa", WHITE) {
                plain(it).copy(glow = 1f, glowColor = 0xFFFF2D55.toInt())
            },
            TextPreset("Brilho", WHITE) { plain(it).copy(glow = 0.6f, glowColor = WHITE) },
            TextPreset("Sombra dura", WHITE) {
                plain(it).copy(shadow = 1f, shadowBlur = 0f, shadowDistance = 0.12f)
            },
            TextPreset("Contorno grosso", WHITE) {
                plain(it).copy(strokeWidth = 0.25f, strokeColor = BLACK)
            },
            TextPreset("Recorte", BLACK) {
                plain(it).copy(strokeWidth = 0.15f, strokeColor = WHITE, shadow = 0.6f)
            },
            TextPreset("Etiqueta", WHITE) {
                plain(
                    it
                ).copy(
                    background = 1f,
                    backgroundColor = 0xFF5856D6.toInt(),
                    backgroundRadius = 0.5f
                )
            }
        )
}

/** Font plus style plus motion, applied as one template (Modelos tab). */
data class TextTemplate(val name: String, val sample: String, val color: Int, val style: TextStyle)

object TextTemplates {
    val all =
        listOf(
            TextTemplate(
                "Título",
                "TÍTULO",
                TextStyle.WHITE,
                TextStyle(font = "bebas", animationIn = TextAnimation.POP)
            ),
            TextTemplate(
                "Legenda",
                "Legenda",
                TextStyle.WHITE,
                TextStyle(font = "inter", background = 0.7f, animationIn = TextAnimation.FADE)
            ),
            TextTemplate(
                "Elegante",
                "Elegante",
                TextStyle.WHITE,
                TextStyle(
                    font = "playfair",
                    bold = false,
                    italic = true,
                    shadow = 0.6f,
                    animationIn = TextAnimation.SLIDE_UP
                )
            ),
            TextTemplate(
                "Neon",
                "Neon",
                TextStyle.WHITE,
                TextStyle(
                    font = "montserrat",
                    glow = 0.9f,
                    glowColor = 0xFF00C7BE.toInt(),
                    animationIn = TextAnimation.ZOOM
                )
            ),
            TextTemplate(
                "Destaque",
                "DESTAQUE",
                TextStyle.BLACK,
                TextStyle(
                    font = "poppins",
                    background = 1f,
                    backgroundColor = 0xFFFFCC00.toInt(),
                    animationIn = TextAnimation.POP
                )
            ),
            TextTemplate(
                "Digitando",
                "digitando…",
                TextStyle.WHITE,
                TextStyle(
                    font = "inter",
                    bold = false,
                    animationIn = TextAnimation.TYPEWRITER,
                    animationOut = TextAnimation.FADE,
                    animationMs = 1200
                )
            )
        )
}
