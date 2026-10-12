package br.com.riseedit

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Fixed regions; timeline and tool sheet scroll internally, never the entire editor. */
@Composable
fun EditorScreen(
    vm: EditorViewModel,
    state: EditorState,
    onBack: () -> Unit,
    onSettings: () -> Unit
) {
    val ui = rememberEditorUiState()
    val media = rememberEditorMediaActions(vm, state, ui)
    val enabled = state.busy == null
    val hasClips = state.project.clips.isNotEmpty()
    EditorScreenEffects(vm, state, ui)
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        val sheetMax = maxHeight * 0.42f
        Column(Modifier.fillMaxSize()) {
            EditorTopBar(
                state.project.name,
                enabled,
                enabled && hasClips,
                onBack,
                onSettings,
                onExport = { ui.exportDialog = true }
            )
            if (hasClips) {
                EditorWorkspace(vm, state, ui, media, sheetMax)
            } else {
                EditorEmptyPanel(enabled, media.videos)
            }
        }
        EditorMessage(state.message, vm::message)
    }
    EditorDialogs(vm, state, ui, media)
}
