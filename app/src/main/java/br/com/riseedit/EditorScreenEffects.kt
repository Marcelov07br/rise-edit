package br.com.riseedit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import br.com.riseedit.core.Selection
import br.com.riseedit.editor.ExportBus
import kotlinx.coroutines.delay

@Composable
internal fun EditorScreenEffects(vm: EditorViewModel, state: EditorState, ui: EditorUiState) {
    val previous = remember { mutableStateOf<Selection>(Selection.None) }
    LaunchedEffect(state.selection) {
        val selection = state.selection
        val before = previous.value
        previous.value = selection
        when {
            // A newly selected layer gets its own contextual toolbar (no panel opens by itself).
            state.layerId != null && selection != before -> {
                ui.contextual = true
                ui.tool = null
                ui.menu = emptyList()
            }
            // The layer target was deselected or deleted: its panels must not stay open on nothing.
            state.layerId == null && before !is Selection.None && before !is Selection.Clip -> {
                ui.contextual = false
                ui.tool = null
                ui.menu = emptyList()
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
    val export by ExportBus.state.collectAsState()
    LaunchedEffect(export.message) {
        export.message?.let { vm.message(it) }
    }
}
