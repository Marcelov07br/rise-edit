package br.com.riseedit.tools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.riseedit.RiseMuted
import br.com.riseedit.core.ColorAdjust
import br.com.riseedit.core.color.Qualifier
import br.com.riseedit.core.color.Vignette
import br.com.riseedit.designsystem.GraphicSurface
import br.com.riseedit.designsystem.PanelIconButton
import br.com.riseedit.designsystem.TileSlider

/**
 * App-wide state of the on-preview color tools (the Ajustar panel sets it, the preview reads it):
 * the qualifier eyedropper and the draggable vignette center.
 */
internal object ColorTools {
    /** Waiting for a tap on the preview: receives the ORIGINAL frame color (ARGB). */
    var eyedropper by mutableStateOf<((Int) -> Unit)?>(null)
    /** Vignette center (0..1, y down) shown as a handle on the preview while the tab is open. */
    var vignetteCenter by mutableStateOf<Offset?>(null)
    var onVignetteCenter: ((Float, Float) -> Unit)? = null
    /** Qualifier "ver máscara" (mirrors ColorLive.showMask for Compose). */
    var showMask by mutableStateOf(false)

    fun setMask(on: Boolean, player: androidx.media3.common.Player?) {
        showMask = on
        br.com.riseedit.color.ColorLive.showMask = on
        android.util.Log.i("RiseColor", "qualifier mask view=$on")
        br.com.riseedit.redrawColor(player)
    }
}

/** UI hooks of the Seleção/Vinheta tabs (stateless; Paparazzi passes defaults). */
internal class LocalColorUi(
    val picking: Boolean = false,
    val onEyedropper: () -> Unit = {},
    val showMask: Boolean = false,
    val onMask: () -> Unit = {}
)

private fun hueColor(h: Float) = Color.hsv(((h % 360f) + 360f) % 360f, 0.8f, 0.95f)

@Composable
internal fun QualifierTab(a: ColorAdjust, change: (ColorAdjust) -> Unit, ui: LocalColorUi) {
    val q = a.qualifier
    fun set(n: Qualifier) = change(a.copy(qualifier = n))
    GraphicSurface {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PanelIconButton(
                Icons.Rounded.Colorize, if (ui.picking) "Toque na prévia…" else "Conta-gotas: escolher cor",
                tint = if (ui.picking) KeyGold else Color.White, onClick = ui.onEyedropper
            )
            Box(
                Modifier.size(26.dp).clip(CircleShape).background(if (q.active) hueColor(q.hue) else Color.Transparent)
                    .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
            )
            Text(
                when {
                    ui.picking -> "Toque na prévia na cor desejada"
                    q.active -> "Matiz %.0f°".format(q.hue)
                    else -> "Nenhuma cor escolhida"
                },
                color = RiseMuted, fontSize = 12.sp, modifier = Modifier.weight(1f)
            )
            PanelIconButton(
                if (ui.showMask) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                if (ui.showMask) "Ocultar máscara" else "Ver máscara (P&B)",
                enabled = q.active, tint = if (ui.showMask) KeyGold else Color.White, onClick = ui.onMask
            )
            PanelIconButton(Icons.Rounded.RestartAlt, "Resetar seleção", enabled = q != Qualifier()) { set(Qualifier()) }
        }
        // Hue strip with the selected range.
        Canvas(Modifier.fillMaxWidth().height(14.dp).clip(CircleShape)) {
            val n = 60
            for (i in 0 until n) {
                drawRect(hueColor(i * 360f / n), Offset(size.width * i / n, 0f), androidx.compose.ui.geometry.Size(size.width / n + 1f, size.height))
            }
            if (q.active) {
                val c = q.hue / 360f * size.width
                val w = q.hueWidth / 360f * size.width
                drawRect(Color.Black.copy(alpha = 0.55f), Offset(0f, 0f), androidx.compose.ui.geometry.Size((c - w).coerceAtLeast(0f), size.height))
                drawRect(Color.Black.copy(alpha = 0.55f), Offset(c + w, 0f), androidx.compose.ui.geometry.Size((size.width - c - w).coerceAtLeast(0f), size.height))
                drawLine(Color.White, Offset(c, 0f), Offset(c, size.height), 3f)
            }
        }
    }
    if (!q.active) {
        Text("Toque no conta-gotas e depois na prévia para escolher a cor a ajustar.", color = RiseMuted, fontSize = 12.sp)
    }
    val pct = { v: Float -> "%.0f%%".format(v * 100f) }
    val deg = { v: Float -> "±%.0f°".format(v) }
    Text("Faixas", color = RiseMuted, fontSize = 12.sp)
    TileGrid(
        listOf(
            Tile("Largura matiz", q.hueWidth, 2f..90f, 25f, deg) { set(q.copy(hueWidth = it)) },
            Tile("Suav. matiz", q.hueSoft, 0f..1f, 0.5f, pct) { set(q.copy(hueSoft = it)) },
            Tile("Sat. mín", q.satLow, 0f..1f, 0.15f, pct) { set(q.copy(satLow = it.coerceAtMost(q.satHigh))) },
            Tile("Sat. máx", q.satHigh, 0f..1f, 1f, pct) { set(q.copy(satHigh = it.coerceAtLeast(q.satLow))) },
            Tile("Lum. mín", q.lumLow, 0f..1f, 0.05f, pct) { set(q.copy(lumLow = it.coerceAtMost(q.lumHigh))) },
            Tile("Lum. máx", q.lumHigh, 0f..1f, 0.95f, pct) { set(q.copy(lumHigh = it.coerceAtLeast(q.lumLow))) },
            Tile("Suav. sat/lum", q.satSoft, 0f..1f, 0.3f, pct) { set(q.copy(satSoft = it, lumSoft = it)) }
        ),
        enabled = q.active
    )
    Text("Ajustes na seleção", color = RiseMuted, fontSize = 12.sp)
    TileGrid(
        listOf(
            Tile("Matiz", q.hueShift, -1f..1f) { set(q.copy(hueShift = it)) },
            Tile("Saturação", q.saturation, -1f..1f) { set(q.copy(saturation = it)) },
            Tile("Luminosidade", q.lightness, -1f..1f) { set(q.copy(lightness = it)) },
            Tile("Exposição (EV)", q.exposure, -2f..2f) { set(q.copy(exposure = it)) },
            Tile("Temperatura", q.temperature, -1f..1f) { set(q.copy(temperature = it)) }
        ),
        enabled = q.active
    )
}

