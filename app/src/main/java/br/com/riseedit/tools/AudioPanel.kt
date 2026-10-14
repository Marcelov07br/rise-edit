package br.com.riseedit.tools

import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import br.com.riseedit.EditorState
import br.com.riseedit.EditorViewModel

/** Volume, fade and trim of the selected audio layer. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AudioPanel(vm: EditorViewModel, state: EditorState) {
    val a = state.project.audio.firstOrNull { it.id == state.selection }
    if (a == null) {
        Text("Selecione um áudio na timeline.", style = MaterialTheme.typography.bodySmall)
        return
    }
    Text(a.name, style = MaterialTheme.typography.bodySmall)
    ValueSlider("Volume", a.volume, 0f..2f) { vm.audio(a.copy(volume = it)) }
    ValueSlider("Fade de entrada e saída (s)", a.fadeMs / 1000f, 0f..3f) {
        vm.audio(a.copy(fadeMs = (it * 1000).toLong()))
    }
    var trim by remember(a.id, a.startMs, a.endMs) {
        mutableStateOf(a.startMs.toFloat()..a.endMs.toFloat())
    }
    RangeSlider(
        trim,
        { if (it.endInclusive - it.start >= 40) trim = it },
        valueRange = 0f..a.sourceDurationMs.toFloat()
    )
    Button(onClick = {
        vm.audio(a.copy(startMs = trim.start.toLong(), endMs = trim.endInclusive.toLong()))
    }) { Text("Aplicar corte de áudio") }
}
