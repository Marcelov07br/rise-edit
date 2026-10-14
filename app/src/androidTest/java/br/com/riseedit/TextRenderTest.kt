package br.com.riseedit

import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import br.com.riseedit.core.TextLayer
import br.com.riseedit.core.TextStyle
import br.com.riseedit.text.TextFonts
import br.com.riseedit.text.TextRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The preview measures and the export bitmaps come from one renderer and scale together. */
class TextRenderTest {
    init {
        TextFonts.install(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    private fun opaque(bitmap: android.graphics.Bitmap): Int {
        var count = 0
        for (y in 0 until bitmap.height step 2) {
            for (x in 0 until bitmap.width step 2) {
                if (Color.alpha(bitmap.getPixel(x, y)) > 128) count++
            }
        }
        return count
    }

    @Test
    fun measureMatchesRenderAtAnyResolution() {
        val layer =
            TextLayer(text = "Rise Edit\nsegunda linha", style = TextStyle(font = "montserrat"))
        val (w, h) = TextRenderer.measure(layer, 16f / 9f)
        listOf(1280 to 720, 1920 to 1080).forEach { (fw, fh) ->
            val bitmap = TextRenderer.render(layer, fw, fh)
            assertEquals(w, bitmap.width.toFloat() / fw, 0.01f)
            assertEquals(h, bitmap.height.toFloat() / fh, 0.01f)
        }
    }

    @Test
    fun fontsStylesAndRevealChangeThePixels() {
        val base = TextLayer(text = "Aa Bb", style = TextStyle(strokeWidth = 0f))
        val plain = opaque(TextRenderer.render(base, 1280, 720))
        val stroked =
            opaque(
                TextRenderer.render(
                    base.copy(style = base.style.copy(strokeWidth = 0.2f)),
                    1280,
                    720
                )
            )
        val boxed =
            opaque(
                TextRenderer.render(base.copy(style = base.style.copy(background = 1f)), 1280, 720)
            )
        val half = opaque(TextRenderer.render(base, 1280, 720, reveal = 0.4f))
        assertTrue(plain > 50)
        assertTrue("stroke $stroked vs $plain", stroked > plain * 1.2)
        assertTrue("box $boxed vs $plain", boxed > plain * 2)
        assertTrue("reveal $half vs $plain", half < plain * 0.8)
        val fonts = TextFonts.all.map { font ->
            TextRenderer.render(base.copy(style = TextStyle(font = font.key)), 1280, 720).width
        }
        assertTrue("bundled fonts should differ: $fonts", fonts.toSet().size >= 4)
    }
}
