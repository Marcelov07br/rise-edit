package br.com.riseedit.tools

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clipToBounds
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import br.com.riseedit.TextTab
import br.com.riseedit.core.CaptionWord
import br.com.riseedit.core.TextLayer
import br.com.riseedit.core.TextLooks
import br.com.riseedit.text.StudioActions
import br.com.riseedit.text.TextFonts
import br.com.riseedit.text.TextStudio
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import org.junit.Before
import org.junit.Rule
import org.junit.Test

private val DEX_0260 = DeviceConfig.PIXEL_C.copy(
    screenWidth = 1536, screenHeight = 864, xdpi = 240, ydpi = 240,
    density = Density.HIGH, orientation = ScreenOrientation.LANDSCAPE
)

internal object Shots0260 {
    /** Frame-wide flow bitmap, zoomed 1.8× around the caption so blur and font mix read in the print. */
    @androidx.compose.runtime.Composable
    fun Over(scope: androidx.compose.foundation.layout.BoxScope, layer: TextLayer, localMs: Long) = with(scope) {
        val bmp = br.com.riseedit.text.TextRenderer.render(layer, 1280, 720, 1f, null, localMs)
        androidx.compose.foundation.layout.Box(
            androidx.compose.ui.Modifier.matchParentSize().then(androidx.compose.ui.Modifier.clipToBounds()),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            androidx.compose.foundation.Image(
                bmp.asImageBitmap(), null,
                androidx.compose.ui.Modifier.fillMaxWidth().graphicsLayer(scaleX = 1.8f, scaleY = 1.8f)
            )
        }
    }

    private val words = "eu amo essa vida demais, nunca pare".split(" ")

    /** Timed caption (300 ms per word) wearing a 0.26.0 flow preset. */
    fun caption(look: String, overrides: Map<Int, Boolean> = emptyMap()): TextLayer {
        val base = Shots0240.caption.copy(
            text = words.joinToString(" "),
            startMs = 0,
            endMs = words.size * 300L + 400,
            captionGroup = "g",
            words = words.mapIndexed { i, w -> CaptionWord(i * 300L, (i + 1) * 300L, w) },
            fontOverrides = overrides
        )
        return TextLooks.all.first { it.name == look }.let { it.applyTo(base).copy(highlightColor = it.highlightColor) }
    }
}

abstract class Release0260Base(private val dex: Boolean) {
    @get:Rule
    val p = Paparazzi(
        deviceConfig = if (dex) DEX_0260 else DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.NoActionBar"
    )

    @Before fun fonts() = TextFonts.install(p.context)

    private fun studio(tab: TextTab, layer: TextLayer, localMs: Long) = p.snapshot {
        Shots0240.Frame(dex, over = { Shots0260.Over(this, layer, localMs) }) {
            TextStudio(layer, {}, Shots0250.ui(tab), StudioActions(trackCount = 24))
        }
    }

    @Test fun gradeDeEstilos() = studio(TextTab.STYLE, Shots0260.caption("Cluster"), 1260)

    /** Mid-animation: "demais" 60 ms into its pop (vertical motion blur), Caveat on emotive words. */
    @Test fun presetCluster() = studio(TextTab.ANIMATION, Shots0260.caption("Cluster"), 1260)

    @Test fun presetHiperEscala() = studio(TextTab.ANIMATION, Shots0260.caption("Hiper-escala"), 1240)

    /** "demais" 130 ms into the 280 ms fade (gaussian blur), Playfair on "vida". */
    @Test fun presetSerifaCinema() = studio(TextTab.ANIMATION, Shots0260.caption("Serifa cinema"), 1330)

    /** "DEMAIS" sliding in with X motion blur and wide tracking snapping closed. */
    @Test fun presetVelocidade() = studio(TextTab.ANIMATION, Shots0260.caption("Velocidade"), 1225)

    /** Texto tab: per-word font chips (manual override on "essa"). */
    @Test fun fontePorPalavra() = studio(TextTab.TEXT, Shots0260.caption("Cluster", mapOf(2 to true)), 1100)
}

class Release0260PhoneShots : Release0260Base(false)
class Release0260DexShots : Release0260Base(true)
