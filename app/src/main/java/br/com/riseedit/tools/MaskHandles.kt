package br.com.riseedit.tools

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import br.com.riseedit.core.mask.Mask
import br.com.riseedit.core.mask.MaskGeom
import br.com.riseedit.core.mask.MaskMath
import br.com.riseedit.core.mask.MaskType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Which part of the selected mask a drag grabbed. */
internal enum class MaskGrab { MOVE, SIZE, HEIGHT, ROTATE, FEATHER }

/**
 * Pure handle geometry in pixels for a mask drawn in a [w]×[h] box (y down). Local axes follow
 * the mask rotation; [local] / [screen] convert between them (same convention as [MaskMath]).
 */
internal class MaskFrame(val g: MaskGeom, val w: Float, val h: Float, val knob: Float) {
    val center = Offset(g.cx * w, g.cy * h)
    val hx = max(g.w * w / 2f, 1f)
    val hy = max(g.h * h / 2f, 1f)
    private val a = g.rotation * (PI.toFloat() / 180f)
    private val ca = cos(a)
    private val sa = sin(a)
    val featherPx = g.feather * h * 0.5f

    fun screen(lx: Float, ly: Float) = Offset(
        center.x + lx * ca - ly * sa,
        center.y + lx * sa + ly * ca
    )
    fun local(p: Offset): Offset {
        val dx = p.x - center.x
        val dy = p.y - center.y
        return Offset(dx * ca + dy * sa, -dx * sa + dy * ca)
    }

    fun sizeHandle(type: MaskType) = if (type == MaskType.LINEAR) screen(0f, hy) else screen(hx, hy)
    fun rotateHandle() = screen(0f, -hy - knob)
    fun featherHandle(type: MaskType) = when (type) {
        MaskType.RADIAL -> screen(hx * (1f - g.feather.coerceIn(0f, 1f)), 0f)
        MaskType.LINEAR -> screen(hx.coerceAtMost(w) * 0.5f, hy + featherPx + knob * 0.5f)
        else -> screen(hx + featherPx + knob * 0.5f, 0f)
    }

    /** Handle under [p] (radius [touch]); null = outside handles (body test is separate). */
    fun grab(type: MaskType, p: Offset, touch: Float): MaskGrab? = when {
        (p - rotateHandle()).getDistance() < touch -> MaskGrab.ROTATE
        (p - sizeHandle(type)).getDistance() < touch -> if (type ==
            MaskType.LINEAR
        ) {
            MaskGrab.HEIGHT
        } else {
            MaskGrab.SIZE
        }
        (p - featherHandle(type)).getDistance() < touch -> MaskGrab.FEATHER
        else -> null
    }

    /** New geometry for a drag of [grab] with the pointer now at [p] (from the geometry at drag start). */
    fun drag(grab: MaskGrab, type: MaskType, p: Offset, moved: Offset): MaskGeom = when (grab) {
        MaskGrab.MOVE -> g.copy(cx = g.cx + moved.x / w, cy = g.cy + moved.y / h)
        MaskGrab.SIZE -> local(p).let { l -> g.copy(w = 2f * abs(l.x) / w, h = 2f * abs(l.y) / h) }
        MaskGrab.HEIGHT -> local(p).let { l -> g.copy(h = 2f * abs(l.y) / h) }
        MaskGrab.ROTATE -> {
            val d = p - center
            g.copy(rotation = (atan2(d.y, d.x) * 180f / PI.toFloat()) + 90f)
        }
        MaskGrab.FEATHER -> local(p).let { l ->
            when (type) {
                MaskType.RADIAL -> g.copy(feather = (1f - l.x / hx).coerceIn(0f, 1f))
                MaskType.LINEAR -> g.copy(
                    feather = ((l.y - hy - knob * 0.5f) * 2f / h).coerceIn(0f, 1f)
                )
                else -> g.copy(feather = ((l.x - hx - knob * 0.5f) * 2f / h).coerceIn(0f, 1f))
            }
        }
    }.clamped()

    companion object {
        /** Rotation snaps to multiples of 45° within ±[deg]. */
        fun snapAngle(r: Float, deg: Float = 3f): Pair<Float, Boolean> {
            val n = (r / 45f).roundToInt() * 45f
            return if (abs(r - n) < deg) n to true else r to false
        }
    }
}

