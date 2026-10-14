package br.com.riseedit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay

@Composable
internal fun EditorScreenEffects(vm: EditorViewModel, state: EditorState, ui: EditorUiState) {
    LaunchedEffect(state.selection) {
        // A newly selected layer gets its own contextual toolbar (no panel opens by itself).
        if (state.selection != null) {
            ui.contextual = true
            ui.tool = null
            ui.menu = emptyList()
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
