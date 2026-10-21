package br.com.riseedit.tools

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import br.com.riseedit.RiseBlack
import br.com.riseedit.RiseSurfaceHigh
import br.com.riseedit.RiseTheme
import br.com.riseedit.RiseWhite
import br.com.riseedit.core.Clip
import br.com.riseedit.core.ColorAdjust
import br.com.riseedit.core.color.Qualifier
import br.com.riseedit.core.color.Vignette
import br.com.riseedit.designsystem.ActionMenuItems
import br.com.riseedit.designsystem.ActionOverflow
import br.com.riseedit.designsystem.PanelAction
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

private val keys = ColorKeyUi({ false }, { false }, {})
private val all =
    AdjustActions(canPaste = true, savePreset = {}, exportCube = {}, scopes = {}, match = {})
private val q = Qualifier.pick(
    Qualifier(),
    floatArrayOf(0.85f, 0.45f, 0.2f)
).copy(saturation = -0.4f, hueShift = 0.15f, lightness = 0.1f)

private fun menuActions() = listOf(
    PanelAction(Icons.Rounded.Insights, "Mostrar scopes", primary = true) {},
    PanelAction(Icons.Rounded.ContentCopy, "Copiar ajustes") {},
    PanelAction(Icons.Rounded.ContentPaste, "Colar ajustes") {},
    PanelAction(Icons.Rounded.LibraryAdd, "Aplicar a todos os clipes") {},
    PanelAction(Icons.Rounded.AutoFixHigh, "Combinar cor com outro clipe") {},
    PanelAction(Icons.Rounded.BookmarkAdd, "Salvar como preset") {},
    PanelAction(Icons.Rounded.FileDownload, "Exportar LUT .cube") {},
    PanelAction(Icons.Rounded.RestartAlt, "Resetar todos", danger = true) {}
)

class LocalColorShots {
    @get:Rule val p =
        Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "android:Theme.Material.NoActionBar")

    @Test fun overflowSplit() {
        val (inline, menu) = ActionOverflow.split(menuActions(), 4)
        assertEquals(listOf("Mostrar scopes"), inline.map { it.name })
        assertEquals("Resetar todos", menu.last().name)
        assertEquals(8, ActionOverflow.split(menuActions(), 20).first.size)
    }

    @Test fun actionBarPhone() = p.snapshot {
        Screen("Ajustar · barra com menu 'mais'") {
            AdjustContent(ColorAdjust(exposure = 0.3f), AdjustTab.BASIC, {
            }, {}, all, keys = keys, compare = {
                br.com.riseedit.designsystem.PanelIconButton(Icons.Rounded.Insights, "Comparar") {}
            }, grade = {})
        }
    }

    @Test fun actionMenuOpen() = p.snapshot {
        Screen("Menu 'mais' aberto") {
            Box(
                Modifier.width(280.dp).clip(RoundedCornerShape(16.dp)).background(RiseSurfaceHigh)
            ) {
                Column { ActionMenuItems(ActionOverflow.split(menuActions(), 4).second) }
            }
        }
    }

    @Test fun qualifierTab() = p.snapshot {
        Screen("Ajustar · Seleção (qualifier)") {
            AdjustContent(ColorAdjust(qualifier = q), AdjustTab.QUALIFIER, {
            }, {}, all, keys = keys, grade = {}, local = LocalColorUi(showMask = true))
        }
    }

    @Test fun qualifierEmpty() = p.snapshot {
        Screen("Ajustar · Seleção (sem cor)") {
            AdjustContent(ColorAdjust(), AdjustTab.QUALIFIER, {
            }, {}, all, keys = keys, grade = {}, local = LocalColorUi(picking = true))
        }
    }

    @Test fun vignetteTab() = p.snapshot {
        Screen("Ajustar · Vinheta") {
            AdjustContent(
                ColorAdjust(
                    vignette = Vignette(-0.6f, 0.45f, 0.6f, 0.3f, 0.4f, 0.45f)
                ),
                AdjustTab.VIGNETTE,
                {
                },
                {},
                all,
                keys = keys,
                grade = {}
            )
        }
    }

    @Test fun colorMatch() = p.snapshot {
        val clips = (1..5).map {
            Clip(uri = "file:///$it.mp4", name = "Clipe $it", sourceDurationMs = 4000)
        }
        val thumbs = clips.mapIndexed { i, c ->
            c.id to Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888).apply {
                eraseColor(
                    android.graphics.Color.HSVToColor(
                        floatArrayOf(
                            i * 50f + 20f,
                            0.5f,
                            0.4f + i * 0.1f
                        )
                    )
                )
            }
        }.toMap()
        Screen("Combinar cor") {
            ColorMatchContent(clips, thumbs, 0, setOf(2, 3), 0.8f, false, {}, {}, {})
        }
    }
}
