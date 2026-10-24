package br.com.riseedit.tools

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import br.com.riseedit.EditorUiState
import br.com.riseedit.TextTab
import br.com.riseedit.core.CaptionWords
import br.com.riseedit.core.LookScope
import br.com.riseedit.core.TextLayer
import br.com.riseedit.core.TextLooks
import br.com.riseedit.core.WhisperModel
import br.com.riseedit.subtitles.CaptionsActions
import br.com.riseedit.subtitles.CaptionsContent
import br.com.riseedit.subtitles.SubtitleStatus
import br.com.riseedit.text.StudioActions
import br.com.riseedit.text.TextFonts
import br.com.riseedit.text.TextRenderer
import br.com.riseedit.text.TextStudio
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import org.junit.Before
import org.junit.Rule
import org.junit.Test

private val DEX = DeviceConfig.PIXEL_C.copy(
    screenWidth = 1536, screenHeight = 864, xdpi = 240, ydpi = 240,
    density = Density.HIGH, orientation = ScreenOrientation.LANDSCAPE
)

internal object Shots0250 {
    /** A caption wearing [look] (group "g": the track the scope switch talks about). */
    fun caption(look: String = "Caixa por palavra"): TextLayer =
        TextLooks.all.first { it.name == look }.applyTo(Shots0240.caption.copy(captionGroup = "g"))

    @Composable
    fun Over(scope: BoxScope, layer: TextLayer, localMs: Long = 1500) = with(scope) {
        val hl = CaptionWords.activeRange(layer, localMs)
        val bmp = TextRenderer.render(layer, 1280, 720, 1f, hl, localMs)
        Image(
            bmp.asImageBitmap(), null,
            Modifier.align(Alignment.BottomCenter).fillMaxWidth(0.8f).padding(bottom = 18.dp)
        )
    }

    fun ui(tab: TextTab) = EditorUiState(mutableStateOf(null)).apply {
        textTab = tab
        lookScope = LookScope.ALL
    }
}

abstract class Release0250Base(private val dex: Boolean) {
    @get:Rule
    val p = Paparazzi(
        deviceConfig = if (dex) DEX else DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.NoActionBar"
    )

    @Before fun fonts() = TextFonts.install(p.context)

    private fun studio(
        tab: TextTab,
        layer: TextLayer = Shots0250.caption(),
        info: Boolean = false,
        fonts: Boolean = false,
        localMs: Long = 1500
    ) = p.snapshot {
        Shots0240.Frame(dex, over = { Shots0250.Over(this, layer, localMs) }) {
            TextStudio(
                layer, {}, Shots0250.ui(tab),
                StudioActions(trackCount = 24),
                infoInitially = info,
                fontMenuOpen = fonts
            )
        }
    }

    @Test fun legendaAbaTexto() = studio(TextTab.TEXT)
    @Test fun legendaAbaEstilo() = studio(TextTab.STYLE)
    @Test fun legendaAbaAnimacao() = studio(TextTab.ANIMATION, Shots0250.caption("Palavra-chave"), localMs = 1250)
    @Test fun estilosGradeAltaResolucao() = studio(TextTab.STYLE, Shots0250.caption("Elegante"))
    @Test fun seletorDeFontes() = studio(TextTab.TEXT, Shots0250.caption("Editorial"), fonts = true)
    @Test fun aplicarATodas() = studio(TextTab.STYLE, Shots0250.caption("Podcast"), info = true)
    @Test fun textoSimplesIndividual() = studio(
        TextTab.STYLE,
        TextLooks.all.first { it.name == "Gradiente" }.applyTo(TextLayer(text = "TÍTULO DO VÍDEO"))
    )

    @Test fun legendasProgressivas() = p.snapshot {
        Shots0240.Frame(dex, over = { Shots0250.Over(this, Shots0250.caption("Clássica")) }) {
            Column {
                CaptionsContent(
                    CaptionsActions(
                        status = SubtitleStatus(
                            true,
                            "Entrevista: transcrevendo trecho 2/4 · 9 legendas",
                            0.38f
                        ),
                        downloaded = listOf(WhisperModel.TINY_Q5, WhisperModel.BASE_Q5),
                        captions = 9
                    ),
                    initialModel = WhisperModel.TINY_Q5
                )
            }
        }
    }
}

class Release0250PhoneShots : Release0250Base(false)
class Release0250DexShots : Release0250Base(true)
