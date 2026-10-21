package br.com.riseedit.tools

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import br.com.riseedit.RiseBlack
import br.com.riseedit.RiseSurfaceHigh
import br.com.riseedit.RiseTheme
import br.com.riseedit.RiseWhite
import br.com.riseedit.core.Clip
import br.com.riseedit.core.editing.ClipEdits
import br.com.riseedit.designsystem.ActionMenuItems
import br.com.riseedit.designsystem.ActionOverflow
import br.com.riseedit.designsystem.GraphicSurface
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@Composable
private fun Screen(title: String, content: @Composable () -> Unit) = RiseTheme {
    Column(Modifier.fillMaxSize().background(RiseBlack).padding(16.dp)) {
        Text(
            title,
            color = RiseWhite,
            fontSize = 18.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        content()
    }
}

/** Fake film frames (sky → sand gradient with a moving "sun") so the strip reads as video. */
private fun frame(ms: Long): ImageBitmap {
    val b = Bitmap.createBitmap(128, 72, Bitmap.Config.ARGB_8888)
    val c = Canvas(b)
    val t = (ms % 8000) / 8000f
    c.drawColor(android.graphics.Color.rgb((40 + 120 * t).toInt(), 90, (170 - 80 * t).toInt()))
    val p = Paint().apply { color = android.graphics.Color.rgb(230, 190, 120) }
    c.drawRect(0f, 48f, 128f, 72f, p)
    p.color = android.graphics.Color.rgb(255, 230, 150)
    c.drawCircle(20f + 90f * t, 26f, 10f, p)
    return b.asImageBitmap()
}

private val clip = Clip(
    uri = "file:///praia.mp4",
    name = "Praia",
    sourceDurationMs = 8000
).trim(1200, 6400)

class CutShots {
    @get:Rule val p =
        Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "android:Theme.Material.NoActionBar")

    @Test fun overflowKeepsSplitAndDuplicateInline() {
        val list = cutActionList(clip, CutTab.CUT, true, true, CutActions())
        val (inline, menu) = ActionOverflow.split(list, 4)
        assertEquals(listOf("Dividir no cursor", "Duplicar"), inline.map { it.name })
        assertEquals("Excluir clipe", menu.last().name)
    }

    @Test fun cutPanelTabs() = p.snapshot {
        Screen("Corte · abas + ações") {
            CutPanel(clip, 2000, CutTab.CUT, {}, CutActions(moveLeft = {}), cached = ::frame)
        }
    }

    @Test fun speedTab() = p.snapshot {
        Screen("Corte · Velocidade") {
            CutPanel(ClipEdits.speed(clip, 2.0, keepPitch = false), 0, CutTab.SPEED, {
            }, CutActions())
        }
    }

    @Test fun fadeTab() = p.snapshot {
        Screen("Corte · Fade") {
            CutPanel(clip.copy(fadeMs = 500), 0, CutTab.FADE, {}, CutActions())
        }
    }

    @Test fun trimStrip() = p.snapshot {
        Screen("Tira de miniaturas") {
            GraphicSurface {
                TrimStrip(clip, clip.startMs..clip.endMs, 3600, true, {}, {}, { null }, ::frame)
            }
        }
    }

    @Test fun moreMenu() = p.snapshot {
        Screen("Corte · menu 'mais'") {
            val list =
                cutActionList(
                    clip,
                    CutTab.CUT,
                    true,
                    true,
                    CutActions(moveLeft = {
                    }, moveRight = {})
                )
            Box(
                Modifier.width(300.dp).clip(RoundedCornerShape(16.dp)).background(RiseSurfaceHigh)
            ) {
                Column { ActionMenuItems(ActionOverflow.split(list, 4).second) }
            }
        }
    }

    @Test fun toolbarSplit() = p.snapshot {
        Screen("Barra principal · Dividir na agulha") {
            Column(
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
            ) {
                br.com.riseedit.BottomToolbar(
                    listOf(
                        br.com.riseedit.ToolbarItem(
                            "Dividir",
                            androidx.compose.material.icons.Icons.Rounded.ContentCut
                        ) {
                        },
                        br.com.riseedit.ToolbarItem(
                            "Vídeo",
                            androidx.compose.material.icons.Icons.Outlined.Add
                        ) {
                        },
                        br.com.riseedit.ToolbarItem(
                            "Texto",
                            androidx.compose.material.icons.Icons.Outlined.TextFields
                        ) {
                        },
                        br.com.riseedit.ToolbarItem(
                            "Áudio",
                            androidx.compose.material.icons.Icons.Outlined.MusicNote
                        ) {
                        }
                    )
                )
                Text("Agulha a < 1 quadro da borda:", color = RiseWhite, fontSize = 12.sp)
                br.com.riseedit.BottomToolbar(
                    listOf(
                        br.com.riseedit.ToolbarItem(
                            "Dividir",
                            androidx.compose.material.icons.Icons.Rounded.ContentCut,
                            false
                        ) {
                        },
                        br.com.riseedit.ToolbarItem(
                            "Vídeo",
                            androidx.compose.material.icons.Icons.Outlined.Add
                        ) {
                        }
                    )
                )
            }
        }
    }

    /** Same layout as TimelineClip.ClipBackground: tiles anchored to source time, cut at edges. */
    @Composable
    private fun Strip(c: Clip, pps: Double) {
        val d = androidx.compose.ui.platform.LocalDensity.current.density
        val w = br.com.riseedit.core.timeline.FilmStrip.outputAt(c, c.endMs) * pps / 1000
        Box(
            Modifier.padding(
                start = 8.dp
            ).width((w / d).dp).height(44.dp).clip(RoundedCornerShape(6.dp))
                .background(Color(0x553D7BFF))
        ) {
            br.com.riseedit.core.timeline.FilmStrip.tiles(c, pps, 40.0 * d).forEach { t ->
                androidx.compose.foundation.Image(
                    frame(t.sourceMs),
                    null,
                    Modifier.offset(x = (t.xPx / d).dp).width((t.widthPx / d).dp).height(44.dp),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            }
        }
    }

    @Test fun timelineFilmStrip() = p.snapshot {
        Screen("Timeline · miniaturas pelo tempo de fonte") {
            val base = Clip(uri = "file:///praia.mp4", name = "Praia", sourceDurationMs = 8000)
            Column(
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
            ) {
                Text("inteiro (8 s)", color = RiseWhite, fontSize = 12.sp)
                Strip(base, 40.0 * 2.75)
                Text(
                    "início cortado em 2,5 s (mesmos quadros, borda corta o tile)",
                    color = RiseWhite,
                    fontSize = 12.sp
                )
                Strip(base.trim(2500, 8000), 40.0 * 2.75)
                Text("2× (tiles = 2× mais fonte)", color = RiseWhite, fontSize = 12.sp)
                Strip(ClipEdits.speed(base, 2.0), 40.0 * 2.75)
            }
        }
    }
}
