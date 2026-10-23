package br.com.riseedit.timeline

import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import br.com.riseedit.EditorState
import kotlin.math.floor
import kotlin.math.pow

/**
 * 0.22 mouse/trackpad on the timeline (DeX): hover highlights the clip under the pointer,
 * right click opens the clip menu, the wheel scrolls the timeline horizontally and Ctrl+wheel (or a
 * trackpad pinch reported as Ctrl+scroll) zooms around the pointer.
 */
internal fun Modifier.timelineMouse(
    ui: TimelineUiState,
    state: State<EditorState>,
    entries: State<List<TimelineEntry>>,
    density: Float,
    scrollY: () -> Int
): Modifier = pointerInput(ui, density) {
    fun entryAt(x: Float, y: Float): TimelineEntry? {
        val lane = floor((y - RULER_DP * density + scrollY()) / (LANE_DP * density)).toInt()
        val row = ui.lanes.getOrElse(lane) { -1 }
        val t = ui.viewport.timeAtPx(x.toDouble())
        return entries.value.lastOrNull { it.row == row && t >= it.startUs && t < it.endUs }
    }
    awaitPointerEventScope {
        while (true) {
            val e = awaitPointerEvent(PointerEventPass.Initial)
            val c = e.changes.firstOrNull() ?: continue
            val duration = state.value.project.durationMs * 1000
            when (e.type) {
                PointerEventType.Scroll -> {
                    val d = c.scrollDelta
                    if (e.keyboardModifiers.isCtrlPressed) {
                        ui.viewport = ui.viewport.zoomed(1.15.pow(-d.y.toDouble()), c.position.x.toDouble(), duration)
                    } else {
                        // Vertical wheel pans time; Shift+wheel or trackpad horizontal also pan.
                        val delta = if (d.x != 0f) d.x else d.y
                        val step = if (e.keyboardModifiers.isShiftPressed) 120f else 60f
                        ui.viewport = ui.viewport.panned((delta * step * density).toDouble(), duration)
                    }
                    c.consume()
                }
                PointerEventType.Move, PointerEventType.Enter ->
                    if (c.type == PointerType.Mouse || c.type == PointerType.Stylus) {
                        ui.hoverId = entryAt(c.position.x, c.position.y)?.id
                    }
                PointerEventType.Exit -> ui.hoverId = null
                PointerEventType.Press -> if (e.buttons.isSecondaryPressed) {
                    entryAt(c.position.x, c.position.y)?.let { ui.clipMenu = ClipMenu(it, c.position.x, c.position.y) }
                    c.consume()
                }
            }
        }
    }
}
