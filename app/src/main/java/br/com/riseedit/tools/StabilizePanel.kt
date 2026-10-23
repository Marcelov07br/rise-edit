package br.com.riseedit.tools

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import br.com.riseedit.core.Clip
import br.com.riseedit.core.MotionAnalysis
import br.com.riseedit.core.MotionSmoother
import br.com.riseedit.designsystem.GraphicSurface
import br.com.riseedit.designsystem.PanelIconButton
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.riseedit.EditorState
import br.com.riseedit.EditorViewModel
import br.com.riseedit.core.MotionGoal
import br.com.riseedit.core.Stabilization
import br.com.riseedit.core.StabilizeMethod
import br.com.riseedit.designsystem.ActionBar
import br.com.riseedit.designsystem.RiseDropdown
import br.com.riseedit.designsystem.RiseToggle
import br.com.riseedit.designsystem.PanelAction
import br.com.riseedit.designsystem.SegmentedTabs
import br.com.riseedit.designsystem.TileSlider
import kotlin.math.roundToInt

/** Same tab/segmented/dial philosophy as the color zone (AdjustPanel). */
internal enum class StabilizeTab(val label: String) {
    SMOOTH("Estabilidade"),
    WARP("Distorção"),
    DIAG("Análise")
}

/** A non-neutral Stabilization compared against defaults with [enabled] preserved. */
private fun Stabilization.tuned(): Boolean = copy(enabled = false) != Stabilization(enabled = false)

private val PRESET_SUAVE = Stabilization(true, MotionGoal.SMOOTH, 0.65, 0.4, 1.10)
private val PRESET_FORTE =
    Stabilization(true, MotionGoal.SMOOTH, 1.0, 1.1, 1.25, StabilizeMethod.WARP, 0.8)
private val PRESET_FIXA =
    Stabilization(true, MotionGoal.FIXED, 1.0, 0.8, 1.25, StabilizeMethod.WARP, 1.0)

/** Callbacks of the stabilization zone (stateless content → Paparazzi without a ViewModel). */
internal class StabilizeActions(
    val settings: (Stabilization) -> Unit = {},
    val analyze: () -> Unit = {},
    val compare: () -> Unit = {},
    val copy: () -> Unit = {},
    val paste: () -> Unit = {},
    val hasClipboard: Boolean = false,
    /** Measured trajectory of the selected clip's source (null = not analyzed yet). */
    val analysis: MotionAnalysis? = null
)

@Composable
internal fun StabilizePanel(vm: EditorViewModel, state: EditorState) {
    val clip = state.project.clips.getOrNull(state.selected)
    StabilizeContent(
        state,
        StabilizeActions(
            vm::settings,
            vm::analyze,
            vm::compare,
            vm::copyAttributes,
            vm::pasteAttributes,
            vm.hasClipboard,
            clip?.let { vm.motionAnalysis(it.uri) }
        )
    )
}

@Composable
internal fun StabilizeContent(
    state: EditorState,
    vm: StabilizeActions,
    initialTab: StabilizeTab = StabilizeTab.SMOOTH,
    presetsOpenInitially: Boolean = false,
    infoOpenInitially: Boolean = false
) {
    val clip = state.project.clips.getOrNull(state.selected) ?: return
    val enabled = state.busy == null
    val analyzed = clip.uri in state.analyzed
    val s = clip.stabilization
    var tab by remember { mutableStateOf(initialTab) }
    fun change(next: Stabilization) = vm.settings(next)

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        StabilizeHeader(vm, state, enabled, analyzed, s) { change(it) }
        SegmentedTabs(
            StabilizeTab.entries.toList(),
            tab,
            label = { it.label },
            marked = {
                when (it) {
                    StabilizeTab.SMOOTH -> s.tuned()
                    StabilizeTab.WARP -> s.method == StabilizeMethod.WARP
                    StabilizeTab.DIAG -> false
                }
            },
            onSelect = { tab = it }
        )
        when (tab) {
            StabilizeTab.SMOOTH ->
                SmoothTab(vm, state, enabled, analyzed, s, presetsOpenInitially) { change(it) }
            StabilizeTab.WARP -> WarpTab(analyzed, s, infoOpenInitially) { change(it) }
            StabilizeTab.DIAG -> DiagTab(vm, state, enabled, clip, analyzed)
        }
    }
}

