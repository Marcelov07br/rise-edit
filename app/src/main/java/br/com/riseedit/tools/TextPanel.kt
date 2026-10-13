package br.com.riseedit.tools

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import br.com.riseedit.EditorState
import br.com.riseedit.EditorViewModel
import br.com.riseedit.core.TextLayer
import kotlin.math.min

@Composable
internal fun TextPanel(vm: EditorViewModel, state: EditorState) {
    val project = state.project
    if (project.clips.getOrNull(state.selected) == null) return
    val layer = project.texts.firstOrNull { it.id == state.selection }
    var draft by remember(layer?.id) { mutableStateOf(layer?.text ?: "Sua história começa aqui") }
    OutlinedTextField(
        draft,
        { draft = it.take(160) },
        label = { Text("Texto ou legenda") },
        modifier = Modifier.fillMaxWidth(),
        maxLines = 4
    )
    Button(onClick = {
        val start = state.positionMs.coerceIn(0, maxOf(0, project.durationMs - 1))
        val updated = layer?.copy(text = draft) ?: TextLayer(
            text = draft,
            startMs = start,
            endMs = min(project.durationMs, start + 3000)
        )
        vm.text(updated)
    }, enabled = state.busy == null && draft.isNotBlank()) {
        Text(if (layer == null) "Adicionar no cursor" else "Atualizar texto")
    }
    if (layer != null) {
        TextLayerControls(vm, layer, project.durationMs)
    } else {
        Text(
            "Selecione uma legenda na timeline para editar duração e aparência.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
