package br.com.riseedit

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.riseedit.text.TextEditorSheet
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
    val draft = ui.textDraft
    if (ui.full) {
        Box(Modifier.weight(1f))
    } else {
        PreviewPanel(
            vm,
            state,
            Modifier.weight(1f).fillMaxWidth().heightIn(min = 120.dp).padding(horizontal = 12.dp),
            editable = true,
            mainSelected = ui.contextual,
            onSelect = { main -> ui.contextual = main },
            textDraft = draft,
            onDraftTransform = { ui.textDraft = it },
            pickColor = ui.eyedropper
        )
    }
    if (draft != null) {
        TextEditorSheet(vm, ui, draft)
        return
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
    val level = toolbarLevel(vm, state, ui, media)
    BackHandler(enabled = level.leading != null) { ui.back(vm) }
    AnimatedContent(
        ui.menu.size to (level.leading != null),
        transitionSpec = {
            (slideInHorizontally(risePanelSpring) { it / 6 } + fadeIn(tween(160))) togetherWith
                fadeOut(tween(90))
        },
        label = "toolbar"
    ) { _ ->
        BottomToolbar(level.items, leading = level.leading)
    }
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
                    ToolPanel(tool, vm, state, addAudio = media.audioFile)
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
        3 -> ui.menu = listOf(ToolMenu.TEXT)
        else -> {
            ui.audioTrack = lane - 4
            media.audio()
        }
    }
}
