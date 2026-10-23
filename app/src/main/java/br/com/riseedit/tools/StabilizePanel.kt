package br.com.riseedit.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.riseedit.ChoiceRow
import br.com.riseedit.EditorState
import br.com.riseedit.EditorViewModel
import br.com.riseedit.RiseWhite
import br.com.riseedit.core.MotionGoal
import br.com.riseedit.core.Stabilization
import br.com.riseedit.core.StabilizeMethod
import br.com.riseedit.designsystem.ActionBar
import br.com.riseedit.designsystem.PanelAction
import br.com.riseedit.designsystem.SegmentedTabs
import br.com.riseedit.designsystem.TileSlider

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

@Composable
internal fun StabilizePanel(vm: EditorViewModel, state: EditorState) {
    val clip = state.project.clips.getOrNull(state.selected) ?: return
    val enabled = state.busy == null
    val analyzed = clip.uri in state.analyzed
    val s = clip.stabilization
    var tab by remember { mutableStateOf(StabilizeTab.SMOOTH) }
    LaunchedEffect(analyzed) {
        if (!analyzed) tab = StabilizeTab.SMOOTH
    }
    fun change(next: Stabilization) = vm.settings(next)

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        StabilizeHeader(vm, state, enabled, analyzed, s.tuned(), s.enabled) { change(it) }        SegmentedTabs(
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
            StabilizeTab.SMOOTH -> SmoothTab(vm, state, enabled, analyzed, s) { change(it) }
            StabilizeTab.WARP -> WarpTab(analyzed, s) { change(it) }
            StabilizeTab.DIAG -> DiagTab(state)
        }
    }
}

@Composable
private fun StabilizeHeader(
    vm: EditorViewModel,
    state: EditorState,
    enabled: Boolean,
    analyzed: Boolean,
    tuned: Boolean,
    keepEnabled: Boolean,
    change: (Stabilization) -> Unit
) {
    Row(Modifier.fillMaxWidth()) {
        Spacer(Modifier.weight(1f).width(8.dp))
        ActionBar(
            listOfNotNull(
                if (analyzed) {
                    PanelAction(
                        Icons.Rounded.Compare,
                        if (state.comparing) "Ver estabilizado" else "Antes / depois",
                        enabled,
                        primary = true,
                        onClick = vm::compare
                    )
                } else {
                    null
                },
                PanelAction(
                    Icons.Rounded.ContentCopy,
                    "Copiar ajustes",
                    enabled,
                    onClick = vm::copyAttributes
                ),
                if (vm.hasClipboard) {
                    PanelAction(
                        Icons.Rounded.ContentPaste,
                        "Colar ajustes",
                        enabled,
                        onClick = vm::pasteAttributes
                    )
                } else {
                    null
                },
                PanelAction(
                    Icons.Rounded.RestartAlt,
                    "Resetar ajustes",
                    enabled && tuned,
                    danger = true,
                    onClick = { change(Stabilization(enabled = keepEnabled)) }
                )
            ),
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

@Composable
private fun SmoothTab(
    vm: EditorViewModel,
    state: EditorState,
    enabled: Boolean,
    analyzed: Boolean,
    s: Stabilization,
    change: (Stabilization) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (!analyzed) {
            AnalyzeCta(vm, enabled)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    s.enabled,
                    { change(s.copy(enabled = !s.enabled)) },
                    enabled = enabled,
                    label = { Text(if (s.enabled) "Ativada" else "Desativada") }
                )
                ChoiceRow(
                    listOf(PRESET_SUAVE, PRESET_FORTE, PRESET_FIXA),
                    presetMatch(s) ?: s,
                    { change(it) },
                    { presetName(it) }
                )
            }
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
            if (s.enabled && s.method == StabilizeMethod.WARP) {
                Text(
                    "Método Warp ativo: a correção de distorção está na aba Distorção.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        if (state.busy != null && state.progress > 0f) {
            Text(
                "Analisando… ${(state.progress * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun AnalyzeCta(vm: EditorViewModel, enabled: Boolean) {
    Text("✦ De tremido a suave", color = RiseWhite, style = MaterialTheme.typography.titleMedium)
    Text(
        "1. Analise o movimento  2. Ajuste os dials  3. Compare antes e depois",
        style = MaterialTheme.typography.bodySmall
    )
    Button(
        onClick = vm::analyze,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) { Text("Analisar e estabilizar") }
}

@Composable
private fun WarpTab(
    analyzed: Boolean,
    s: Stabilization,
    change: (Stabilization) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (!analyzed) {
            Text(
                "Analise o movimento na aba Estabilidade para liberar a correção de distorção.",
                style = MaterialTheme.typography.bodySmall
            )
            return@Column
        }
        Text("Método", style = MaterialTheme.typography.labelMedium)
        ChoiceRow(
            StabilizeMethod.entries.toList(),
            s.method,
            { change(s.copy(method = it)) },
            { if (it == StabilizeMethod.RIGID) "Rígido" else "Warp" }
        )
        DialGrid(
            listOf(
                Dial(
                    "Distorção", s.rollingShutter.toFloat(), 0f..1f,
                    Stabilization().rollingShutter.toFloat()
                ) { change(s.copy(rollingShutter = it.toDouble())) },
                Dial(
                    "Intensidade", s.strength.toFloat(), 0f..1f,
                    Stabilization().strength.toFloat()
                ) { change(s.copy(strength = it.toDouble())) },
                Dial(
                    "Crop", s.maxZoom.toFloat(), 1f..1.25f,
                    Stabilization().maxZoom.toFloat(),
                    format = { "%.2f×".format(it) }
                ) { change(s.copy(maxZoom = it.toDouble().coerceIn(1.0, 1.25))) }
            )
        )
        Text(
            "Warp endireita o tremor de rolling shutter (jello) que a correção rígida não " +
                "alcança: cada linha do quadro é corrigida com a velocidade medida entre " +
                "quadros. Rígido aplica apenas posição, rotação e crop.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun DiagTab(state: EditorState) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TelemetryReadout(state)
        Text(
            "Até 20 minutos por fonte. Confira o resultado: cenas com pouca textura ou objetos " +
                "grandes em movimento podem exigir ajustes.",
            style = MaterialTheme.typography.bodySmall
        )
    }
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
    val format: (Float) -> String = { v -> "%d%%".format(v * 100) },
    val change: (Float) -> Unit
)

/** Responsive TileSlider grid, same layout as the color zone's AdjustGrid. */
@Composable
private fun DialGrid(dials: List<Dial>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cols = (maxWidth / 170.dp).toInt().coerceIn(2, 3)
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