@Composable
internal fun VignetteTab(a: ColorAdjust, change: (ColorAdjust) -> Unit) {
    val v = a.vignette
    fun set(n: Vignette) = change(a.copy(vignette = n))
    TileGrid(
        listOf(
            Tile("Quantidade", v.amount, -1f..1f) { set(v.copy(amount = it)) },
            Tile("Tamanho", v.size, 0f..1f, 0.5f) { set(v.copy(size = it)) },
            Tile("Suavidade", v.softness, 0f..1f, 0.5f) { set(v.copy(softness = it)) },
            Tile("Arredondamento", v.roundness, -1f..1f) { set(v.copy(roundness = it)) }
        )
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PanelIconButton(Icons.Rounded.CenterFocusStrong, "Centralizar vinheta", enabled = v.cx != 0.5f || v.cy != 0.5f) {
            set(v.copy(cx = 0.5f, cy = 0.5f))
        }
        Text(
            "Arraste o ponto na prévia para mover o centro. Quantidade negativa escurece as bordas.",
            color = RiseMuted, fontSize = 12.sp
        )
    }
}

internal class Tile(
    val label: String, val value: Float, val range: ClosedFloatingPointRange<Float>, val default: Float = 0f,
    val format: ((Float) -> String)? = null, val set: (Float) -> Unit
)

@Composable
internal fun TileGrid(tiles: List<Tile>, enabled: Boolean = true) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cols = (maxWidth / 170.dp).toInt().coerceIn(2, 4)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            tiles.chunked(cols).forEach { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    line.forEach { t ->
                        if (t.format != null) {
                            TileSlider(t.label, t.value, t.range, Modifier.weight(1f), enabled = enabled, default = t.default, format = t.format, change = t.set)
                        } else {
                            TileSlider(t.label, t.value, t.range, Modifier.weight(1f), enabled = enabled, default = t.default, change = t.set)
                        }
                    }
                    repeat(cols - line.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** Draggable vignette center drawn over the video frame (local coords = frame coords). */
@Composable
internal fun VignetteHandle(modifier: Modifier = Modifier) {
    val c = ColorTools.vignetteCenter ?: return
    Box(
        modifier.fillMaxSize().pointerInput(Unit) {
            detectDragGestures { change, _ ->
                change.consume()
                val u = (change.position.x / size.width).coerceIn(0f, 1f)
                val v = (change.position.y / size.height).coerceIn(0f, 1f)
                ColorTools.vignetteCenter = Offset(u, v)
                ColorTools.onVignetteCenter?.invoke(u, v)
            }
        }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val p = Offset(c.x * size.width, c.y * size.height)
            drawCircle(Color.Black.copy(alpha = 0.4f), 16.dp.toPx(), p)
            drawCircle(Color.White, 14.dp.toPx(), p, style = Stroke(2.dp.toPx()))
            drawCircle(Color.White, 3.dp.toPx(), p)
        }
    }
}

/** Shown on top of Ajustar when editing a camada de ajuste: indicator + opacity. */
@Composable
internal fun LayerHeader(layer: br.com.riseedit.core.AdjustmentLayer, onOpacity: (Float) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.Rounded.Layers, null,
                    Modifier.size(16.dp), tint = br.com.riseedit.LaneAdjust
                )
                Text("Camada de ajuste", color = Color.White, fontSize = 13.sp)
            }
            Text(
                "${layer.name} · afeta vídeo e overlays abaixo, de %.1fs a %.1fs".format(layer.startMs / 1000f, layer.endMs / 1000f),
                color = RiseMuted, fontSize = 11.sp
            )
        }
        TileSlider(
            "Opacidade", layer.opacity, 0f..1f, Modifier.width(150.dp), default = 1f,
            format = { "%.0f%%".format(it * 100f) }, change = onOpacity
        )
    }
}
