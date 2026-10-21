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
import br.com.riseedit.RiseSurface
import br.com.riseedit.RiseTheme
import br.com.riseedit.RiseWhite
import br.com.riseedit.core.color.FilterRegistry
import org.junit.Rule
import org.junit.Test

class FiltersPanelShots {
    @get:Rule val p =
        Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "android:Theme.Material.NoActionBar")
    private val presets = FilterRegistry.all

    @Composable
    private fun Screen(selected: String?, intensity: Float) = RiseTheme {
        val thumbs = androidx.compose.runtime.remember { sampleThumbnails(presets) }
        Column(Modifier.fillMaxSize().background(RiseBlack).padding(top = 360.dp)) {
            Column(Modifier.padding(8.dp).background(RiseSurface).padding(16.dp)) {
                Text(
                    "Filtros",
                    color = RiseWhite,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                FiltersContent(presets, selected, intensity, { thumbs[it.id] }, {}, {})
            }
        }
    }

    @Test fun filtersNone() = p.snapshot { Screen("none", 1f) }

    @Test fun filtersSelected() = p.snapshot { Screen("cinema", 0.7f) }

    @Test fun filtersCustom() = p.snapshot { Screen(null, 1f) }
}
