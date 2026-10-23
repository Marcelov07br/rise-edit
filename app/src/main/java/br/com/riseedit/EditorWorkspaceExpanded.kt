package br.com.riseedit

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.riseedit.text.TextEditorSheet
import br.com.riseedit.tools.ToolPanel

/** 0.22: window size class from the activity (recomputed on DeX/multi-window resize). */
val LocalWindowSizeClass = staticCompositionLocalOf<WindowSizeClass?> { null }

/** Expanded (≥ 840 dp: tablet landscape, DeX window) uses the side-by-side editor. */
@Composable
@Suppress("VisibleForTests")
@OptIn(androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi::class)
internal fun isExpanded(width: Dp, height: Dp): Boolean {
    val c = LocalWindowSizeClass.current ?: WindowSizeClass.calculateFromSize(DpSize(width, height))
    return c.widthSizeClass == WindowWidthSizeClass.Expanded
}

/**
 * DeX / tablet editor: side tool column | (preview · inspector panel) over transport and a
 * taller timeline that shows more tracks. Same state objects as the phone layout, so resizing the
 * window just swaps the arrangement — nothing is recreated.
 */
@Composable
internal fun EditorWorkspaceExpanded(
    vm: EditorViewModel,
    state: EditorState,
    ui: EditorUiState,
    media: EditorMediaActions,
    height: Dp
) {
    val enabled = state.busy == null
    val level = toolbarLevel(vm, state, ui, media)
    BackHandler(enabled = level.leading != null) { ui.back(vm) }
    ExpandedEditorLayout(
        tools = {
            AnimatedContent(
                ui.menu.size to (level.leading != null),
                transitionSpec = {
                    (slideInVertically(risePanelSpring) { it / 8 } + fadeIn(tween(160))) togetherWith
                        fadeOut(tween(90))
                },
                label = "sideTools"
            ) { target -> key(target) { SideToolColumn(level.items, leading = level.leading) } }
        },
        preview = { m ->
            if (ui.full) {
                Box(m)
            } else {
                PreviewPanel(
                    vm,
                    state,
                    m,
                    editable = true,
                    mainSelected = ui.contextual,
                    onSelect = { main -> ui.contextual = main },
                    textDraft = ui.textDraft,
                    onDraftTransform = { ui.textDraft = it },
                    pickColor = ui.eyedropper ?: br.com.riseedit.tools.ColorTools.eyedropper
                )
            }
        },
        inspector = { InspectorPanel(vm, state, ui, media, height) },
        transport = { TransportBar(vm, state, enabled, onFullscreen = { ui.full = true }) },
        timeline = {
            TimelinePanel(
                vm,
                state,
                trackAreaHeight = expandedTrackHeight(height),
                onSelect = { ui.contextual = true },
                onAddToLane = { lane -> addToLaneExpanded(lane, ui, media, vm) },
                onKeyframe = {
                    ui.contextual = true
                    ui.lastTool = br.com.riseedit.tools.EditorTool.Keyframe
                    ui.tool = br.com.riseedit.tools.EditorTool.Keyframe
                },
                remote = ui.timeline
            )
        }
    )
}

/** Taller track area on big windows: shows ~2× the phone's lanes. */
internal fun expandedTrackHeight(height: Dp): Dp = (height * 0.36f).coerceIn(220.dp, 420.dp)

/** The DeX/tablet arrangement itself (slots), shared by the editor and its screenshot test. */
@Composable
internal fun ExpandedEditorLayout(
    tools: @Composable () -> Unit,
    preview: @Composable (Modifier) -> Unit,
    inspector: @Composable () -> Unit,
    transport: @Composable () -> Unit,
    timeline: @Composable () -> Unit
) {
    Row(Modifier.fillMaxSize()) {
        tools()
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(
                Modifier.weight(1f).fillMaxWidth().padding(end = 12.dp, top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                preview(Modifier.weight(1f).fillMaxHeight())
                Box(Modifier.width(380.dp).fillMaxHeight()) { inspector() }
            }
            transport()
            Box(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, bottom = 8.dp)) { timeline() }
        }
    }
}

@Composable
private fun InspectorPanel(
    vm: EditorViewModel,
    state: EditorState,
    ui: EditorUiState,
    media: EditorMediaActions,
    height: Dp
) {
    Box(Modifier.fillMaxSize()) {
        val draft = ui.textDraft
        val tool = ui.tool
        AnimatedContent(
            Triple(draft != null, tool, draft?.id),
            transitionSpec = { fadeIn(riseSpring()) togetherWith fadeOut(tween(100)) },
            label = "inspector"
        ) { (editingText, t, _) ->
            when {
                editingText && ui.textDraft != null -> Column { TextEditorSheet(vm, ui, ui.textDraft!!) }
                t != null -> ToolSheet(t.title, height * 0.5f, onClose = { ui.tool = null }) {
                    ToolPanel(t, vm, state, addAudio = media.audioFile)
                }
                else -> EmptyInspector(state)
            }
        }
    }
}

@Composable
internal fun EmptyInspector(state: EditorState) {
    val clip = state.project.clips.getOrNull(state.selected)
    Column(
        Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)).background(RiseSurface)
            .border(1.dp, RiseOutline, RoundedCornerShape(22.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Inspetor", color = RiseWhite, fontSize = 16.sp)
        Text(
            clip?.let { "${it.name} · ${time(it.durationMs)}" } ?: "Nenhum clipe selecionado",
            color = RiseMuted,
            fontSize = 13.sp
        )
        Box(Modifier.weight(1f))
        Text(
            "Escolha uma ferramenta na coluna ao lado.\nZ / D: ripple trim · C: dividir · Meta+/: atalhos",
            color = RiseMuted,
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.Start)
        )
    }
}

private fun addToLaneExpanded(lane: Int, ui: EditorUiState, media: EditorMediaActions, vm: EditorViewModel) {
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
