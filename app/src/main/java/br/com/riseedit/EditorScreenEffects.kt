package br.com.riseedit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import br.com.riseedit.tools.EditorTool
import kotlinx.coroutines.delay

@Composable
internal fun EditorScreenEffects(vm: EditorViewModel, state: EditorState, ui: EditorUiState) {
    LaunchedEffect(state.selection) {
        state.selection?.let { id ->
            ui.tool = when {
                state.project.audio.any { it.id == id } -> EditorTool.Audio
                state.project.texts.any { it.id == id } -> EditorTool.Text
                state.project.overlays.any { it.id == id } -> EditorTool.Overlay
                else -> ui.tool
            }
        }
    }
    LaunchedEffect(state.message) {
        if (state.message != null) {
            delay(6000)
            vm.message(null)
        }
    }
    LaunchedEffect(state.project.clips.isNotEmpty()) {
        if (state.project.clips.isEmpty()) ui.tool = null
    }
}
