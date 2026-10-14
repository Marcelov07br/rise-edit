package br.com.riseedit.edit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.riseedit.EditorState
import br.com.riseedit.EditorViewModel
import br.com.riseedit.MediaThumbnail
import br.com.riseedit.RiseBlack
import br.com.riseedit.RiseWhite
import br.com.riseedit.core.TransformKeyframe
import br.com.riseedit.core.TransformPath
import br.com.riseedit.core.editing.FrameSnap
import kotlin.math.roundToInt

/** Live state of a direct manipulation; the main-clip draft also drives the video transform. */
@Stable
internal class PreviewEditState {
    var draftId by mutableStateOf<String?>(null)
    var draft by mutableStateOf<TransformKeyframe?>(null)
    var guideX by mutableStateOf<FrameSnap.Guide?>(null)
    var guideY by mutableStateOf<FrameSnap.Guide?>(null)

    fun draftFor(id: String?) = draft.takeIf { id != null && draftId == id }

    fun clear() {
        draftId = null
        draft = null
        guideX = null
        guideY = null
    }
}

/**
 * Tactile editing over the output frame: tap selects, one finger moves, two fingers scale and
 * rotate, double tap resets. Snaps to the frame center/edges and to right angles with a haptic
 * tick. Overlays and texts are drawn as live stand-ins in the fast preview.
 */
@Composable
internal fun BoxScope.PreviewEditLayer(
    vm: EditorViewModel,
    state: EditorState,
    edit: PreviewEditState,
    items: List<PreviewItem>,
    selectedId: String?,
    fast: Boolean,
    onSelect: (Boolean) -> Unit
) {
    val density = LocalDensity.current.density
    val haptic = LocalHapticFeedback.current
    val latestItems = rememberUpdatedState(items)
    val latestSelected = rememberUpdatedState(selectedId)
    val enabled = state.busy == null
    val actions = PreviewActions(
        select = { item ->
            when (item) {
                is PreviewItem.Main -> vm.focus(item.index)
                else -> vm.selectLayer(item.id)
            }
            onSelect(item is PreviewItem.Main)
        },
        commit = { result ->
            when (result) {
                is PreviewEdit.ClipEdit -> vm.clipSettings(result.clip)
                is PreviewEdit.OverlayEdit -> vm.overlay(result.layer)
                is PreviewEdit.TextEdit -> vm.text(result.layer)
            }
        },
        togglePlay = vm::togglePlay,
        tick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
        resetTick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    ).let { rememberUpdatedState(it) }
    Box(
        Modifier.fillMaxSize()
            .semantics { contentDescription = "Prévia editável" }
            .previewTaps(enabled, latestItems, latestSelected) { actions.value }
            .previewTransforms(enabled, latestItems, latestSelected, edit) { actions.value }
    ) {
        items.filter { it !is PreviewItem.Main && (fast || edit.draftId == it.id) }
            .forEach { StandIn(it, edit.draftFor(it.id) ?: it.transform) }
        items.firstOrNull { it.id == selectedId }?.let { item ->
            SelectionBox(item, edit.draftFor(item.id) ?: item.transform)
            KeyframeButton(item) { actions.value.commit(item.toggleKey()) }
        }
        Guides(edit, density)
    }
}

private fun ndcX(px: Float, width: Float) = (px / width * 2 - 1).toDouble()

private fun ndcY(px: Float, height: Float) = (1 - px / height * 2).toDouble()

private fun hitTest(items: List<PreviewItem>, p: Offset, width: Int, height: Int): PreviewItem? {
    val aspect = width.toFloat() / height
    return items.lastOrNull {
        it.contains(ndcX(p.x, width.toFloat()), ndcY(p.y, height.toFloat()), aspect)
    }
}

/** Applies frame and angle snapping; returns the snapped transform and whether a new snap engaged. */
private fun snap(
    item: PreviewItem,
    raw: TransformKeyframe,
    edit: PreviewEditState
): Pair<TransformKeyframe, Boolean> {
    val (hx, hy) = halfExtents(item, raw)
    val sx = FrameSnap.axis(raw.x.toDouble(), hx)
    val sy = FrameSnap.axis(raw.y.toDouble(), hy)
    val (angle, angleSnapped) = FrameSnap.angle(raw.rotation)
    val engaged =
        (sx.guide != null && sx.guide != edit.guideX) ||
            (sy.guide != null && sy.guide != edit.guideY) ||
            (angleSnapped && edit.draft?.rotation != angle)
    edit.guideX = sx.guide
    edit.guideY = sy.guide
    return raw.copy(x = sx.center.toFloat(), y = sy.center.toFloat(), rotation = angle) to engaged
}

