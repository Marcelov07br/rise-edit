package br.com.riseedit.tools

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import br.com.riseedit.EditorTopBar
import br.com.riseedit.HomeScreen
import br.com.riseedit.RiseTheme
import br.com.riseedit.core.Clip
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

/** 0.19.1: dark-only palette, phone with a fake status bar (32dp) and gesture bar (24dp). */
class Release0191Shots {
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
                        "20:05",
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.CenterStart)
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

    @Test fun home() = phone {
        HomeScreen(emptyList(), null, br.com.riseedit.core.library.ProjectLibraries(), "padrao", br.com.riseedit.HomeActions())
    }

    @Test fun pickerGestureBar() = phone {
        val items = (1..21).map {
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
            val c = Color(0xFF2E5A88 + (it.id % 6) * 0x0B0904)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(c, Color.Black))))
        }
    }

    @Test fun settingsLook() = phone {
        SettingsScreen(AppSettings(), SettingsSection.LOOK, {}, {}, SettingsInfo("0.19.1", 32))
    }

    @Test fun editor() = phone {
        val clip = Clip(
            uri = "file:///praia.mp4",
            name = "Praia",
            sourceDurationMs = 8000
        ).trim(1200, 6400)
        Column(Modifier.fillMaxSize()) {
            EditorTopBar("Viagem", true, true, true, false, {}, {}, {}, {}, {})
            Box(
                Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(
                    Brush.verticalGradient(listOf(Color(0xFF5AA0D8), Color(0xFFE9C58A)))
                )
            )
            Box(Modifier.weight(1f))
            CutPanel(clip, 2000, CutTab.CUT, {}, CutActions(moveLeft = {}))
        }
    }
}
