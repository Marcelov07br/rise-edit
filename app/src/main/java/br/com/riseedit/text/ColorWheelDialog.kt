package br.com.riseedit.text

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import br.com.riseedit.RiseSurface
import br.com.riseedit.RiseWhite
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Hue/saturation wheel with a brightness slider. */
@Composable
internal fun ColorWheelDialog(initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val hsv = remember { FloatArray(3).also { AndroidColor.colorToHSV(initial, it) } }
    var hue by remember { mutableStateOf(hsv[0]) }
    var sat by remember { mutableStateOf(hsv[1]) }
    var value by remember { mutableStateOf(hsv[2].coerceAtLeast(0.05f)) }
    val color = AndroidColor.HSVToColor(floatArrayOf(hue, sat, value))
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.clip(RoundedCornerShape(24.dp)).background(RiseSurface).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Wheel(hue, sat) { h, s ->
                hue = h
                sat = s
            }
            LiveSlider("Brilho", value, 0.05f..1f) { value = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(Color(color)))
                TextButton(onClick = onDismiss) { Text("Cancelar", color = RiseWhite) }
                TextButton(onClick = {
                    onPick(color)
                    onDismiss()
                }) { Text("Aplicar", color = RiseWhite) }
            }
        }
    }
}

@Composable
private fun Wheel(hue: Float, sat: Float, onChange: (Float, Float) -> Unit) {
    fun pick(p: Offset, size: Float) {
        val r = size / 2
        val dx = p.x - r
        val dy = p.y - r
        val angle = (Math.toDegrees(atan2(dy, dx).toDouble()).toFloat() + 360) % 360
        onChange(angle, (hypot(dx, dy) / r).coerceIn(0f, 1f))
    }
    val hues = (0..6).map { Color.hsv(it * 60f % 360, 1f, 1f) }
    Canvas(
        Modifier.size(220.dp)
            .pointerInput(Unit) { detectTapGestures { pick(it, size.width.toFloat()) } }
            .pointerInput(Unit) {
                detectDragGestures { change, _ -> pick(change.position, size.width.toFloat()) }
            }
    ) {
        drawCircle(Brush.sweepGradient(hues, center))
        drawCircle(
            Brush.radialGradient(
                listOf(Color.White, Color.Transparent),
                center,
                size.minDimension / 2
            )
        )
        val r = size.minDimension / 2 * sat
        val rad = Math.toRadians(hue.toDouble())
        val knob = Offset(center.x + (r * cos(rad)).toFloat(), center.y + (r * sin(rad)).toFloat())
        drawCircle(Color.Black, 11.dp.toPx(), knob, style = Stroke(3.dp.toPx()))
        drawCircle(Color.White, 9.dp.toPx(), knob, style = Stroke(2.dp.toPx()))
    }
}