/** Level 2, same as the color zone: icon-only actions in the top corner, reset set apart. */
@Composable
private fun StabilizeHeader(
    vm: StabilizeActions,
    state: EditorState,
    enabled: Boolean,
    analyzed: Boolean,
    s: Stabilization,
    change: (Stabilization) -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        ActionBar(
            listOfNotNull(
                PanelAction(
                    Icons.Rounded.PowerSettingsNew,
                    if (s.enabled) "Desligar estabilização" else "Ligar estabilização",
                    enabled && analyzed,
                    primary = true,
                    onClick = { change(s.copy(enabled = !s.enabled)) }
                ),
                if (analyzed) {
                    PanelAction(
                        Icons.Rounded.Compare,
                        if (state.comparing) "Ver estabilizado" else "Antes / depois",
                        enabled,
                        primary = true,
                        onClick = vm.compare
                    )
                } else {
                    null
                },
                PanelAction(Icons.Rounded.ContentCopy, "Copiar ajustes", enabled, onClick = vm.copy),
                PanelAction(
                    Icons.Rounded.ContentPaste,
                    "Colar ajustes",
                    enabled && vm.hasClipboard,
                    onClick = vm.paste
                ),
                PanelAction(
                    Icons.Rounded.RestartAlt,
                    "Resetar ajustes",
                    enabled && s.tuned(),
                    danger = true,
                    onClick = { change(Stabilization(enabled = s.enabled)) }
                )
            )
        )
    }
}

@Composable
private fun SmoothTab(
    vm: StabilizeActions,
    state: EditorState,
    enabled: Boolean,
    analyzed: Boolean,
    s: Stabilization,
    presetsOpenInitially: Boolean,
    change: (Stabilization) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (!analyzed) {
            AnalyzeCta(vm, enabled)
        } else {
            RiseDropdown(
                "Predefinição",
                listOf<Stabilization?>(PRESET_SUAVE, PRESET_FORTE, PRESET_FIXA),
                presetMatch(s),
                expandedInitially = presetsOpenInitially,
                text = { it?.let(::presetName) ?: "Personalizado" }
            ) { p -> p?.let { change(it.copy(enabled = true)) } }
            DialGrid(
                listOf(
                    Dial(
                        "Intensidade", s.strength.toFloat(), 0f..1f,
                        Stabilization().strength.toFloat()
                    ) { change(s.copy(strength = it.toDouble())) },
                    Dial(
                        "Suavidade", s.smoothSeconds.toFloat(), 0.1f..2f,
                        Stabilization().smoothSeconds.toFloat(),
                        format = { "%.2f s".format(it) }
                    ) { change(s.copy(smoothSeconds = it.toDouble().coerceIn(0.1, 2.0))) },
                    Dial(
                        "Crop", s.maxZoom.toFloat(), 1f..1.25f,
                        Stabilization().maxZoom.toFloat(),
                        format = { "%.2f×".format(it) }
                    ) { change(s.copy(maxZoom = it.toDouble().coerceIn(1.0, 1.25))) }
                )
            )
        }
        BusyLine(state)
    }
}

