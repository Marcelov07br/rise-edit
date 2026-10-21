package br.com.riseedit.tools

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import br.com.riseedit.RiseBlack
import br.com.riseedit.RiseTheme
import br.com.riseedit.RiseWhite
import br.com.riseedit.color.ScopeKind
import br.com.riseedit.color.Scopes
import br.com.riseedit.color.ScopesBox
import br.com.riseedit.color.renderScope
import br.com.riseedit.core.ColorAdjust
import br.com.riseedit.core.ColorAdjustMath
import br.com.riseedit.core.ColorCurves
import br.com.riseedit.core.CurvePoint
import br.com.riseedit.core.color.FilterPreset
import br.com.riseedit.core.color.FilterRegistry
import org.junit.Rule
import org.junit.Test

private val look = ColorAdjust(exposure = 0.3f, contrast = 0.2f, warmth = 0.35f, saturation = 0.2f,
    curves = ColorCurves(master = listOf(CurvePoint(0f, 0.05f), CurvePoint(0.5f, 0.52f), CurvePoint(1f, 0.95f))))

/** Synthetic frame (sky / skin / foliage) graded by the CPU mirror. */
private fun frame(w: Int = 192, h: Int = 108): IntArray = IntArray(w * h) { i ->
    val u = (i % w) / (w - 1f); val v = (i / w) / (h - 1f)
    val (r, g, b) = when {
        v < 0.45f -> Triple(0.35f + 0.3f * v, 0.55f + 0.3f * v, 0.85f)
        v < 0.7f -> Triple(0.85f - 0.3f * u, 0.62f - 0.2f * u, 0.5f - 0.15f * u)
        else -> Triple(0.18f + 0.1f * u, 0.42f - 0.2f * (v - 0.7f), 0.16f)
    }
    val o = ColorAdjustMath.apply(look, floatArrayOf(r, g, b))
    fun c(f: Float) = (f.coerceIn(0f, 1f) * 255f).toInt()
    (0xFF shl 24) or (c(o[0]) shl 16) or (c(o[1]) shl 8) or c(o[2])
}

@Composable
private fun PreviewWithScope(kind: ScopeKind) = RiseTheme {
    Column(Modifier.fillMaxSize().background(RiseBlack)) {
        Text("Prévia · scopes", color = RiseWhite, modifier = Modifier.padding(12.dp))
        Box(Modifier.fillMaxWidth().height(330.dp).padding(horizontal = 12.dp)) {
            val px = frame()
            Image(android.graphics.Bitmap.createBitmap(px, 192, 108, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap(), null,
                Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Scopes.kind = kind
            ScopesBox(renderScope(kind, px, 192).asImageBitmap(), kind, Modifier.align(Alignment.TopEnd).padding(6.dp))
        }
        br.com.riseedit.MiniScrubBar(false, 12_400, 48_000, 30, true, {}, {}, marks = listOf(4_000, 12_400, 30_000))
        Box(Modifier.padding(16.dp)) {
            AdjustContent(look, AdjustTab.BASIC, {}, {}, AdjustActions(canPaste = true, savePreset = {}, exportCube = {}, scopes = {}), grade = {})
        }
    }
}

class ColorExtrasShots {
    @get:Rule val p = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "android:Theme.Material.NoActionBar")

    @Test fun scopesWaveform() = p.snapshot { PreviewWithScope(ScopeKind.WAVEFORM) }
    @Test fun scopesParade() = p.snapshot { PreviewWithScope(ScopeKind.PARADE) }
    @Test fun scopesVectorscope() = p.snapshot { PreviewWithScope(ScopeKind.VECTOR) }
    @Test fun scopesHistogram() = p.snapshot { PreviewWithScope(ScopeKind.HISTOGRAM) }

    private fun keys(here: Boolean) = ColorKeyUi({ here }, { it == AdjustTab.BASIC || it == AdjustTab.CURVES }, {}, {}, {})

    @Test fun keyframesBasicTab() = p.snapshot {
        RiseTheme {
            Column(Modifier.fillMaxSize().background(RiseBlack).padding(16.dp)) {
                br.com.riseedit.MiniScrubBar(false, 12_400, 48_000, 30, true, {}, {}, marks = listOf(4_000, 12_400, 30_000))
                AdjustContent(look, AdjustTab.BASIC, {}, {}, AdjustActions(canPaste = true, savePreset = {}, exportCube = {}, scopes = {}), grade = {}, keys = keys(true))
            }
        }
    }

    @Test fun keyframesCurvesTab() = p.snapshot {
        RiseTheme {
            Column(Modifier.fillMaxSize().background(RiseBlack).padding(16.dp)) {
                br.com.riseedit.MiniScrubBar(false, 20_000, 48_000, 30, true, {}, {}, marks = listOf(4_000, 12_400, 30_000))
                AdjustContent(look, AdjustTab.CURVES, {}, {}, AdjustActions(canPaste = true, savePreset = {}, exportCube = {}, scopes = {}), grade = {}, keys = keys(false))
            }
        }
    }

    @Test fun savePreset() = p.snapshot {
        RiseTheme { Box(Modifier.fillMaxSize().background(RiseBlack)) { SavePresetDialog(look.copy(hsl = look.hsl.with(1, 1, 0.3f)), "Pôr do sol quente", {}, { _, _ -> }) } }
    }

    @Test fun filtersMine() = p.snapshot {
        RiseTheme {
            Column(Modifier.fillMaxSize().background(RiseBlack).padding(16.dp)) {
                val mine = listOf(FilterPreset(USER + "a", "Pôr do sol", look, MINE), FilterPreset(USER + "b", "Noite azul", ColorAdjust(warmth = -0.5f, exposure = -0.3f), MINE))
                val all = FilterRegistry.all + mine
                val thumbs = sampleThumbnails(all) { if (it.id.startsWith(USER)) it.adjust else it.adjust }
                FiltersContent(all, USER + "a", 1f, { thumbs[it.id] }, {}, {}, initialCategory = MINE)
            }
        }
    }

    @Test fun exportCube() = p.snapshot {
        RiseTheme { Box(Modifier.fillMaxSize().background(RiseBlack)) { ExportCubeContent(33, {}, true, {}, {}) } }
    }
}
