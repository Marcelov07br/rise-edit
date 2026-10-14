package br.com.riseedit

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.VerticalSplit
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.riseedit.tools.EditorTool
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
            Modifier.weight(1f).fillMaxWidth().heightIn(min = 120.dp).padding(horizontal = 12.dp),
            editable = true,
            mainSelected = ui.contextual,
            onSelect = { main -> ui.contextual = main }
        )
    }
    TransportBar(vm, state, enabled, onFullscreen = { ui.full = true })
    Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        TimelinePanel(
            vm,
            state,
            trackAreaHeight = if (ui.tool == null) 168.dp else 112.dp,
            onSelect = { ui.contextual = true },
            onAddToLane = { lane -> addToLane(lane, ui, media) }
        )
    }
    DockedToolPanel(vm, state, ui, media, sheetMax)
    val contextual = ui.contextual && state.project.clips.isNotEmpty()
    BottomToolbar(
        if (contextual) contextualItems(vm, state, ui, enabled) else mainItems(ui, media, enabled),
        leading = if (contextual) {
            ToolbarItem("Voltar", Icons.AutoMirrored.Outlined.ArrowBack) {
                ui.contextual = false
                ui.tool = null
            }
        } else {
            null
        }
    )
}

@Composable
private fun DockedToolPanel(
    vm: EditorViewModel,
    state: EditorState,
    ui: EditorUiState,
    media: EditorMediaActions,
    sheetMax: Dp
) {
    AnimatedVisibility(
        visible = ui.tool != null,
        enter = slideInVertically(risePanelSpring) { it / 2 } + fadeIn(tween(160)),
        exit = slideOutVertically(risePanelSpring) { it / 2 } + fadeOut(tween(120))
    ) {
        val current = ui.tool ?: ui.lastTool
        AnimatedContent(
            current,
            transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(100)) },
            label = "tool"
        ) { tool ->
            if (tool != null) {
                ToolSheet(tool.title, sheetMax, onClose = { ui.tool = null }) {
                    ToolPanel(
                        tool,
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
    }
}

private fun addToLane(lane: Int, ui: EditorUiState, media: EditorMediaActions) {
    when (lane) {
        0 -> media.videos()
        1, 2 -> {
            ui.overlayTrack = lane - 1
            media.overlay()
        }
        3 -> ui.openTool(EditorTool.Text)
        else -> {
            ui.audioTrack = lane - 4
            media.audio()
        }
    }
}

private fun EditorUiState.openTool(tool: EditorTool) {
    lastTool = tool
    this.tool = if (this.tool == tool) null else tool
}

private fun EditorUiState.toolItem(tool: EditorTool, enabled: Boolean) =
    ToolbarItem(tool.label, tool.icon, enabled, active = this.tool == tool) { openTool(tool) }

private fun mainItems(ui: EditorUiState, media: EditorMediaActions, enabled: Boolean) =
    listOf(ToolbarItem("Vídeo", Icons.Outlined.Add, enabled, onClick = media.videos)) +
        EditorTool.main.map { ui.toolItem(it, enabled) }

private fun contextualItems(
    vm: EditorViewModel,
    state: EditorState,
    ui: EditorUiState,
    enabled: Boolean
): List<ToolbarItem> {
    val selection = state.selection
    val delete = ToolbarItem("Excluir", Icons.Outlined.DeleteOutline, enabled) {
        if (selection != null) vm.removeLayer() else vm.remove()
        ui.tool = null
        ui.contextual = false
    }
    val project = state.project
    val layerTools = when {
        selection == null -> null
        project.audio.any { it.id == selection } -> listOf(EditorTool.Volume, EditorTool.Audio)
        project.overlays.any { it.id == selection } -> listOf(EditorTool.Overlay)
        project.texts.any { it.id == selection } -> listOf(EditorTool.Text)
        else -> emptyList()
    }
    if (layerTools != null) return layerTools.map { ui.toolItem(it, enabled) } + delete
    val split = ToolbarItem("Dividir", Icons.Outlined.VerticalSplit, enabled, onClick = vm::split)
    val clipTools = listOf(
        EditorTool.Speed,
        EditorTool.Stabilize,
        EditorTool.Volume,
        EditorTool.Trim,
        EditorTool.Color,
        EditorTool.Movement
    )
    return listOf(split) + clipTools.map { ui.toolItem(it, enabled) } + delete
}
