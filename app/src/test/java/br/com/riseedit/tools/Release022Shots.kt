package br.com.riseedit.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.FilterVintage
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import br.com.riseedit.EditorState
import br.com.riseedit.EditorTopBar
import br.com.riseedit.EmptyInspector
import br.com.riseedit.ExpandedEditorLayout
import br.com.riseedit.RiseMuted
import br.com.riseedit.RiseSurface
import br.com.riseedit.RiseTheme
import br.com.riseedit.SideToolColumn
import br.com.riseedit.ToolbarItem
import br.com.riseedit.core.Clip
import br.com.riseedit.core.Project
import br.com.riseedit.designsystem.LocalRiseInsets
import br.com.riseedit.designsystem.RiseScreenFrame
import br.com.riseedit.keys.AppKeymap
import br.com.riseedit.settings.AppSettings
import br.com.riseedit.settings.SettingsInfo
import br.com.riseedit.settings.SettingsScreen
import br.com.riseedit.settings.SettingsSection
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import java.io.File
import org.junit.Rule
import org.junit.Test

/** 0.22: DeX / tablet (Expanded) editor and the keyboard shortcuts screen. */
class Release022Shots {
    /** Samsung DeX on a 1080p monitor: 1920×1080 px at 240 dpi → 1280×720 dp (Expanded). */
    private val dex = DeviceConfig.PIXEL_C.copy(
        screenWidth = 1920,
        screenHeight = 1080,
        xdpi = 240,
        ydpi = 240,
        density = Density.HIGH,
        orientation = ScreenOrientation.LANDSCAPE
    )

    @get:Rule
    val p = Paparazzi(deviceConfig = dex, theme = "android:Theme.Material.NoActionBar")

    private fun shot(content: @Composable () -> Unit) = p.snapshot {
        RiseTheme {
            CompositionLocalProvider(LocalRiseInsets provides WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)) {
                RiseScreenFrame { content() }
            }
        }
    }

    private val keymap = AppKeymap.fromText(File("src/main/assets/keymaps/premiere-marce.kys").readText())

    @Test fun dexEditor() = shot {
        val clips = listOf(
            Clip(uri = "file:///praia.mp4", name = "Praia", sourceDurationMs = 8000),
            Clip(uri = "file:///trilha.mp4", name = "Trilha", sourceDurationMs = 6000),
            Clip(uri = "file:///por-do-sol.mp4", name = "Pôr do sol", sourceDurationMs = 9000)
        )
        val state = EditorState(project = Project(name = "Viagem", clips = clips), selected = 1)
        val items = listOf(
            ToolbarItem("Cortar", Icons.Outlined.ContentCut) {},
            ToolbarItem("Velocidade", Icons.Outlined.Speed) {},
            ToolbarItem("Áudio", Icons.Outlined.Audiotrack) {},
            ToolbarItem("Texto", Icons.Outlined.TextFields) {},
            ToolbarItem("Sobrepor", Icons.Outlined.Layers) {},
            ToolbarItem("Ajustes", Icons.Outlined.Tune, active = true) {},
            ToolbarItem("Filtros", Icons.Outlined.FilterVintage) {}
        )
        Column(Modifier.fillMaxSize()) {
            EditorTopBar("Viagem", true, true, true, false, {}, {}, {}, {}, {})
            ExpandedEditorLayout(
                tools = { SideToolColumn(items) },
                preview = { m ->
                    Box(m.padding(start = 4.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        Box(
                            Modifier.fillMaxHeight().width(500.dp).background(
                                Brush.verticalGradient(listOf(Color(0xFF5AA0D8), Color(0xFFE9C58A))),
                                RoundedCornerShape(12.dp)
                            )
                        )
                    }
                },
                inspector = { EmptyInspector(state) },
                transport = {
                    Text(
                        "00:00:07:12 / 00:00:23:00",
                        color = RiseMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                    )
                },
                timeline = { MockTimeline() }
            )
        }
    }

    /** Lanes of the taller Expanded timeline (track area 260 dp ≈ 6 lanes vs 3 on the phone). */
    @Composable
    private fun MockTimeline() {
        val lanes = listOf(
            "Texto" to listOf(Triple(180, 160, Color(0xFFB388FF))),
            "Sobrep." to listOf(Triple(420, 220, Color(0xFF4FC3F7))),
            "Vídeo" to listOf(
                Triple(0, 300, Color(0xFF6B7A8F)),
                Triple(302, 220, Color(0xFFFFFFFF)),
                Triple(524, 330, Color(0xFF6B7A8F))
            ),
            "Áudio 1" to listOf(Triple(0, 854, Color(0xFF66BB6A))),
            "Áudio 2" to listOf(Triple(300, 260, Color(0xFF26A69A))),
            "Ajuste" to listOf(Triple(520, 200, Color(0xFFFFB74D)))
        )
        Column(Modifier.fillMaxWidth().height(272.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.height(18.dp))
            lanes.forEach { (name, items) ->
                Row(Modifier.fillMaxWidth().height(38.dp)) {
                    Text(name, color = RiseMuted, fontSize = 11.sp, modifier = Modifier.width(64.dp).padding(top = 12.dp))
                    Box(Modifier.weight(1f).fillMaxHeight().background(RiseSurface, RoundedCornerShape(8.dp))) {
                        items.forEach { (x, w, c) ->
                            Box(
                                Modifier.offset(x.dp).width(w.dp).fillMaxHeight().padding(vertical = 3.dp)
                                    .background(c.copy(alpha = if (c == Color.White) 0.9f else 0.55f), RoundedCornerShape(6.dp))
                            )
                        }
                    }
                }
            }
        }
    }

    @Test fun dexShortcuts() = shot {
        SettingsScreen(AppSettings(), SettingsSection.SHORTCUTS, {}, {}, SettingsInfo("0.22.0", 37, keymap = keymap))
    }

    @Test fun shortcutsPhone() {
        p.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_5)
        shot {
            SettingsScreen(AppSettings(), SettingsSection.SHORTCUTS, {}, {}, SettingsInfo("0.22.0", 37, keymap = keymap))
        }
    }
}
