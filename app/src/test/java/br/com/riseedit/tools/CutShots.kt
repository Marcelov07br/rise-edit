package br.com.riseedit.tools

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import br.com.riseedit.timeline.SplitAtCursorButton
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

    /** Timeline stand-in (the real stage needs the view model): lanes + playhead + real button. */
    @Test fun timelineSplitButton() = p.snapshot {
        Screen("Timeline · dividir no cursor") {
            Box(Modifier.fillMaxWidth().height(150.dp).background(Color(0xFF101013))) {
                Column(
                    Modifier.padding(top = 24.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(
                        6.dp
                    )
                ) {
                    Row {
                        Box(
                            Modifier.width(
                                110.dp
                            ).height(
                                44.dp
                            ).clip(RoundedCornerShape(6.dp)).background(Color(0x553D7BFF))
                        )
                        Box(
                            Modifier.padding(
                                start = 2.dp
                            ).width(170.dp).height(44.dp).clip(RoundedCornerShape(6.dp))
                                .background(Color(0x553D7BFF)).background(Color.Transparent)
                        )
                    }
                    Box(
                        Modifier.offset(
                            x = 60.dp
                        ).width(
                            140.dp
                        ).height(30.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x5543C59E))
                    )
                }
                Box(Modifier.offset(x = 190.dp).width(2.dp).fillMaxSize().background(RiseWhite))
                with(androidx.compose.ui.platform.LocalDensity.current) {
                    SplitAtCursorButton(x = 191.dp.toPx(), visible = true) {}
                }
            }
        }
    }
}
