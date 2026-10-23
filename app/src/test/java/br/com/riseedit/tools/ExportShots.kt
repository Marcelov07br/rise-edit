package br.com.riseedit.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import br.com.riseedit.EditorTopBar
import br.com.riseedit.RiseTheme
import br.com.riseedit.core.Chroma
import br.com.riseedit.core.Clip
import br.com.riseedit.core.EncoderSupport
import br.com.riseedit.core.ExportSettings
import br.com.riseedit.core.Project
import br.com.riseedit.core.VideoCodec
import br.com.riseedit.designsystem.LocalRiseInsets
import br.com.riseedit.designsystem.RiseScreenFrame
import br.com.riseedit.editor.ExportBus
import br.com.riseedit.export.ExportContent
import br.com.riseedit.export.RenderBubble
import br.com.riseedit.export.RenderTab
import org.junit.Rule
import org.junit.Test

/** 0.21: export panel, advanced section, render bubble over the editor, render tab. */
class ExportShots {
    @get:Rule
    val p = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "android:Theme.Material.NoActionBar")

    /** A phone with Main10 + HEVC 4:2:2 hardware, no APV (Android 15), and the bundled ProRes. */
    private val support = EncoderSupport(h264 = true, hevc = true, hevc10 = true, hevc422 = true, prores = true)
    private val project = Project(name = "Viagem")

    private fun phone(content: @Composable () -> Unit) = p.snapshot {
        RiseTheme {
            CompositionLocalProvider(LocalRiseInsets provides WindowInsets(0.dp, 32.dp, 0.dp, 24.dp)) {
                RiseScreenFrame { content() }
            }
        }
    }

    @Composable
    private fun Editor() {
        val clip = Clip(uri = "file:///praia.mp4", name = "Praia", sourceDurationMs = 8000).trim(1200, 6400)
        Column(Modifier.fillMaxSize()) {
            EditorTopBar("Viagem", true, true, true, false, {}, {}, {}, {}, {})
            Box(
                Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                    .background(Brush.verticalGradient(listOf(Color(0xFF5AA0D8), Color(0xFFE9C58A))))
            )
            Box(Modifier.weight(1f))
            CutPanel(clip, 2000, CutTab.CUT, {}, CutActions(moveLeft = {}))
        }
    }

    @Composable
    private fun Dialog(value: ExportSettings, advanced: Boolean, tall: Boolean = false) =
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)), Alignment.Center) {
            ExportContent(project, value, listOf(24, 25, 30, 60), support, advanced, {}, {}, {}, {},
                maxBodyHeight = if (tall) 2000.dp else 520.dp)
        }

    @Test fun exportNormal() = phone {
        Dialog(ExportSettings(height = 1080, fps = 30, bitrate = 12_000_000, codec = VideoCodec.HEVC, bitDepth = 10), false)
    }

    @Test fun exportAdvanced() {
        // Taller canvas so the whole open "Avançado" section is visible in one image.
        p.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 3150))
        advancedShot()
    }

    private fun advancedShot() = phone {
        Dialog(
            ExportSettings(height = 2160, fps = 30, bitrate = 100_000_000, codec = VideoCodec.PRORES,
                bitDepth = 10, chroma = Chroma.YUV422),
            true, tall = true
        )
    }

    private val running = ExportBus.State(
        active = true, projectId = "p", progress = 0.62f, phase = "Codificando ProRes 422 HQ",
        startedAt = 1_000_000L, minimized = true
    )

    @Test fun bubbleOverEditor() = phone {
        Box(Modifier.fillMaxSize()) {
            Editor()
            RenderBubble(running, initialFromTop = 0.42f) {}
        }
    }

    @Test fun renderTab() = phone {
        Box(Modifier.fillMaxSize()) {
            Editor()
            // 62% after 93 s → ≈ 1 min left.
            RenderTab(running.copy(panel = true, minimized = false), 1_093_000L, {}, {}, {}, {})
        }
    }
}
