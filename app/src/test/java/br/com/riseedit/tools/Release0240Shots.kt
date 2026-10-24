package br.com.riseedit.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import br.com.riseedit.core.WhisperModel
import br.com.riseedit.subtitles.CaptionsActions
import br.com.riseedit.subtitles.CaptionsContent
import br.com.riseedit.subtitles.SubtitleStatus
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import br.com.riseedit.EditorState
import br.com.riseedit.RiseTheme
import br.com.riseedit.core.BackgroundKind
import br.com.riseedit.core.Clip
import br.com.riseedit.core.FrameBackground
import br.com.riseedit.core.Orientation
import br.com.riseedit.core.Project
import br.com.riseedit.core.ScaleMode
import br.com.riseedit.core.TransformKeyframe
import br.com.riseedit.designsystem.RiseScreenFrame
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import org.junit.Rule
import org.junit.Test

/** Shared fixtures of the 0.24.0 prints (phone + DeX). */
internal object Shots0240 {
    fun clip(fit: Boolean = false, keyed: Boolean = false) = Clip(
        uri = "file:///entrevista-4k.mp4", name = "Entrevista", sourceDurationMs = 64_000
    ).copy(
        orientation = Orientation(scaleMode = if (fit) ScaleMode.FIT else ScaleMode.FILL),
        keyframes = if (keyed) {
            listOf(TransformKeyframe(0, x = 0.42f), TransformKeyframe(6000, x = -0.3f), TransformKeyframe(12000, x = 0.1f))
        } else listOf(TransformKeyframe(0, x = 0.35f, y = -0.05f, scale = 1.15f, rotation = 4f))
    )

    fun state(
        ratio: Float = 9f / 16f,
        fit: Boolean = false,
        bg: FrameBackground = FrameBackground(),
        keyed: Boolean = false
    ) = EditorState(project = Project(clips = listOf(clip(fit, keyed)), aspectRatio = ratio, background = bg))

    @Composable
    fun Preview(modifier: Modifier = Modifier) {
        Box(
            modifier.background(
                Brush.verticalGradient(listOf(Color(0xFF5AA0D8), Color(0xFFE9C58A)))
            )
        )
    }

    /** Phone: preview on top, panel below. DeX: preview left, panel column right. */
    @Composable
    fun Frame(
        dex: Boolean,
        over: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {},
        panel: @Composable () -> Unit
    ) = RiseTheme {
        RiseScreenFrame {
            if (dex) {
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxHeight().padding(16.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) { Preview(Modifier.fillMaxSize()); over() }
                    }
                    Column(Modifier.width(460.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp)) { panel() }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) { Preview(Modifier.fillMaxSize()); over() }
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) { panel() }
                }
            }
        }
    }

    val caption = br.com.riseedit.core.TextLayer(
        text = "Bora editar no tablet hoje",
        startMs = 4_000,
        endMs = 7_000,
        words = listOf(
            br.com.riseedit.core.CaptionWord(0, 420, "Bora"),
            br.com.riseedit.core.CaptionWord(420, 1100, "editar"),
            br.com.riseedit.core.CaptionWord(1100, 1350, "no"),
            br.com.riseedit.core.CaptionWord(1350, 2200, "tablet"),
            br.com.riseedit.core.CaptionWord(2200, 3000, "hoje")
        ),
        highlightColor = br.com.riseedit.core.CaptionWords.HIGHLIGHT
    )

    @Composable
    fun CaptionOver(scope: androidx.compose.foundation.layout.BoxScope) = with(scope) {
        val hl = br.com.riseedit.core.CaptionWords.activeRange(caption, 1500)
        val bmp = br.com.riseedit.text.TextRenderer.render(caption, 1280, 720, 1f, hl)
        androidx.compose.foundation.Image(
            bmp.asImageBitmap(), null,
            Modifier.align(Alignment.BottomCenter).fillMaxWidth(0.8f).padding(bottom = 18.dp)
        )
    }

    val blur = FrameBackground(BackgroundKind.BLUR, blur = 0.7f, dim = 0.3f)
    val color = FrameBackground(BackgroundKind.COLOR, color = 0xFF0A84FF.toInt())
}

private val DEX = DeviceConfig.PIXEL_C.copy(
    screenWidth = 1536, screenHeight = 864, xdpi = 240, ydpi = 240,
    density = Density.HIGH, orientation = ScreenOrientation.LANDSCAPE
)

abstract class Release0240Base(private val dex: Boolean) {
    @get:Rule
    val p = Paparazzi(
        deviceConfig = if (dex) DEX else DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.NoActionBar"
    )

    private fun format(state: EditorState, tab: FormatTab, open: Boolean = false, info: Boolean = false) =
        p.snapshot { Shots0240.Frame(dex) { FormatContent(state, FormatActions(), tab, info, open) } }

    @Test fun formatProporcao() = format(Shots0240.state(), FormatTab.RATIO)
    @Test fun formatProporcaoLivre() = format(Shots0240.state(ratio = 1.85f), FormatTab.RATIO)
    @Test fun formatEnquadrar() = format(Shots0240.state(), FormatTab.FRAME, info = true)
    @Test fun reframeBotao() = p.snapshot {
        Shots0240.Frame(dex) { FormatContent(Shots0240.state(keyed = true), FormatActions(reframe = {}), FormatTab.FRAME) }
    }
    @Test fun reframeAnalisando() = p.snapshot {
        Shots0240.Frame(dex) {
            FormatContent(
                Shots0240.state(keyed = true).copy(busy = br.com.riseedit.editor.ReframeCoordinator.BUSY, progress = 0.42f),
                FormatActions(reframe = {}, reframeProgress = 0.42f),
                FormatTab.FRAME
            )
        }
    }
    @Test fun formatFundoDesfoque() = format(Shots0240.state(fit = true, bg = Shots0240.blur), FormatTab.BACKGROUND)
    @Test fun formatFundoCor() = format(Shots0240.state(fit = true, bg = Shots0240.color), FormatTab.BACKGROUND)
    @Test fun formatFundoMenu() = format(Shots0240.state(fit = true, bg = Shots0240.blur), FormatTab.BACKGROUND, open = true)

    @Test fun legendasBaixandoModelo() = p.snapshot {
        Shots0240.Frame(dex) {
            CaptionsContent(CaptionsActions(status = SubtitleStatus(true, "Baixando Base q5: 25/59 MB", 0.42f)))
        }
    }
    @Test fun legendasVerificado() = p.snapshot {
        Shots0240.Frame(dex) {
            CaptionsContent(
                CaptionsActions(
                    status = SubtitleStatus(false, "14 legendas · SHA-256 verificado"),
                    downloaded = listOf(WhisperModel.BASE_Q5)
                ),
                infoOpenInitially = true
            )
        }
    }
    @Test fun legendasPalavras() = p.snapshot {
        Shots0240.Frame(dex, over = { Shots0240.CaptionOver(this) }) {
            CaptionsContent(
                CaptionsActions(downloaded = listOf(WhisperModel.BASE_Q5), selected = Shots0240.caption),
                initialWord = 3
            )
        }
    }
}

class Release0240PhoneShots : Release0240Base(false)
class Release0240DexShots : Release0240Base(true)
