package br.com.riseedit.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.sp
import br.com.riseedit.EditorState
import br.com.riseedit.RiseMuted
import br.com.riseedit.RiseSurface
import br.com.riseedit.RiseSurfaceHigh
import br.com.riseedit.RiseWhite

private val SnapGuide = androidx.compose.ui.graphics.Color(0xFFFFD54F)

@Composable
internal fun TimelineStage(
    ui: TimelineUiState,
    state: State<EditorState>,
    actions: TimelineActions,
    height: Dp,
    onAddToLane: (Int) -> Unit
) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current.density
    // Perf 0.22.2: rebuild entries only when what they depend on changes (not on every
    // playhead tick / scroll frame), and index each lane for O(log n) visible lookup.
    val s = state.value
    val built = androidx.compose.runtime.remember(
        s.project, s.layerId, s.selected, ui.trimDraft, ui.movingId, ui.movingUs
    ) { timelineEntries(s, ui) }
    val entries = rememberUpdatedState(built)
    val laneIndex = androidx.compose.runtime.remember(built) { LaneIndex.of(built) }
    val lanes = androidx.compose.runtime.remember(laneIndex, ui.extraLanes) {
        visibleLanes(laneIndex.rows, ui.extraLanes)
    }
    ui.lanes = lanes
    val duration = state.value.project.durationMs * 1000
    LaunchedEffect(duration) { ui.viewport = ui.viewport.fitted(duration).bounded(duration) }
    PlayheadTracker(ui, state, density, duration)
    val haptic = LocalHapticFeedback.current
    ui.onSnap = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    Row(Modifier.fillMaxWidth().height(height)) {
        Column(Modifier.width(64.dp)) {
            Box(Modifier.height(RULER_DP.dp))
            Column(Modifier.verticalScroll(scroll)) {
                lanes.forEach { row ->
                    LaneLabel(row, laneIndex.isEmpty(row)) { onAddToLane(row) }
                }
                AddLaneButton(ui, lanes)
            }
        }
        Box(
            Modifier.weight(1f).clipToBounds()
                .onSizeChanged {
                    ui.viewport = ui.viewport.resized(it.width.toDouble(), duration)
                }
                .semantics { contentDescription = "Timeline interativa" }
                .timelineMouse(ui, state, entries, density) { scroll.value }
                .timelineGestures(ui, state, entries, actions, density) { scroll.value }
        ) {
            Column {
                TimelineRuler(
                    ui.viewport,
                    duration,
                    state.value.project.markers.map { it.timeMs },
                    state.value.project.export.fps
                )
                TimelineTrackList(lanes, laneIndex, ui, actions, scroll)
            }
            PlayheadNeedle(ui, state, density, Modifier.matchParentSize())
            ClipContextMenu(ui, actions, density)
        }
    }
}

@Composable
private fun PlayheadTracker(
    ui: TimelineUiState,
    state: State<EditorState>,
    density: Float,
    duration: Long
) {
    val isPlaying = state.value.playing
    val currentPositionMs = state.value.positionMs
    LaunchedEffect(isPlaying, currentPositionMs) {
        if (isPlaying && ui.movingId == null && ui.trimDraft == null) {
            val playheadPx = ui.viewport.pxAtTime(currentPositionMs * 1000.0)
            val marginPx = 40.0 * density
            if (playheadPx > ui.viewport.widthPx - marginPx ||
                playheadPx < ui.viewport.edgeInsetPx
            ) {
                val targetOrigin = (currentPositionMs * 1000.0) -
                    (ui.viewport.widthPx / 3) * 1_000_000 / ui.viewport.pixelsPerSecond
                ui.viewport = ui.viewport.copy(originUs = targetOrigin).bounded(duration)
            }
        }
    }
}

@Composable
private fun PlayheadNeedle(
    ui: TimelineUiState,
    state: State<EditorState>,
    density: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        val x = ui.viewport.pxAtTime(state.value.positionMs * 1000.0).toFloat()
        val needle = br.com.riseedit.RiseAccent
        drawLine(needle, Offset(x, 0f), Offset(x, size.height), 2 * density)
        drawCircle(needle, 6 * density, Offset(x, 6 * density))
        drawCircle(androidx.compose.ui.graphics.Color.Black, 2.5f * density, Offset(x, 6 * density))
        ui.snapUs?.let {
            val snapX = ui.viewport.pxAtTime(it).toFloat()
            drawLine(SnapGuide, Offset(snapX, 0f), Offset(snapX, size.height), density)
        }
    }
}

