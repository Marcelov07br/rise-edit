package br.com.riseedit

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.runtime.key
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
    workspaceHeight: Dp
) {
    val toolOpen = ui.tool != null
    androidx.compose.runtime.LaunchedEffect(toolOpen) {
        if (!toolOpen) ToolSheetDetent.expanded = false
    }
    val sheetMax = ToolSheetDetent.maxContent(workspaceHeight)
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
            pickColor = ui.eyedropper ?: br.com.riseedit.tools.ColorTools.eyedropper
        )
    }
    if (draft != null) {
        TextEditorSheet(vm, ui, draft)
        return
    }
    // With a tool panel open the timeline collapses to a mini scrub bar (generic for every panel),
    // giving its height to the preview; it springs back when the panel closes.
    AnimatedVisibility(
        visible = !toolOpen,
        enter = layoutEnter(),
        exit = layoutExit()
    ) {
        androidx.compose.foundation.layout.Column {
            TransportBar(vm, state, enabled, onFullscreen = { ui.full = true })
            Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                TimelinePanel(
                    vm,
                    state,
                    trackAreaHeight = 168.dp,
                    onSelect = { ui.contextual = true },
                    onAddToLane = { lane -> addToLane(lane, ui, media, vm) },
                    onKeyframe = {
                        ui.contextual = true
                        ui.lastTool = br.com.riseedit.tools.EditorTool.Keyframe
                        ui.tool = br.com.riseedit.tools.EditorTool.Keyframe
                    }
                )
            }
        }
    }
    AnimatedVisibility(
        visible = toolOpen,
        enter = layoutEnter(),
        exit = layoutExit()
    ) {
        MiniScrubBar(
            playing = state.playing,
            positionMs = state.positionMs,
            durationMs = state.project.durationMs,
            fps = state.project.export.fps,
            enabled = enabled,
            onToggle = vm::togglePlay,
            onSeek = vm::seek,
            marks = br.com.riseedit.tools.colorKeyMarks(state)
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
    ) { target ->
        key(target) { BottomToolbar(level.items, leading = level.leading) }
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
        // Same height spring as the timeline: the panel's height is released exactly while the
        // timeline's is taken back, so the preview (weight 1f) resizes continuously instead of
        // being squeezed and jumping when a slide-out finally removes the panel.
        enter = layoutEnter(clip = true),
        exit = layoutExit(clip = true)
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

private fun addToLane(lane: Int, ui: EditorUiState, media: EditorMediaActions, vm: EditorViewModel) {
    when (lane) {
        0 -> media.videos()
        br.com.riseedit.timeline.LANE_ADJUST -> vm.addAdjustmentLayer()
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

/**
 * One spring for every height in the workspace (timeline, mini scrub bar, tool panel) so opening
 * and closing are symmetric. Height (expand/shrink) and alpha share the same low-bounce spring.
 */
private fun layoutEnter(clip: Boolean = false) =
    expandVertically(riseSpring(), expandFrom = androidx.compose.ui.Alignment.Top, clip = clip) +
        fadeIn(riseSpring())

private fun layoutExit(clip: Boolean = false) =
    shrinkVertically(riseSpring(), shrinkTowards = androidx.compose.ui.Alignment.Top, clip = clip) +
        fadeOut(riseSpring())
