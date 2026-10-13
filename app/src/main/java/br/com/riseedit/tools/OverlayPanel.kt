package br.com.riseedit.tools

import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import br.com.riseedit.ChoiceRow
import br.com.riseedit.EditorState
import br.com.riseedit.EditorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OverlayPanel(
    vm: EditorViewModel,
    state: EditorState,
    chooseOverlayTrack: (Int) -> Unit,
    addOverlay: () -> Unit
) {
    val p = state.project
    val clip = p.clips.getOrNull(state.selected) ?: return
    val enabled = state.busy == null
    val o = p.overlays.firstOrNull { it.id == state.selection }
    var track by remember { mutableIntStateOf(0) }
    ChoiceRow(listOf(0, 1), track, {
        track = it
        chooseOverlayTrack(it)
    }) { "Camada ${it + 1}" }
    Button(onClick = addOverlay, enabled = enabled) { Text("Importar vídeo sobreposto") }
    if (o != null) {
        ValueSlider("Escala", o.scale, 0.1f..1f) { vm.overlay(o.copy(scale = it)) }
        ValueSlider("Posição horizontal", o.x, -0.8f..0.8f) { vm.overlay(o.copy(x = it)) }
        ValueSlider("Posição vertical", o.y, -0.8f..0.8f) { vm.overlay(o.copy(y = it)) }
        ValueSlider("Opacidade", o.opacity, 0f..1f) { vm.overlay(o.copy(opacity = it)) }
        var trim by remember(o.id, o.clip.startMs, o.clip.endMs) {
            mutableStateOf(
                o.clip.startMs.toFloat()..o.clip.endMs.toFloat()
            )
        }
        RangeSlider(trim, {
            if (it.endInclusive - it.start >=
                40
            ) {
                trim = it
            }
        }, valueRange = 0f..o.clip.sourceDurationMs.toFloat())
        Button(onClick = {
            vm.overlay(o.copy(clip = o.clip.trim(trim.start.toLong(), trim.endInclusive.toLong())))
        }) { Text("Aplicar corte") }
        TextButton(onClick = vm::removeLayer) { Text("Excluir overlay") }
    } else {
        Text(
            "Importe um vídeo e selecione seu bloco para ajustar o picture-in-picture.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
