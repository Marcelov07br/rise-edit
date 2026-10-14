package br.com.riseedit.timeline

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import br.com.riseedit.EditorState
import br.com.riseedit.core.editing.AudioTrimSession
import br.com.riseedit.core.editing.ClipTrimSession
import br.com.riseedit.core.editing.OverlayTrimSession
import br.com.riseedit.core.editing.TrimEdge
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

private const val SNAP_DP = 10f

private enum class GestureMode { TAP, TRIM, SCRUB, PAN, MOVE, VERTICAL, TRANSFORM }

private class GestureDraft(
    val down: PointerInputChange,
    val entry: TimelineEntry?,
    var mode: GestureMode,
    val scale: Double,
    val origin: Double,
    val edgeUs: Double = 0.0,
    val targets: List<Double> = emptyList(),
    val thresholdUs: Double = 0.0
)

internal fun Modifier.timelineGestures(
    ui: TimelineUiState,
    state: State<EditorState>,
    entries: State<List<TimelineEntry>>,
    actions: TimelineActions,
    density: Float,
    scrollY: () -> Int
): Modifier = pointerInput(ui, state.value.project.id, state.value.busy, density) {
    try {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (state.value.busy == null) {
                val draft = beginGesture(
                    down,
                    entries.value,
                    ui,
                    actions,
                    density,
                    scrollY(),
                    state.value.positionMs * 1000.0
                )
                var event: PointerEvent
                do {
                    event = awaitPointerEvent(PointerEventPass.Initial)
                    updateGesture(event, draft, ui, actions, state.value.project.durationMs * 1000)
                } while (event.changes.any { it.pressed })
                finishGesture(draft, ui, actions)
            }
            ui.clearGesture()
            actions.endScrub()
        }
    } finally {
        ui.clearGesture()
        actions.endScrub()
    }
}

private fun beginGesture(
    down: PointerInputChange,
    entries: List<TimelineEntry>,
    ui: TimelineUiState,
    actions: TimelineActions,
    density: Float,
    scrollY: Int,
    playheadUs: Double
): GestureDraft {
    val lane = floor((down.position.y - RULER_DP * density + scrollY) / (LANE_DP * density)).toInt()
    val row = ui.lanes.getOrElse(lane) { -1 }
    val selected = entries.firstOrNull { it.row == row && it.selected && trimmable(it) }
    val edge = selected?.let { trimEdge(it, ui, down.position.x, 22 * density) }
    val time = ui.viewport.timeAtPx(down.position.x.toDouble())
    val entry = entries.lastOrNull { it.row == row && time >= it.startUs && time < it.endUs }
    val mode = initialMode(down, selected, edge, entry, ui, actions, density, time)
    val trimmed = selected?.takeIf { mode == GestureMode.TRIM }
    val edgeUs = when {
        trimmed == null -> 0.0
        edge == TrimEdge.START -> trimmed.startUs
        else -> trimmed.endUs
    }
    val exclude = trimmed?.id ?: entry?.id
    val targets = entries.filter { it.id != exclude }.flatMap { listOf(it.startUs, it.endUs) } +
        listOf(0.0, playheadUs)
    val pps = ui.viewport.pixelsPerSecond
    return GestureDraft(
        down,
        entry,
        mode,
        pps,
        ui.viewport.originUs,
        edgeUs,
        if (mode == GestureMode.SCRUB) targets - playheadUs else targets,
        SNAP_DP * density * 1_000_000 / pps
    )
}

@Suppress("LongParameterList")
private fun initialMode(
    down: PointerInputChange,
    selected: TimelineEntry?,
    edge: TrimEdge?,
    entry: TimelineEntry?,
    ui: TimelineUiState,
    actions: TimelineActions,
    density: Float,
    time: Double
): GestureMode {
    val diamond = entry?.let { keyDiamond(it, ui, down.position.x, 14 * density) }
    return when {
        edge != null && selected != null -> {
            ui.trimDraft = startTrim(selected, edge)
            actions.beginTrim()
            down.consume()
            GestureMode.TRIM
        }
        diamond != null && entry != null -> {
            actions.openKeyframe(entry, diamond, ui)
            down.consume()
            GestureMode.TAP
        }
        down.position.y < RULER_DP * density -> {
            actions.beginScrub()
            actions.seek(time)
            down.consume()
            GestureMode.SCRUB
        }
        else -> GestureMode.TAP
    }
}

private fun trimmable(entry: TimelineEntry) =
    entry.mainIndex != null || entry.overlay != null || entry.audio != null

private fun startTrim(entry: TimelineEntry, edge: TrimEdge): TrimDraft = when {
    entry.mainIndex != null && entry.clip != null ->
        TrimDraft.Main(ClipTrimSession(entry.clip, edge), entry.clip)
    entry.audio != null ->
        TrimDraft.Audio(AudioTrimSession(entry.audio, edge), entry.audio)
    entry.overlay != null ->
        TrimDraft.Overlay(OverlayTrimSession(entry.overlay, edge), entry.overlay)
    else -> error("not trimmable")
}