/**
 * Direct manipulation of the masks of the Máscara tool on the preview. Local coords = the target's
 * frame (clip video box, output frame for layers, overlay box). Drag body = move, corner = size,
 * top knob = rotate (snaps to 45° with a light haptic), side knob = feather; tap another outline
 * to select it. Handles grow with a low-bounce spring while held.
 */
@Composable
internal fun MaskHandles(
    masks: List<Mask>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onGeom: (String, MaskGeom) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var active by remember { mutableStateOf<MaskGrab?>(null) }
    val grow by animateFloatAsState(
        if (active != null) 1.35f else 1f,
        spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "maskHandle"
    )
    val currentMasks by rememberUpdatedState(masks)
    val currentSel by rememberUpdatedState(selectedId)
    val currentGeom by rememberUpdatedState(onGeom)
    val currentSelect by rememberUpdatedState(onSelect)
    val feel = MaskFeel(
        masks = { currentMasks },
        selected = { currentSel },
        haptic = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
        onSelect = { currentSelect(it) },
        onGeom = { id, g -> currentGeom(id, g) },
        onGrab = { active = it }
    )
    Box(
        modifier.fillMaxSize().maskGestures(feel)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val knob = 28.dp.toPx()
            masks.filter { it.isGeometric }.forEach { m ->
                val f = MaskFrame(m.geom, size.width, size.height, knob)
                val sel = m.id == selectedId
                drawOutline(m, f, sel)
                if (sel) drawHandles(m, f, grow, active)
            }
        }
    }
}

/** Live inputs/outputs of the mask gestures (lambdas read the latest composition values). */
internal class MaskFeel(
    val masks: () -> List<Mask>,
    val selected: () -> String?,
    val haptic: () -> Unit,
    val onSelect: (String) -> Unit,
    val onGeom: (String, MaskGeom) -> Unit,
    val onGrab: (MaskGrab?) -> Unit
)

private fun hits(m: Mask, p: Offset, w: Float, h: Float) = MaskMath.shape(
    m.copy(geom = m.geom.copy(feather = 0f)),
    p.x / w,
    p.y / h,
    w / h.coerceAtLeast(1f)
) >
    0.5f

/** Tap = select another outline; drag = act on the grabbed handle of the selected mask. */
private fun Modifier.maskGestures(feel: MaskFeel): Modifier = maskTap(feel).maskDrag(feel)

private fun Modifier.maskTap(feel: MaskFeel): Modifier = pointerInput(Unit) {
    detectTapGestures { p ->
        val w = size.width.toFloat()
        val h = size.height.toFloat()
        val hit = feel.masks().lastOrNull { it.isGeometric && hits(it, p, w, h) }
        if (hit != null && hit.id != feel.selected()) {
            feel.haptic()
            feel.onSelect(hit.id)
        }
    }
}

/** One drag: which handle, the frame at grab time, accumulated motion, rotation snap state. */
private class MaskDrag(val grab: MaskGrab, val frame: MaskFrame) {
    var total = Offset.Zero
    var snapped = false
}

/** Resolves what a drag starting at [p] grabs on the selected mask; null = nothing (drag ignored). */
private fun startDrag(
    feel: MaskFeel,
    p: Offset,
    w: Float,
    h: Float,
    knob: Float,
    touch: Float
): MaskDrag? {
    val m = feel.masks().firstOrNull { it.id == feel.selected() && it.isGeometric } ?: return null
    val f = MaskFrame(m.geom, w, h, knob)
    val g =
        f.grab(m.type, p, touch)
            ?: MaskGrab.MOVE.takeIf { m.type == MaskType.LINEAR || hits(m, p, w, h) }
            ?: return null
    feel.onGrab(g)
    feel.haptic()
    android.util.Log.d(MASK_TAG, "grab $g ${m.type.id}")
    return MaskDrag(g, f)
}

private fun Modifier.maskDrag(feel: MaskFeel): Modifier = pointerInput(Unit) {
    var d: MaskDrag? = null
    fun end() {
        d = null
        feel.onGrab(null)
    }
    detectDragGestures(
        onDragStart = { p ->
            d =
                startDrag(
                    feel,
                    p,
                    size.width.toFloat(),
                    size.height.toFloat(),
                    28.dp.toPx(),
                    24.dp.toPx()
                )
        },
        onDragEnd = { end() },
        onDragCancel = { end() }
    ) { change, drag ->
        val cur = d ?: return@detectDragGestures
        val m = feel.masks().firstOrNull { it.id == feel.selected() } ?: return@detectDragGestures
        change.consume()
        cur.total += drag
        var g = cur.frame.drag(cur.grab, m.type, change.position, cur.total)
        if (cur.grab == MaskGrab.ROTATE) {
            val (r, s) = MaskFrame.snapAngle(g.rotation)
            if (s && !cur.snapped) feel.haptic()
            cur.snapped = s
            g = g.copy(rotation = r)
        }
        feel.onGeom(m.id, g)
    }
}