@Composable
private fun TimelineTrackList(
    lanes: List<Int>,
    index: LaneIndex,
    ui: TimelineUiState,
    actions: TimelineActions,
    scroll: androidx.compose.foundation.ScrollState
) {
    Column(Modifier.verticalScroll(scroll)) {
        lanes.forEach { row ->
            Box(
                Modifier.fillMaxWidth().height(LANE_DP.dp).padding(vertical = 3.dp)
                    .background(RiseSurface, RoundedCornerShape(8.dp))
            ) {
                val v = ui.viewport
                index.visible(row, v.timeAtPx(0.0), v.timeAtPx(v.widthPx)).sortedBy { it.selected }
                    .forEach { androidx.compose.runtime.key(it.id) { TimelineClip(it, ui, actions) } }
            }
        }
        Box(Modifier.height(32.dp))
    }
}

@Composable
private fun LaneLabel(row: Int, empty: Boolean, onAdd: () -> Unit) {
    Row(
        Modifier.height(LANE_DP.dp).padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            trackNames[row],
            Modifier.weight(1f),
            fontSize = 10.sp,
            color = RiseMuted,
            maxLines = 1
        )
        if (empty) {
            Box(
                Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(RiseSurfaceHigh)
                    .clickable(onClick = onAdd)
                    .semantics { contentDescription = "Adicionar em ${trackNames[row]}" },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Outlined.Add, null, Modifier.size(14.dp), tint = RiseWhite) }
        }
    }
}

@Composable
private fun AddLaneButton(ui: TimelineUiState, lanes: List<Int>) {
    var open by remember { mutableStateOf(false) }
    val options = listOf(
        "Overlay" to 1..2,
        "Camada de ajuste" to LANE_ADJUST..LANE_ADJUST,
        "Texto" to 3..3,
        "Áudio" to 4..6
    )
        .mapNotNull { (label, kind) -> nextLane(kind, lanes)?.let { label to it } }
    if (options.isEmpty()) return
    Box {
        Row(
            Modifier.padding(top = 6.dp).clip(RoundedCornerShape(50)).background(RiseSurfaceHigh)
                .clickable { open = true }.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Add, null, Modifier.size(12.dp), tint = RiseWhite)
            Text("Faixa", fontSize = 10.sp, color = RiseWhite)
        }
        DropdownMenu(open, { open = false }) {
            options.forEach { (label, lane) ->
                DropdownMenuItem({ Text(label) }, onClick = {
                    ui.extraLanes = ui.extraLanes + lane
                    open = false
                })
            }
        }
    }
}

/** 0.22: right-click menu on a clip (split, duplicate, delete, copy/paste effects). */
@Composable
private fun ClipContextMenu(ui: TimelineUiState, actions: TimelineActions, density: Float) {
    val menu = ui.clipMenu
    Box(
        Modifier.offset(((menu?.xPx ?: 0f) / density).dp, ((menu?.yPx ?: 0f) / density).dp)
    ) {
        androidx.compose.material3.DropdownMenu(
            expanded = menu != null,
            onDismissRequest = { ui.clipMenu = null },
            containerColor = br.com.riseedit.RiseSurfaceHigh
        ) {
            val entry = menu?.entry ?: return@DropdownMenu
            fun item(label: String, keys: String, enabled: Boolean = true, run: (TimelineEntry) -> Unit) =
                @Composable {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { androidx.compose.material3.Text(label, color = RiseWhite) },
                        trailingIcon = {
                            androidx.compose.material3.Text(keys, color = br.com.riseedit.RiseMuted, fontSize = 12.sp)
                        },
                        enabled = enabled,
                        onClick = {
                            ui.clipMenu = null
                            run(entry)
                        }
                    )
                }
            val main = entry.mainIndex != null
            item("Dividir", "C") { actions.split(it) }()
            item("Duplicar", "Alt+D", enabled = main) { actions.duplicate(it) }()
            item("Excluir", "Del") { actions.delete(it) }()
            item("Copiar efeitos", "", enabled = main) { actions.copyEffects(it) }()
            item("Colar efeitos", "Ctrl+Alt+V", enabled = main) { actions.pasteEffects(it) }()
        }
    }
}
