package br.com.riseedit.text

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Typeface

/** Bundled SIL OFL 1.1 fonts (see assets/fonts/OFL-*.txt) plus the system sans. */
object TextFonts {
    data class Font(
        val key: String,
        val label: String,
        val regular: String?,
        val bold: String? = null,
        val variable: Boolean = false
    )

    val all = listOf(
        Font("default", "Padrão", null),
        Font("inter", "Inter", "fonts/Inter.ttf", variable = true),
        Font("montserrat", "Montserrat", "fonts/Montserrat.ttf", variable = true),
        Font("bebas", "Bebas Neue", "fonts/BebasNeue.ttf"),
        Font("playfair", "Playfair", "fonts/PlayfairDisplay.ttf", variable = true),
        Font("poppins", "Poppins", "fonts/Poppins-Regular.ttf", "fonts/Poppins-Bold.ttf")
    )

    @Volatile private var assets: AssetManager? = null
    private val cache = HashMap<String, Typeface>()

    fun install(context: Context) {
        if (assets == null) assets = context.applicationContext.assets
    }

    fun typeface(key: String, bold: Boolean, italic: Boolean): Typeface = synchronized(cache) {
        cache.getOrPut("$key/$bold/$italic") { load(key, bold, italic) }
    }

    private fun load(key: String, bold: Boolean, italic: Boolean): Typeface {
        val style = when {
            bold && italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        val font = all.firstOrNull { it.key == key }
        val manager = assets
        if (font?.regular == null ||
            manager == null
        ) {
            return Typeface.create(Typeface.SANS_SERIF, style)
        }
        return runCatching {
            when {
                font.variable -> {
                    val base = Typeface.Builder(manager, font.regular)
                        .setFontVariationSettings("'wght' ${if (bold) 700 else 400}")
                        .build()
                    if (italic) Typeface.create(base, Typeface.ITALIC) else base
                }
                bold && font.bold != null -> {
                    val base = Typeface.createFromAsset(manager, font.bold)
                    if (italic) Typeface.create(base, Typeface.ITALIC) else base
                }
                else -> Typeface.create(Typeface.createFromAsset(manager, font.regular), style)
            }
        }.getOrElse { Typeface.create(Typeface.SANS_SERIF, style) }
    }
}
