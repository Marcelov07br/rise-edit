package br.com.riseedit.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import br.com.riseedit.RiseSurfaceHigh
import br.com.riseedit.core.ColorCurves
import br.com.riseedit.core.CurvePoint

/**
 * Editable curve: drag points, tap empty area to add, double tap a point to remove
 * (endpoints stay). Drawn with the same monotone spline used by the shader table.
 */
@Composable
// detekt: point hit-test/drag branches of one gesture.
@Suppress("CyclomaticComplexMethod", "LongMethod")
fun CurveEditor(
    points: List<CurvePoint>,
    color: Color,
    modifier: Modifier = Modifier,
    change: (List<CurvePoint>) -> Unit
) {
    val pts by rememberUpdatedState(points)
    var drag by remember { mutableIntStateOf(-1) }
    fun near(o: Offset, w: Float, h: Float) = pts.indexOfFirst {
        (Offset(it.x * w, (1 - it.y) * h) - o).getDistance() < 28f * (w / 300f).coerceAtLeast(1f)
    }
    Canvas(
        modifier.fillMaxWidth().aspectRatio(
            1f
        ).clip(RoundedCornerShape(16.dp())).background(RiseSurfaceHigh)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { o ->
                        val i = near(o, size.width.toFloat(), size.height.toFloat())
                        if (i > 0 && i < pts.lastIndex) change(pts.filterIndexed { k, _ -> k != i })
                    },
                    onTap = { o ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        if (near(o, w, h) < 0 && pts.size < ColorCurves.MAX_POINTS) {
                            change(ColorCurves.normalize(pts + CurvePoint(o.x / w, 1 - o.y / h)))
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { o ->
                        drag = near(o, size.width.toFloat(), size.height.toFloat())
                    },
                    onDragEnd = { drag = -1 },
                    onDrag = { c, amount ->
                        val i = drag
                        if (i < 0) return@detectDragGestures
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val lo = if (i == 0) 0f else pts[i - 1].x + 0.01f
                        val hi = if (i == pts.lastIndex) 1f else pts[i + 1].x - 0.01f
                        // 0.22 S Pen: the stylus moves the point relatively at 1/3 speed (fine
                        // control, the tip never hides the point); finger/mouse follow absolutely.
                        val pen = c.type == androidx.compose.ui.input.pointer.PointerType.Stylus
                        val px = if (pen) pts[i].x + amount.x * PEN_GAIN / w else c.position.x / w
                        val py = if (pen) pts[i].y - amount.y * PEN_GAIN / h else 1 - c.position.y / h
                        val x = when (i) {
                            0 -> 0f
                            pts.lastIndex -> 1f
                            else -> px.coerceIn(lo, hi)
                        }
                        val y = py.coerceIn(0f, 1f)
                        change(pts.mapIndexed { k, p -> if (k == i) CurvePoint(x, y) else p })
                    }
                )
            }
    ) {
        val w = size.width
        val h = size.height
        val grid = Color.White.copy(alpha = 0.08f)
        for (k in 1..3) {
            drawLine(grid, Offset(w * k / 4, 0f), Offset(w * k / 4, h))
            drawLine(grid, Offset(0f, h * k / 4), Offset(w, h * k / 4))
        }
        drawLine(grid, Offset(0f, h), Offset(w, 0f))
        val path = Path()
        for (i in 0..64) {
            val x = i / 64f
            val y = ColorCurves.sample(points, x)
            if (i == 0) path.moveTo(0f, (1 - y) * h) else path.lineTo(x * w, (1 - y) * h)
        }
        drawPath(path, color, style = Stroke(width = 2.5f * density))
        points.forEach { p ->
            drawCircle(Color.Black, 7f * density, Offset(p.x * w, (1 - p.y) * h))
            drawCircle(color, 5f * density, Offset(p.x * w, (1 - p.y) * h))
        }
    }
}

/** Stylus drag gain on curve points (S Pen precision). */
internal const val PEN_GAIN = 0.33f

private fun Int.dp() = androidx.compose.ui.unit.Dp(this.toFloat())
