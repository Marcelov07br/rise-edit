package br.com.riseedit.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import br.com.riseedit.RiseBlack
import br.com.riseedit.RiseTheme
import br.com.riseedit.RiseWhite
import br.com.riseedit.core.AdjustmentLayer
import br.com.riseedit.core.ColorAdjust
import br.com.riseedit.core.color.Vignette
import org.junit.Rule
import org.junit.Test

@Composable
private fun Screen(title: String, content: @Composable () -> Unit) = RiseTheme {
    Column(Modifier.fillMaxSize().background(RiseBlack).padding(16.dp)) {
        Text(title, color = RiseWhite, fontSize = 18.sp, modifier = Modifier.padding(bottom = 16.dp))
        content()
    }
}

private val keys = ColorKeyUi({ false }, { it == AdjustTab.VIGNETTE }, {})
// Layer: no "aplicar a todos" / "combinar cor" (clip-only actions).
private val layerActions = AdjustActions(canPaste = true, applyAll = null, savePreset = {}, exportCube = {}, scopes = {})

class AdjustmentLayerShots {
    @get:Rule val p = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "android:Theme.Material.NoActionBar")

    private val layer = AdjustmentLayer(
        startMs = 2000, durationMs = 4500, name = "Ajuste 1", opacity = 0.8f,
        adjust = ColorAdjust(contrast = 0.2f, saturation = -0.3f, warmth = 0.25f, vignette = Vignette(-0.5f))
    )

    @Test fun layerBasic() = p.snapshot {
        Screen("Ajustar · camada de ajuste") {
            LayerHeader(layer) {}
            AdjustContent(layer.adjust, AdjustTab.BASIC, {}, {}, layerActions, keys = keys, grade = {})
        }
    }

    @Test fun layerVignette() = p.snapshot {
        Screen("Camada de ajuste · Vinheta") {
            LayerHeader(layer) {}
            AdjustContent(layer.adjust, AdjustTab.VIGNETTE, {}, {}, layerActions, keys = keys, grade = {})
        }
    }
}