private fun keyDiamond(entry: TimelineEntry, ui: TimelineUiState, x: Float, radius: Float): Long? {
    if (entry.keyOffsetsUs.isEmpty()) return null
    return entry.keyOffsetsUs.minByOrNull { us ->
        kotlin.math.abs(ui.viewport.pxAtTime(entry.startUs + us) - x)
    }?.takeIf { us ->
        kotlin.math.abs(ui.viewport.pxAtTime(entry.startUs + us) - x) <= radius
    }?.let { (it / 1000).roundToLong() }
}

private fun trimEdge(
    entry: TimelineEntry,
    ui: TimelineUiState,
    x: Float,
    radius: Float
): TrimEdge? {
    val left = abs(ui.viewport.pxAtTime(entry.startUs) - x)
    val right = abs(ui.viewport.pxAtTime(entry.endUs) - x)
    return when {
        minOf(left, right) > radius -> null
        left <= right -> TrimEdge.START
        else -> TrimEdge.END
    }
}

private fun AwaitPointerEventScope.updateGesture(
    event: PointerEvent,
    draft: GestureDraft,
    ui: TimelineUiState,
    actions: TimelineActions,
    durationUs: Long
) {
    if (event.changes.all { !it.pressed && it.isConsumed }) {
        ui.clearGesture()
        draft.mode = GestureMode.TRANSFORM
        return
    }
    if (event.changes.count { it.pressed } >= 2) {
        ui.clearGesture()
        actions.endScrub()
        draft.mode = GestureMode.TRANSFORM
        val focus = event.calculateCentroid(useCurrent = false)
        if (focus != Offset.Unspecified) {
            ui.viewport =
                ui.viewport.zoomed(event.calculateZoom().toDouble(), focus.x.toDouble(), durationUs)
                    .panned(event.calculatePan().x.toDouble(), durationUs)
        }
        event.changes.forEach { it.consume() }
        return
    }
    val change = event.changes.firstOrNull { it.id == draft.down.id } ?: return
    val delta = change.position - draft.down.position
    resolveMode(
        draft,
        change,
        delta,
        ui,
        actions,
        viewConfiguration.touchSlop,
        viewConfiguration.longPressTimeoutMillis
    )
    applyMovement(draft, change, delta, ui, actions, durationUs)
    if (draft.mode !in listOf(GestureMode.TAP, GestureMode.VERTICAL)) change.consume()
}

private fun resolveMode(
    draft: GestureDraft,
    change: PointerInputChange,
    delta: Offset,
    ui: TimelineUiState,
    actions: TimelineActions,
    slop: Float,
    longPressMs: Long
) {
    if (draft.mode != GestureMode.TAP || delta.getDistance() < slop) return
    draft.mode = when {
        abs(delta.y) > abs(delta.x) -> GestureMode.VERTICAL
        draft.entry != null && change.uptimeMillis - draft.down.uptimeMillis >= longPressMs -> {
            actions.select(draft.entry)
            ui.movingId = draft.entry.id
            GestureMode.MOVE
        }
        else -> GestureMode.PAN
    }
}

private fun applyMovement(
    draft: GestureDraft,
    change: PointerInputChange,
    delta: Offset,
    ui: TimelineUiState,
    actions: TimelineActions,
    durationUs: Long
) {
    when (draft.mode) {
        GestureMode.TRIM -> {
            val raw = draft.edgeUs + delta.x * 1_000_000 / draft.scale
            val snapped = ui.magnet(raw, draft.targets, draft.thresholdUs)
            val deltaUs = snapped - draft.edgeUs
            ui.trimDraft = when (val t = ui.trimDraft) {
                is TrimDraft.Main -> t.copy(draft = t.session.dragged(deltaUs))
                is TrimDraft.Overlay -> t.copy(draft = t.session.dragged(deltaUs))
                is TrimDraft.Audio -> t.copy(draft = t.session.dragged(deltaUs))
                null -> null
            }
        }
        GestureMode.SCRUB -> actions.seek(
            ui.magnet(
                ui.viewport.timeAtPx(change.position.x.toDouble()),
                draft.targets,
                draft.thresholdUs
            )
        )
        GestureMode.MOVE -> draft.entry?.let {
            val raw = it.startUs + delta.x * 1_000_000 / draft.scale
            val length = it.durationUs.toDouble()
            ui.movingUs = ui.magnet(raw, draft.targets, draft.thresholdUs, length) - it.startUs
        }
        GestureMode.PAN -> ui.viewport = ui.viewport.copy(
            originUs = draft.origin - delta.x * 1_000_000 / draft.scale
        ).bounded(durationUs)
        else -> Unit
    }
}

private fun finishGesture(draft: GestureDraft, ui: TimelineUiState, actions: TimelineActions) {
    when (draft.mode) {
        GestureMode.TRIM -> actions.commitTrim(ui)
        GestureMode.TAP -> draft.entry?.let(actions::select)
        GestureMode.MOVE -> draft.entry?.let {
            actions.move(it, it.startUs + ui.movingUs, false)
        }
        else -> Unit
    }
}