@Composable
private fun BoxScope.StandIn(item: PreviewItem, t: TransformKeyframe) {
    ItemFrame(item, t) { _, h ->
        when (item) {
            is PreviewItem.Overlay -> MediaThumbnail(
                item.layer.clip.effectiveUri(true),
                item.layer.clip.sourceAt((item.keyTimeMs / 500) * 500),
                Modifier.fillMaxSize().graphicsLayer { alpha = t.opacity }
            )
            is PreviewItem.Text -> {
                val lineHeightPx = h / PreviewItem.wrappedLines(item.layer.text).size
                Text(
                    PreviewItem.wrappedLines(item.layer.text).joinToString("\n"),
                    color = Color(item.layer.color),
                    fontWeight = FontWeight.Bold,
                    fontSize = (
                        lineHeightPx / 1.2f / LocalDensity.current.fontScale /
                            LocalDensity.current.density
                        ).sp,
                    lineHeight = (
                        lineHeightPx / LocalDensity.current.fontScale /
                            LocalDensity.current.density
                        ).sp,
                    maxLines = 12,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            is PreviewItem.Main -> Unit
        }
    }
}

/** Positions content at the item's transform: center, scaled size and rotation. */
@Composable
private fun BoxScope.ItemFrame(
    item: PreviewItem,
    t: TransformKeyframe,
    content: @Composable BoxScope.(Float, Float) -> Unit
) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.matchParentSize()) {
        val frameW = constraints.maxWidth.toFloat()
        val frameH = constraints.maxHeight.toFloat()
        val (fw, fh) = item.baseSize
        val w = (fw * t.scale * frameW).coerceAtLeast(1f)
        val h = (fh * t.scale * frameH).coerceAtLeast(1f)
        val cx = (t.x + 1) / 2 * frameW
        val cy = (1 - t.y) / 2 * frameH
        val density = LocalDensity.current.density
        Box(
            Modifier
                .offset { IntOffset((cx - w / 2).roundToInt(), (cy - h / 2).roundToInt()) }
                .size((w / density).dp, (h / density).dp)
                .graphicsLayer { rotationZ = -t.rotation }
        ) { content(w, h) }
    }
}

@Composable
private fun BoxScope.SelectionBox(item: PreviewItem, t: TransformKeyframe) {
    ItemFrame(item, t) { _, _ ->
        Box(Modifier.fillMaxSize().border(1.5.dp, RiseWhite, RoundedCornerShape(2.dp)))
        listOf(
            Alignment.TopStart,
            Alignment.TopEnd,
            Alignment.BottomStart,
            Alignment.BottomEnd
        ).forEach {
            Box(
                Modifier.align(it).size(12.dp).offset(
                    x = if (it == Alignment.TopStart ||
                        it == Alignment.BottomStart
                    ) {
                        (-5).dp
                    } else {
                        5.dp
                    },
                    y = if (it == Alignment.TopStart || it == Alignment.TopEnd) (-5).dp else 5.dp
                ).clip(CircleShape).background(RiseWhite).border(1.dp, RiseBlack, CircleShape)
            )
        }
    }
}

@Composable
private fun BoxScope.KeyframeButton(item: PreviewItem, onToggle: () -> Unit) {
    val keyed = TransformPath.hasKeyAt(item.keyframes, item.keyTimeMs)
    Box(
        Modifier.align(Alignment.BottomEnd).padding(8.dp).size(34.dp).clip(CircleShape)
            .background(if (keyed) RiseWhite else RiseBlack.copy(alpha = 0.7f))
            .border(1.dp, RiseWhite, CircleShape)
            .clickable(onClick = onToggle)
            .semantics {
                contentDescription =
                    if (keyed) "Remover keyframe" else "Adicionar keyframe"
            },
        contentAlignment = Alignment.Center
    ) { Text("◆", color = if (keyed) RiseBlack else RiseWhite, fontSize = 14.sp) }
}

