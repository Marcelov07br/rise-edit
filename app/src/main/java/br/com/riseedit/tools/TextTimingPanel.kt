package br.com.riseedit.tools

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import br.com.riseedit.EditorState
import br.com.riseedit.EditorViewModel
import kotlin.math.max

/** When the selected text appears and for how long (position/size live in the preview). */
@Composable
internal fun TextTimingPanel(vm: EditorViewModel, state: EditorState) {
    val layer = state.project.texts.firstOrNull { it.id == state.selection }
    val durationMs = state.project.durationMs
    if (layer == null) {
        Text("Selecione um texto na timeline.", style = MaterialTheme.typography.bodySmall)
        return
    }
    ValueSlider("Início (s)", layer.startMs / 1000f, 0f..max(0.001f, (durationMs - 1) / 1000f)) {
        val start = (it * 1000).toLong()
        vm.text(layer.copy(startMs = start, endMs = max(start + 1, layer.endMs)))
    }
    ValueSlider(
        "Duração (s)",
        (layer.endMs - layer.startMs) / 1000f,
        0.001f..max(0.001f, (durationMs - layer.startMs) / 1000f)
    ) {
        vm.text(layer.copy(endMs = layer.startMs + (it * 1000).toLong().coerceAtLeast(1)))
    }
}
