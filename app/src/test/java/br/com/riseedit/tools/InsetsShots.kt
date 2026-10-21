package br.com.riseedit.tools

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import br.com.riseedit.NewProjectForm
import br.com.riseedit.NewProjectScreen
import br.com.riseedit.RiseTheme
import br.com.riseedit.core.Aspect
import br.com.riseedit.core.FrameRateChoice
import br.com.riseedit.core.Resolution
import br.com.riseedit.core.TvStandard
import br.com.riseedit.designsystem.LocalRiseInsets
import br.com.riseedit.designsystem.RiseScreenFrame
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

/** Paparazzi has no system bars: fake a 32dp status bar (+cutout) and a 24dp gesture bar. */
class InsetsShots {
    @get:Rule
    val paparazzi =
        Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "android:Theme.Material.NoActionBar")

    private fun phone(content: @Composable () -> Unit) = paparazzi.snapshot {
        RiseTheme {
            CompositionLocalProvider(
                LocalRiseInsets provides WindowInsets(0.dp, 32.dp, 0.dp, 24.dp)
            ) {
                RiseScreenFrame { content() }
            }
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier.fillMaxWidth().height(
                        32.dp
                    ).background(Color(0x55FF3B30)).padding(horizontal = 16.dp)
                ) {
                    Text(
                        "19:42",
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                    Box(
                        Modifier.align(
                            Alignment.Center
                        ).width(
                            14.dp
                        ).height(14.dp).background(Color.Black, RoundedCornerShape(7.dp))
                    )
                }
                Box(
                    Modifier.align(
                        Alignment.BottomCenter
                    ).fillMaxWidth().height(24.dp).background(Color(0x55FF3B30))
                ) {
                    Box(
                        Modifier.align(Alignment.Center).width(108.dp).height(4.dp)
                            .background(Color.White, RoundedCornerShape(2.dp))
                    )
                }
            }
        }
    }

    @Test fun settings() = phone {
        SettingsScreen(AppSettings(), SettingsSection.LOOK, {}, {}, SettingsInfo("0.19.0", 31))
    }

    @Test fun createProject() = phone {
        NewProjectScreen(
            NewProjectForm(
                "Viagem",
                Aspect.WIDE,
                Resolution.FHD,
                TvStandard.NTSC,
                FrameRateChoice(30, true)
            ),
            {
            },
            {},
            {}
        )
    }

    @Test fun picker() = phone {
        val items = (1..18).map {
            GalleryItem(
                it.toLong(),
                Uri.parse("content://m/$it"),
                true,
                it * 9_000L,
                "v$it"
            )
        }
        MediaPickerScreen(GalleryTab.VIDEOS, items, listOf(items[2].uri), GalleryAccess.FULL, 3, {
        }, {}, {}, {}, {}) {
            Box(Modifier.fillMaxSize().background(Color(0xFF3A6EA5 + it.id * 0x0A0A00)))
        }
    }
}