@Composable
private fun BoxScope.Guides(edit: PreviewEditState, density: Float) {
    if (edit.guideX == null && edit.guideY == null) return
    Canvas(Modifier.matchParentSize()) {
        val dash = PathEffect.dashPathEffect(floatArrayOf(6 * density, 4 * density))
        val color = RiseWhite.copy(alpha = 0.8f)
        if (edit.guideX == FrameSnap.Guide.CENTER) {
            drawLine(
                color,
                Offset(size.width / 2, 0f),
                Offset(size.width / 2, size.height),
                density,
                pathEffect = dash
            )
        }
        if (edit.guideY == FrameSnap.Guide.CENTER) {
            drawLine(
                color,
                Offset(0f, size.height / 2),
                Offset(size.width, size.height / 2),
                density,
                pathEffect = dash
            )
        }
        if (edit.guideX ==
            FrameSnap.Guide.MIN
        ) {
            drawLine(
                color,
                Offset(1f, 0f),
                Offset(1f, size.height),
                2 * density
            )
        }
        if (edit.guideX == FrameSnap.Guide.MAX) {
            drawLine(
                color,
                Offset(size.width - 1, 0f),
                Offset(size.width - 1, size.height),
                2 * density
            )
        }
        if (edit.guideY ==
            FrameSnap.Guide.MAX
        ) {
            drawLine(color, Offset(0f, 1f), Offset(size.width, 1f), 2 * density)
        }
        if (edit.guideY == FrameSnap.Guide.MIN) {
            drawLine(
                color,
                Offset(0f, size.height - 1),
                Offset(size.width, size.height - 1),
                2 * density
            )
        }
    }
}

private class PreviewActions(
    val select: (PreviewItem) -> Unit,
    val commit: (PreviewEdit) -> Unit,
    val togglePlay: () -> Unit,
    val tick: () -> Unit,
    val resetTick: () -> Unit
)

private fun Modifier.previewTaps(
    enabled: Boolean,
    items: State<List<PreviewItem>>,
    selected: State<String?>,
    actionsOf: () -> PreviewActions
) = pointerInput(enabled) {
    if (!enabled) return@pointerInput
    detectTapGestures(
        onTap = { p ->
            val hit = hitTest(items.value, p, size.width, size.height)
            if (hit != null) actionsOf().select(hit) else actionsOf().togglePlay()
        },
        onDoubleTap = { p ->
            val hit = hitTest(items.value, p, size.width, size.height)
            if (hit != null && hit.id == selected.value) {
                actionsOf().commit(hit.reset())
                actionsOf().resetTick()
            }
        }
    )
}

private fun Modifier.previewTransforms(
    enabled: Boolean,
    items: State<List<PreviewItem>>,
    selected: State<String?>,
    edit: PreviewEditState,
    actionsOf: () -> PreviewActions
) = pointerInput(enabled) {
    if (!enabled) return@pointerInput
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val width = size.width.toFloat()
        val height = size.height.toFloat()
        val current = items.value
        val target = grabTarget(current, selected.value, down.position, size.width, size.height)
            ?: return@awaitEachGesture
        var raw = target.transform
        var moved = false
        var travel = 0f
        do {
            val event = awaitPointerEvent()
            val pan = event.calculatePan()
            travel += pan.getDistance()
            val multi = event.changes.count { it.pressed } > 1
            if (!moved && (travel > viewConfiguration.touchSlop || multi)) {
                moved = true
                if (target.id != selected.value) actionsOf().select(target)
                edit.draftId = target.id
            }
            if (moved) {
                raw = raw.copy(
                    x = raw.x + pan.x * 2 / width,
                    y = raw.y - pan.y * 2 / height,
                    scale = (raw.scale * event.calculateZoom()).coerceIn(0.05f, 8f),
                    rotation = raw.rotation - event.calculateRotation()
                )
                val snapped = snap(target, raw, edit)
                if (snapped.second) actionsOf().tick()
                edit.draft = snapped.first
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
        } while (event.changes.any { it.pressed })
        val result = edit.draft
        if (moved && result != null) actionsOf().commit(target.apply(result))
        edit.clear()
    }
}

/** The selected item wins when touched (even under others); otherwise the topmost hit. */
private fun grabTarget(
    items: List<PreviewItem>,
    selectedId: String?,
    p: Offset,
    width: Int,
    height: Int
): PreviewItem? {
    val aspect = width.toFloat() / height
    val x = ndcX(p.x, width.toFloat())
    val y = ndcY(p.y, height.toFloat())
    return items.firstOrNull { it.id == selectedId && it.contains(x, y, aspect) }
        ?: hitTest(items, p, width, height)
}