@Composable
private fun BusyLine(state: EditorState) {
    if (state.busy != null && state.progress > 0f) {
        Text(
            "Analisando… ${(state.progress * 100).toInt()}%",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun AnalyzeCta(vm: StabilizeActions, enabled: Boolean, again: Boolean = false) {
    Button(
        onClick = vm.analyze,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) { Text(if (again) "Analisar novamente" else "Analisar movimento") }
}

@Composable
private fun WarpTab(
    analyzed: Boolean,
    s: Stabilization,
    infoOpenInitially: Boolean,
    change: (Stabilization) -> Unit
) {
    var info by remember { mutableStateOf(infoOpenInitially) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (!analyzed) {
            Text("Analise o movimento primeiro.", style = MaterialTheme.typography.bodySmall)
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RiseDropdown(
                "Método",
                StabilizeMethod.entries.toList(),
                s.method,
                Modifier.weight(1f),
                text = { if (it == StabilizeMethod.RIGID) "Rígido" else "Warp" }
            ) { change(s.copy(method = it)) }
            Spacer(Modifier.width(10.dp))
            PanelIconButton(Icons.Rounded.Info, "Sobre o Warp") { info = !info }
        }
        AnimatedVisibility(info) {
            GraphicSurface {
                Text(
                    "Warp corrige o “jello” do rolling shutter linha a linha. " +
                        "Rígido só move, gira e corta.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        DialGrid(
            listOf(
                Dial(
                    "Distorção", s.rollingShutter.toFloat(), 0f..1f,
                    Stabilization().rollingShutter.toFloat()
                ) { change(s.copy(rollingShutter = it.toDouble())) },
                Dial(
                    "Força do Warp", s.strength.toFloat(), 0f..1f,
                    Stabilization().strength.toFloat()
                ) { change(s.copy(strength = it.toDouble())) }
            )
        )
    }
}

@Composable
private fun DiagTab(
    vm: StabilizeActions,
    state: EditorState,
    enabled: Boolean,
    clip: Clip,
    analyzed: Boolean
) {
    val analysis = vm.analysis.takeIf { analyzed }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        GraphicSurface {
            if (analysis == null) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    Text("Sem análise", style = MaterialTheme.typography.bodySmall)
                }
            } else {
                MotionGraph(analysis, clip)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Legend(MEASURED_COLOR, "Medido")
                    Legend(MaterialTheme.colorScheme.primary, "Suavizado")
                }
            }
        }
        AnalyzeCta(vm, enabled, again = analyzed)
        BusyLine(state)
        TelemetryReadout(state)
    }
}

private val MEASURED_COLOR = Color(0xFF8A8A90)

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

/**
 * Horizontal camera path over the clip: measured (raw tracker pose) vs. what the viewer sees
 * after the correction (measured + correction), both normalized to the same scale.
 */
@Composable
private fun MotionGraph(analysis: MotionAnalysis, clip: Clip) {
    val accent = MaterialTheme.colorScheme.primary
    val paths = remember(analysis, clip.stabilization, clip.startMs, clip.endMs) {
        motionPaths(analysis, clip)
    }
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val (raw, smooth) = paths
        if (raw.size < 2) return@Canvas
        val all = raw + smooth
        val lo = all.min()
        val span = (all.max() - lo).takeIf { it > 1e-9 } ?: 1.0
        fun line(v: List<Double>, color: Color, w: Float) {
            val path = Path()
            v.forEachIndexed { i, y ->
                val px = size.width * i / (v.size - 1)
                val py = (size.height * (1 - (y - lo) / span)).toFloat()
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path, color, style = Stroke(w))
        }
        line(raw, MEASURED_COLOR, 1.5.dp.toPx())
        line(smooth, accent, 2.dp.toPx())
    }
}

internal fun motionPaths(analysis: MotionAnalysis, clip: Clip): Pair<List<Double>, List<Double>> {
    val settings = clip.stabilization.copy(enabled = true)
    val corrections = MotionSmoother.corrections(analysis, settings, clip.startMs, clip.endMs)
    val samples = analysis.samples.filter { it.timeMs in clip.startMs..clip.endMs }
    if (samples.size != corrections.size) return emptyList<Double>() to emptyList()
    val raw = samples.map { it.pose.x }
    val smooth = samples.indices.map { samples[it].pose.x + corrections[it].pose.x }
    return raw to smooth
}

private fun presetName(p: Stabilization): String = when (p) {
    PRESET_SUAVE -> "Suave"
    PRESET_FORTE -> "Forte"
    else -> "Câmera fixa"
}

private fun presetMatch(s: Stabilization): Stabilization? =
    listOf(PRESET_SUAVE, PRESET_FORTE, PRESET_FIXA).firstOrNull { it == s }

private data class Dial(
    val label: String,
    val value: Float,
    val range: ClosedFloatingPointRange<Float>,
    val default: Float,
    val format: (Float) -> String = { v -> "%d%%".format((v * 100).roundToInt()) },
    val change: (Float) -> Unit
)

/** Responsive TileSlider grid, same layout as the color zone's AdjustGrid. */
@Composable
private fun DialGrid(dials: List<Dial>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cols = (maxWidth / 170.dp).toInt().coerceIn(2, 3) // 2 colunas no celular, como a zona de cor
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            dials.chunked(cols).forEach { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    line.forEach { d ->
                        TileSlider(
                            d.label,
                            d.value,
                            d.range,
                            Modifier.weight(1f),
                            default = d.default,
                            format = d.format
                        ) { d.change(it) }
                    }
                    repeat(cols - line.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
