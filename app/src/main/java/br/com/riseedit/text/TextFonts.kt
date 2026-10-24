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
        val variable: Boolean = false,
        val category: Category = Category.SANS
    )

    enum class Category(val label: String) { SANS("Sans"), SERIF("Serifa"), DISPLAY("Display") }

    /** Latin subsets (pyftsubset) of google/fonts @ e7db6ca, all SIL OFL 1.1. */
    val all = listOf(
        Font("default", "Padrão", null),
        Font("inter", "Inter", "fonts/Inter.ttf", variable = true),
        Font("manrope", "Manrope", "fonts/Manrope.ttf", variable = true),
        Font("dmsans", "DM Sans", "fonts/DMSans.ttf", variable = true),
        Font("outfit", "Outfit", "fonts/Outfit.ttf", variable = true),
        Font("jakarta", "Plus Jakarta Sans", "fonts/PlusJakartaSans.ttf", variable = true),
        Font("sora", "Sora", "fonts/Sora.ttf", variable = true),
        Font("spacegrotesk", "Space Grotesk", "fonts/SpaceGrotesk.ttf", variable = true),
        Font("montserrat", "Montserrat", "fonts/Montserrat.ttf", variable = true),
        Font("poppins", "Poppins", "fonts/Poppins-Regular.ttf", "fonts/Poppins-Bold.ttf"),
        Font("playfair", "Playfair", "fonts/PlayfairDisplay.ttf", variable = true, category = Category.SERIF),
        Font("cormorant", "Cormorant", "fonts/CormorantGaramond.ttf", variable = true, category = Category.SERIF),
        Font("dmserif", "DM Serif Display", "fonts/DMSerifDisplay.ttf", category = Category.SERIF),
        Font("fraunces", "Fraunces", "fonts/Fraunces.ttf", variable = true, category = Category.SERIF),
        Font("caslon", "Libre Caslon", "fonts/LibreCaslonText.ttf", variable = true, category = Category.SERIF),
        Font("lora", "Lora", "fonts/Lora.ttf", variable = true, category = Category.SERIF),
        Font("instrument", "Instrument Serif", "fonts/InstrumentSerif.ttf", category = Category.SERIF),
        Font("bebas", "Bebas Neue", "fonts/BebasNeue.ttf", category = Category.DISPLAY),
        // 0.26.0 (OFL, google/fonts @ e7db6ca): presets Cluster / Serifa cinema / Velocidade.
        Font("oswald", "Oswald", "fonts/Oswald.ttf", variable = true, category = Category.DISPLAY),
        Font("caveat", "Caveat", "fonts/Caveat.ttf", variable = true, category = Category.DISPLAY),
        Font("poppinsmedium", "Poppins Medium", "fonts/Poppins-Medium.ttf")
    )

    @Volatile private var assets: AssetManager? = null
    private val cache = HashMap<String, Typeface>()

    fun install(context: Context) {
        if (assets == null) assets = context.applicationContext.assets
    }

    /** [weight] 0 = 700 when bold else 400 (variable fonts take any wght, e.g. Montserrat Black 900). */
    fun typeface(key: String, bold: Boolean, italic: Boolean, weight: Int = 0): Typeface = synchronized(cache) {
        cache.getOrPut("$key/$bold/$italic/$weight") { load(key, bold, italic, weight) }
    }

    private fun load(key: String, bold: Boolean, italic: Boolean, weight: Int): Typeface {
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
                        .setFontVariationSettings("'wght' ${if (weight > 0) weight else if (bold) 700 else 400}")
                        .build()
                    if (italic) Typeface.create(base, Typeface.ITALIC) else base
                }
                bold && font.bold != null -> {
                    val base = Typeface.createFromAsset(manager, font.bold)
                    if (italic) Typeface.create(base, Typeface.ITALIC) else base
                }
                // Static single-weight files (Poppins Medium) are already the weight asked for.
                weight > 0 -> Typeface.create(Typeface.createFromAsset(manager, font.regular), if (italic) Typeface.ITALIC else Typeface.NORMAL)
                else -> Typeface.create(Typeface.createFromAsset(manager, font.regular), style)
            }
        }.getOrElse { Typeface.create(Typeface.SANS_SERIF, style) }
    }
}
