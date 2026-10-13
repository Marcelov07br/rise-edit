package br.com.riseedit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.riseedit.tools.ToolPanel

@Composable
internal fun ColumnScope.EditorWorkspace(
    vm: EditorViewModel,
    state: EditorState,
    ui: EditorUiState,
    media: EditorMediaActions,
    sheetMax: Dp
) {
    val enabled = state.busy == null
    if (ui.full) {
        Box(Modifier.weight(1f))
    } else {
        PreviewPanel(
            vm,
            state,
            Modifier.weight(1f).fillMaxWidth().heightIn(min = 120.dp).padding(horizontal = 12.dp)
        )
    }
    TransportBar(vm, state, enabled, onFullscreen = { ui.full = true })
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        TimelinePanel(vm, state, trackAreaHeight = if (ui.tool == null) 190.dp else 110.dp)
    }
    val current = ui.tool
    if (current == null) {
        BottomToolbar(
            enabled,
            state.selection != null,
            media.videos,
            onTool = { ui.tool = it },
            onSplit = vm::split,
            onDelete = { if (state.selection != null) vm.removeLayer() else vm.remove() }
        )
    } else {
        ToolSheet(current.title, sheetMax, onClose = { ui.tool = null }) {
            ToolPanel(
                current,
                vm,
                state,
                chooseAudioTrack = { ui.audioTrack = it },
                chooseOverlayTrack = { ui.overlayTrack = it },
                addAudio = media.audio,
                addOverlay = media.overlay
            )
        }
    }
}