private val dash = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))

private fun DrawScope.drawOutline(m: Mask, f: MaskFrame, selected: Boolean) {
    val c = Color.White.copy(alpha = if (selected) 0.95f else 0.45f)
    val stroke = (if (selected) 2.dp else 1.dp).toPx()
    rotate(m.geom.rotation, f.center) {
        when (m.type) {
            MaskType.RECT, MaskType.ELLIPSE, MaskType.RADIAL -> closedOutline(m, f, c, stroke)
            MaskType.LINEAR -> linearOutline(f, c, stroke)
            MaskType.COLOR -> Unit
        }
    }
}

/** Shape edge (with a dark halo for contrast) + dashed feather extent (inner ring for radial). */
private fun DrawScope.closedOutline(m: Mask, f: MaskFrame, c: Color, stroke: Float) {
    fun shape(inset: Float, color: Color, style: Stroke) {
        val tl = Offset(f.center.x - f.hx - inset, f.center.y - f.hy - inset)
        val sz = Size((f.hx + inset) * 2, (f.hy + inset) * 2)
        if (m.type == MaskType.RECT) {
            drawRoundRect(
                color,
                tl,
                sz,
                CornerRadius(m.roundness * min(f.hx, f.hy) + inset),
                style = style
            )
        } else {
            drawOval(color, tl, sz, style = style)
        }
    }
    shape(0f, Color.Black.copy(alpha = 0.45f), Stroke(stroke + 2f))
    shape(0f, c, Stroke(stroke))
    val dashed = Stroke(1.dp.toPx(), pathEffect = dash)
    if (m.type == MaskType.RADIAL) {
        val k = 1f - m.geom.feather.coerceIn(0f, 1f)
        val tl = Offset(f.center.x - f.hx * k, f.center.y - f.hy * k)
        drawOval(c.copy(alpha = 0.4f), tl, Size(f.hx * 2 * k, f.hy * 2 * k), style = dashed)
    } else if (f.featherPx > 1f) {
        shape(f.featherPx, c.copy(alpha = 0.4f), dashed)
    }
}

/** Linear gradient: both ends of the ramp as long lines + dashed middle line. */
private fun DrawScope.linearOutline(f: MaskFrame, c: Color, stroke: Float) {
    val long = hypot(size.width, size.height)
    fun line(y: Float, color: Color, width: Float, effect: PathEffect? = null) = drawLine(
        color,
        Offset(f.center.x - long, f.center.y + y),
        Offset(f.center.x + long, f.center.y + y),
        width,
        pathEffect = effect
    )
    line(-f.hy - f.featherPx, c, stroke)
    line(f.hy + f.featherPx, c, stroke)
    line(0f, c.copy(alpha = 0.5f), 1.dp.toPx(), dash)
}

private fun DrawScope.drawHandles(m: Mask, f: MaskFrame, grow: Float, active: MaskGrab?) {
    fun knob(p: Offset, grab: MaskGrab, r: Float, fill: Color) {
        val k = if (active == grab) grow else 1f
        drawCircle(Color.Black.copy(alpha = 0.45f), (r + 2.dp.toPx()) * k, p)
        drawCircle(fill, r * k, p)
    }
    val r = 7.dp.toPx()
    drawLine(Color.White.copy(alpha = 0.7f), f.screen(0f, -f.hy), f.rotateHandle(), 1.5.dp.toPx())
    knob(f.center, MaskGrab.MOVE, 4.dp.toPx(), Color.White)
    knob(f.rotateHandle(), MaskGrab.ROTATE, r, Color.White)
    knob(
        f.sizeHandle(m.type),
        if (m.type ==
            MaskType.LINEAR
        ) {
            MaskGrab.HEIGHT
        } else {
            MaskGrab.SIZE
        },
        r,
        Color.White
    )
    knob(f.featherHandle(m.type), MaskGrab.FEATHER, r * 0.85f, KeyGold)
}
