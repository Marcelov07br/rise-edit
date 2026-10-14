package br.com.riseedit.tools

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import br.com.riseedit.ChoiceRow
import br.com.riseedit.EditorState
import br.com.riseedit.EditorViewModel

@Composable
internal fun FormatPanel(vm: EditorViewModel, state: EditorState) {
    val p = state.project
    val clip = p.clips.getOrNull(state.selected) ?: return
    val enabled = state.busy == null
    ChoiceRow(listOf(0f, 9f / 16f, 16f / 9f, 1f), p.aspectRatio, {
        vm.projectSettings(it, p.export)
    }) {
        when (it) {
            0f -> "Original"
            1f -> "1:1"
            9f /
                16f -> "9:16"
            else -> "16:9"
        }
    }
    Text(
        "O enquadramento preenche o formato escolhido. Ajuste posição e escala direto na prévia.",
        style = MaterialTheme.typography.bodySmall
    )
}
