package br.com.riseedit.keys

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import br.com.riseedit.EditorMediaActions
import br.com.riseedit.EditorState
import br.com.riseedit.EditorUiState
import br.com.riseedit.EditorViewModel
import br.com.riseedit.ToolMenu
import br.com.riseedit.back
import br.com.riseedit.core.shortcuts.RiseAction
import br.com.riseedit.tools.EditorTool

/**
 * 0.22: the single keyboard entry point of the editor. Keys bubble here only when no focused child
 * (text field, slider) consumed them, so typing a title never triggers a shortcut.
 */
@Composable
internal fun Modifier.editorKeyboard(
    vm: EditorViewModel,
    state: EditorState,
    ui: EditorUiState,
    media: EditorMediaActions,
    onShortcuts: () -> Unit
): Modifier {
    val context = LocalContext.current
    val map = remember { AppKeymap.get(context) }
    val latest = rememberUpdatedState(state)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    return this.focusRequester(focus).focusable().onKeyEvent { e ->
        if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
        val chord = AppKeymap.chord(e.nativeKeyEvent) ?: return@onKeyEvent false
        val action = map.action(chord) ?: return@onKeyEvent false
        if (latest.value.busy != null && action != RiseAction.PLAY_PAUSE) return@onKeyEvent true
        perform(action, vm, latest.value, ui, media, onShortcuts)
        true
    }
}

@Suppress("CyclomaticComplexMethod", "LongMethod")
internal fun perform(
    action: RiseAction,
    vm: EditorViewModel,
    s: EditorState,
    ui: EditorUiState,
    media: EditorMediaActions,
    onShortcuts: () -> Unit
) {
    val clips = s.project.clips
    val edges = clips.runningFold(0L) { t, c -> t + c.durationMs }
    val pos = s.positionMs
    fun seek(ms: Long) {
        vm.pausePreview()
        vm.seek(ms.coerceIn(0, s.project.durationMs))
    }
    when (action) {
        RiseAction.PLAY_PAUSE -> vm.togglePlay()
        // Shuttle emulated: J jumps back 1 s per press, K stops, L plays forward.
        RiseAction.SHUTTLE_REVERSE -> seek(pos - 1000)
        RiseAction.SHUTTLE_STOP -> vm.pausePreview()
        RiseAction.SHUTTLE_FORWARD -> if (!s.playing) vm.togglePlay()
        RiseAction.FRAME_BACK -> vm.stepFrame(-1)
        RiseAction.FRAME_FORWARD -> vm.stepFrame(1)
        RiseAction.FRAMES_BACK_5 -> vm.stepFrame(-5)
        RiseAction.FRAMES_FORWARD_5 -> vm.stepFrame(5)
        RiseAction.SECOND_BACK -> seek(pos - 1000)
        RiseAction.SECOND_FORWARD -> seek(pos + 1000)
        RiseAction.PREV_EDIT -> seek(edges.lastOrNull { it < pos - 1 } ?: 0)
        RiseAction.NEXT_EDIT -> seek(edges.firstOrNull { it > pos + 1 } ?: s.project.durationMs)
        RiseAction.GO_START -> seek(0)
        RiseAction.GO_END -> seek(s.project.durationMs)
        RiseAction.CLIP_START -> edges.getOrNull(s.selected)?.let(::seek)
        RiseAction.CLIP_END -> edges.getOrNull(s.selected + 1)?.let { seek(it - 1) }
        RiseAction.RIPPLE_TRIM_START -> vm.rippleTrim(toStart = true)
        RiseAction.RIPPLE_TRIM_END -> vm.rippleTrim(toStart = false)
        RiseAction.SPLIT -> vm.split()
        RiseAction.DELETE -> vm.deleteSelected()
        RiseAction.RIPPLE_DELETE -> vm.rippleDelete()
        RiseAction.DUPLICATE -> vm.duplicate()
        RiseAction.COPY -> vm.copyClip()
        RiseAction.PASTE -> vm.pasteClip()
        RiseAction.PASTE_EFFECTS -> vm.pasteAttributes()
        RiseAction.UNDO -> if (s.canUndo) vm.undo()
        RiseAction.REDO -> if (s.canRedo) vm.redo()
        RiseAction.FREEZE_FRAME -> vm.freezeFrame(br.com.riseedit.tools.FREEZE_MS)
        RiseAction.SPEED -> openTool(ui, EditorTool.Speed)
        RiseAction.ADD_TEXT -> ui.menu = listOf(ToolMenu.TEXT)
        RiseAction.ADD_ADJUSTMENT -> vm.addAdjustmentLayer()
        RiseAction.MARKER -> vm.addMarker()
        RiseAction.MARKER_NEXT -> vm.jumpMarker(true)
        RiseAction.MARKER_PREV -> vm.jumpMarker(false)
        RiseAction.ZOOM_IN -> ui.timeline.zoom(1.4)
        RiseAction.ZOOM_OUT -> ui.timeline.zoom(1 / 1.4)
        RiseAction.ZOOM_FIT -> ui.timeline.fit()
        RiseAction.TOGGLE_SNAP -> ui.timeline.toggleSnap()
        RiseAction.EXPORT -> if (clips.isNotEmpty()) ui.exportDialog = true
        RiseAction.IMPORT -> media.videos()
        RiseAction.FULLSCREEN -> ui.full = !ui.full
        RiseAction.CLOSE_PANEL -> when {
            ui.full -> ui.full = false
            ui.tool != null -> ui.tool = null
            ui.menu.isNotEmpty() -> ui.back(vm)
        }
        RiseAction.SHORTCUTS -> onShortcuts()
        RiseAction.PANEL_1 -> openTool(ui, EditorTool.Trim)
        RiseAction.PANEL_2 -> ui.menu = listOf(ToolMenu.AUDIO)
        RiseAction.PANEL_3 -> ui.menu = listOf(ToolMenu.TEXT)
        RiseAction.PANEL_4 -> ui.menu = listOf(ToolMenu.OVERLAY)
        RiseAction.PANEL_5 -> ui.menu = listOf(ToolMenu.COLOR)
        RiseAction.PANEL_6 -> ui.menu = listOf(ToolMenu.MARKERS)
    }
}

private fun openTool(ui: EditorUiState, tool: EditorTool) {
    ui.contextual = true
    ui.lastTool = tool
    ui.tool = tool
}
