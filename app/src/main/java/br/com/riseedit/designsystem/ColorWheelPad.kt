package br.com.riseedit.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.hypot

/**
 * Trackball for lift/gamma/gain: (x, y) inside the unit disk (y up). Drag moves the puck
 * relative to where it is (fine control), double tap recenters.
 */
@Composable
fun ColorWheelPad(x: Float, y: Float, modifier: Modifier = Modifier, change: (Float, Float) -> Unit) {
    val cur by rememberUpdatedState(Offset(x, y))
    Canvas(
        modifier.fillMaxWidth().aspectRatio(1f)
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { change(0f, 0f) }) }
            .pointerInput(Unit) {
                detectDragGestures { c, d ->
                    val r = size.width / 2f
                    var nx = cur.x + d.x / r * 0.5f
                    var ny = cur.y - d.y / r * 0.5f
                    val len = hypot(nx, ny)
                    if (len > 1f) { nx /= len; ny /= len }
                    c.consume()
                    change(nx, ny)
                }
            }
    ) {
        val r = size.minDimension / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        // hue ring matching Wheel.rgb(): red at 0°, counter-clockwise (y up)
        val hues = listOf(Color.Red, Color.Magenta, Color.Blue, Color.Cyan, Color.Green, Color.Yellow, Color.Red)
        drawCircle(Brush.sweepGradient(hues, c), r, c, alpha = 0.55f)
        drawCircle(Brush.radialGradient(listOf(Color(0xFF1A1A1C), Color(0x001A1A1C)), c, r), r, c)
        drawCircle(Color.White.copy(alpha = 0.25f), r, c, style = Stroke(1.5f * density))
        drawLine(Color.White.copy(alpha = 0.12f), Offset(c.x - r, c.y), Offset(c.x + r, c.y))
        drawLine(Color.White.copy(alpha = 0.12f), Offset(c.x, c.y - r), Offset(c.x, c.y + r))
        val p = Offset(c.x + x * r, c.y - y * r)
        drawCircle(Color.Black, 9f * density, p)
        drawCircle(Color.White, 7f * density, p, style = Stroke(2.5f * density))
    }
}
