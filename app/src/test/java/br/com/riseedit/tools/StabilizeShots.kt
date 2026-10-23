package br.com.riseedit.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import br.com.riseedit.EditorState
import br.com.riseedit.RiseTheme
import br.com.riseedit.core.Clip
import br.com.riseedit.core.Project
import br.com.riseedit.core.Stabilization
import br.com.riseedit.core.StabilizeMethod
import br.com.riseedit.designsystem.RiseScreenFrame
import org.junit.Rule
import org.junit.Test

/** 0.20.0: stabilization zone (Estabilidade / Distorção / Análise). */
class StabilizeShots {
    @get:Rule
    val paparazzi =
        Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "android:Theme.Material.NoActionBar")

    private fun state(analyzed: Boolean, s: Stabilization): EditorState {
        val clip = Clip(uri = "file:///praia.mp4", name = "Praia", sourceDurationMs = 8000)
            .copy(stabilization = s)
        return EditorState(
            project = Project(clips = listOf(clip)),
            analyzed = if (analyzed) setOf(clip.uri) else emptySet()
        )
    }

    private fun shot(
        state: EditorState,
        tab: StabilizeTab = StabilizeTab.SMOOTH,
        open: Boolean = false
    ) = paparazzi.snapshot {
        RiseTheme {
            RiseScreenFrame {
                Column(Modifier.fillMaxSize()) {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(
                            Brush.verticalGradient(listOf(Color(0xFF5AA0D8), Color(0xFFE9C58A)))
                        )
                    )
                    Column(Modifier.padding(16.dp)) {
                        StabilizeContent(state, StabilizeActions(hasClipboard = true), tab, open)
                    }
                }
            }
        }
    }

    @Test fun notAnalyzed() = shot(state(false, Stabilization()))

    @Test fun smooth() = shot(state(true, Stabilization(enabled = true)))

    @Test fun presets() = shot(state(true, Stabilization(enabled = true)), open = true)

    @Test fun warp() = shot(
        state(true, Stabilization(enabled = true, method = StabilizeMethod.WARP)),
        StabilizeTab.WARP
    )

    @Test fun diag() = shot(state(true, Stabilization(enabled = true)), StabilizeTab.DIAG)
}
