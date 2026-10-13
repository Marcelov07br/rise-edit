package br.com.riseedit.tools

import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
internal fun AudioPanel(
    vm: EditorViewModel,
    state: EditorState,
    chooseAudioTrack: (Int) -> Unit,
    addAudio: () -> Unit
) {
    val p = state.project
    val clip = p.clips.getOrNull(state.selected) ?: return
    val enabled = state.busy == null
    val a = p.audio.firstOrNull { it.id == state.selection }
    var track by remember { mutableIntStateOf(0) }
    ChoiceRow(listOf(0, 1, 2), track, {
        track = it
        chooseAudioTrack(it)
    }) { "Faixa ${it + 1}" }
    Button(onClick = addAudio, enabled = enabled) { Text("Importar música / voz") }
    if (a != null) {
        Text(a.name)
        ValueSlider("Volume", a.volume, 0f..2f) { vm.audio(a.copy(volume = it)) }
        ValueSlider("Fade de entrada e saída (s)", a.fadeMs / 1000f, 0f..3f) {
            vm.audio(
                a.copy(
                    fadeMs = (
                        it *
                            1000
                        ).toLong()
                )
            )
        }
        var trim by remember(a.id, a.startMs, a.endMs) {
            mutableStateOf(
                a.startMs.toFloat()..a.endMs.toFloat()
            )
        }
        RangeSlider(trim, {
            if (it.endInclusive - it.start >=
                40
            ) {
                trim = it
            }
        }, valueRange = 0f..a.sourceDurationMs.toFloat())
        Button(onClick = {
            vm.audio(a.copy(startMs = trim.start.toLong(), endMs = trim.endInclusive.toLong()))
        }) { Text("Aplicar corte de áudio") }
        TextButton(onClick = vm::removeLayer) { Text("Excluir áudio") }
    } else {
        Text("Som original do clipe selecionado")
        ValueSlider("Volume", clip.volume, 0f..2f) { vm.clipSettings(clip.copy(volume = it)) }
    }
}
