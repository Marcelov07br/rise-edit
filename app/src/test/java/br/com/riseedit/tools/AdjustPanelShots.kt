package br.com.riseedit.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import br.com.riseedit.RiseBlack
import br.com.riseedit.RiseTheme
import br.com.riseedit.RiseWhite
import br.com.riseedit.core.ColorAdjust
import org.junit.Rule
import org.junit.Test

private val sample = ColorAdjust(exposure = 0.7f, contrast = 0.25f, warmth = -0.4f, saturation = 0.3f, shadows = 0.5f)

class AdjustPanelShots {
    @get:Rule val p = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "android:Theme.Material.NoActionBar")

    @Test fun adjustPhone() = p.snapshot {
        RiseTheme {
            Column(Modifier.fillMaxSize().background(RiseBlack).padding(16.dp)) {
                Text("Ajustar", color = RiseWhite, fontSize = 18.sp, modifier = Modifier.padding(bottom = 16.dp))
                AdjustGrid(sample) {}
            }
        }
    }
}

class AdjustPanelTabletShots {
    @get:Rule val p = Paparazzi(deviceConfig = DeviceConfig.PIXEL_C, theme = "android:Theme.Material.NoActionBar")

    @Test fun adjustTablet() = p.snapshot {
        RiseTheme {
            Column(Modifier.fillMaxSize().background(RiseBlack).padding(24.dp)) { AdjustGrid(sample) {} }
        }
    }
}
