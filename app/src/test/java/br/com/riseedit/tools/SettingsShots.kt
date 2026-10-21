package br.com.riseedit.tools

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import br.com.riseedit.NewProjectForm
import br.com.riseedit.NewProjectScreen
import br.com.riseedit.RiseTheme
import br.com.riseedit.core.Aspect
import br.com.riseedit.core.FrameRateChoice
import br.com.riseedit.core.Resolution
import br.com.riseedit.core.TvStandard
import br.com.riseedit.picker.GalleryAccess
import br.com.riseedit.picker.GalleryItem
import br.com.riseedit.picker.GalleryTab
import br.com.riseedit.picker.MediaPickerScreen
import br.com.riseedit.settings.AppSettings
import br.com.riseedit.settings.SettingsInfo
import br.com.riseedit.settings.SettingsScreen
import br.com.riseedit.settings.SettingsSection
import org.junit.Rule
import org.junit.Test

class SettingsShots {
    @get:Rule
    val paparazzi =
        Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "android:Theme.Material.NoActionBar")

    private fun section(s: SettingsSection) = paparazzi.snapshot {
        RiseTheme {
            SettingsScreen(
                AppSettings(exportFolder = "content://x/tree/primary%3AMovies%2FRise"),
                s,
                {},
                {},
                SettingsInfo("0.19.0", 31, 734_003_200, "Movies/Rise")
            )
        }
    }

    @Test fun settings1Updates() = section(SettingsSection.UPDATES)

    @Test fun settings2Look() = section(SettingsSection.LOOK)

    @Test fun settings3Project() = section(SettingsSection.PROJECT)

    @Test fun settings4Export() = section(SettingsSection.EXPORT)

    @Test fun settings5Preview() = section(SettingsSection.PREVIEW)

    @Test fun settings6Timeline() = section(SettingsSection.TIMELINE)

    @Test fun settings7About() = section(SettingsSection.ABOUT)

    private fun create(standard: TvStandard, rate: FrameRateChoice, aspect: Aspect) =
        paparazzi.snapshot {
            RiseTheme {
                NewProjectScreen(
                    NewProjectForm("Viagem", aspect, Resolution.UHD, standard, rate),
                    {},
                    {},
                    {},
                    expandedFps = true
                )
            }
        }

    @Test fun create1Ntsc() = create(TvStandard.NTSC, FrameRateChoice(24, true), Aspect.WIDE)

    @Test fun create2Pal() = create(TvStandard.PAL, FrameRateChoice(25, false), Aspect.VERTICAL)

    private val colors =
        listOf(0xFF3A6EA5, 0xFFE07A5F, 0xFF81B29A, 0xFFF2CC8F, 0xFF6D597A, 0xFF2A9D8F)

    private fun picker(tab: GalleryTab, access: GalleryAccess) = paparazzi.snapshot {
        val video = tab == GalleryTab.VIDEOS
        val items = (1..15).map {
            GalleryItem(
                it.toLong(),
                Uri.parse("content://media/$it"),
                video,
                it * 7_300L + 2_000,
                "m$it"
            )
        }
        RiseTheme {
            MediaPickerScreen(
                tab,
                items,
                listOf(items[4].uri, items[0].uri, items[7].uri),
                access,
                3,
                {},
                {},
                {},
                {},
                {}
            ) { item ->
                val c = Color(colors[(item.id % colors.size).toInt()])
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(c, c.copy(alpha = 0.4f), Color.Black))
                    )
                )
            }
        }
    }

    @Test fun picker1Videos() = picker(GalleryTab.VIDEOS, GalleryAccess.FULL)

    @Test fun picker2Images() = picker(GalleryTab.IMAGES, GalleryAccess.PARTIAL)
}
