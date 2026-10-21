package br.com.riseedit.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import br.com.riseedit.AttributeDialog
import br.com.riseedit.RiseBlack
import br.com.riseedit.RiseTheme
import br.com.riseedit.RiseWhite
import br.com.riseedit.core.ColorAdjust
import br.com.riseedit.core.ColorCurves
import br.com.riseedit.core.ColorWheels
import br.com.riseedit.core.CurvePoint
import br.com.riseedit.core.HslBands
import br.com.riseedit.core.Wheel
import br.com.riseedit.core.color.ColorGrade
import br.com.riseedit.core.color.LogProfile
import br.com.riseedit.core.editing.AttributeGroup
import org.junit.Rule
import org.junit.Test

private val sample = ColorAdjust(
    exposure = 0.7f, contrast = 0.25f, warmth = -0.4f, saturation = 0.3f, shadows = 0.5f,
    curves = ColorCurves(
        master = listOf(CurvePoint(0f, 0.04f), CurvePoint(0.28f, 0.22f), CurvePoint(0.72f, 0.8f), CurvePoint(1f, 0.97f))
    ),
    hsl = HslBands().with(1, 0, 0.3f).with(1, 1, -0.25f).with(1, 2, 0.4f),
    wheels = ColorWheels(lift = Wheel(-0.3f, -0.4f, -0.1f), gamma = Wheel(0.1f, 0.2f, 0.05f), gain = Wheel(0.35f, 0.1f, 0.15f))
)

@Composable
private fun Screen(title: String, content: @Composable () -> Unit) = RiseTheme {
    Column(Modifier.fillMaxSize().background(RiseBlack).padding(16.dp)) {
        Text(title, color = RiseWhite, fontSize = 18.sp, modifier = Modifier.padding(bottom = 16.dp))
        content()
    }
}

private val actions = AdjustActions(canPaste = true)

class AdjustPanelShots {
    @get:Rule val p = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "android:Theme.Material.NoActionBar")

    @Test fun adjustPhone() = p.snapshot { Screen("Ajustar") { AdjustContent(sample, AdjustTab.BASIC, {}, {}, actions, grade = {}) } }
    @Test fun curves() = p.snapshot { Screen("Ajustar · Curvas") { AdjustContent(sample, AdjustTab.CURVES, {}, {}, actions, grade = {}) } }
    @Test fun hsl() = p.snapshot { Screen("Ajustar · HSL") { AdjustContent(sample, AdjustTab.HSL, {}, {}, actions, band = 1, grade = {}) } }
    @Test fun wheels() = p.snapshot { Screen("Ajustar · Rodas") { AdjustContent(sample, AdjustTab.WHEELS, {}, {}, actions, grade = {}) } }

    private val grade = ColorGrade(log = LogProfile.SLOG3, lutUri = "file:///a.cube", lutName = "Kodak 2383")
    @Test fun lutLog() = p.snapshot {
        Screen("Ajustar · LUT/log") {
            AdjustContent(sample, AdjustTab.GRADE, {}, {}, actions, grade = {
                GradeControls(grade, listOf("file:///a.cube" to "Kodak 2383"), {}, {})
            })
        }
    }
    @Test fun phoneDark() = p.snapshot {
        Screen("Ajustar · celular escuro") {
            AdjustContent(sample.copy(curves = ColorCurves()), AdjustTab.CURVES, {}, {}, actions, curveChannel = 1, grade = {})
        }
    }

    @Test fun copyDialog() = p.snapshot {
        Screen("") {
            val shown = listOf(AttributeGroup.COLOR_BASIC, AttributeGroup.COLOR_CURVES, AttributeGroup.COLOR_HSL,
                AttributeGroup.COLOR_WHEELS, AttributeGroup.LUT, AttributeGroup.GRADE_ORDER, AttributeGroup.VOLUME)
            AttributeSelectDialog(AttributeDialog(false, shown, shown.toSet() - AttributeGroup.VOLUME), {}, { _, _ -> })
        }
    }

    @Test fun pasteDialog() = p.snapshot {
        Screen("") {
            val shown = listOf(AttributeGroup.COLOR_BASIC, AttributeGroup.COLOR_CURVES, AttributeGroup.LUT)
            AttributeSelectDialog(AttributeDialog(true, shown, setOf(AttributeGroup.COLOR_CURVES, AttributeGroup.LUT)), {}, { _, _ -> })
        }
    }

    @Test fun lutDropdown() = p.snapshot {
        Screen("Correção de cor") {
            Box {
                GradeControls(
                    ColorGrade(log = LogProfile.SLOG3, lutUri = "file:///a.cube", lutName = "Kodak 2383"),
                    listOf("file:///a.cube" to "Kodak 2383", "file:///b.cube" to "Teal & Orange"),
                    {}, {}, lutMenuOpen = true
                )
            }
        }
    }
}

class AdjustPanelTabletShots {
    @get:Rule val p = Paparazzi(deviceConfig = DeviceConfig.PIXEL_C, theme = "android:Theme.Material.NoActionBar")

    @Test fun adjustTablet() = p.snapshot {
        RiseTheme {
            Column(Modifier.fillMaxSize().background(RiseBlack).padding(24.dp)) {
                AdjustContent(sample, AdjustTab.WHEELS, {}, {}, actions, grade = {})
            }
        }
    }
}
