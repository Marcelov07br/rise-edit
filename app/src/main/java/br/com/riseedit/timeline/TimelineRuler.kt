package br.com.riseedit.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import br.com.riseedit.core.editing.TimelineViewport
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

@Composable
internal fun TimelineRuler(viewport: TimelineViewport, durationUs: Long) {
    val density = LocalDensity.current.density
    Canvas(Modifier.fillMaxWidth().height(28.dp)) {
        val step = tickStep(viewport.pixelsPerSecond, density)
        val first = (floor(viewport.originUs / step) * step).coerceAtLeast(0.0)
        val last = minOf(durationUs.toDouble(), viewport.timeAtPx(size.width.toDouble()))
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.LTGRAY
            textSize = 9 * density
            isAntiAlias = true
        }
        val count = ceil((last - first) / step).toInt().coerceIn(0, 100)
        repeat(count + 1) { i ->
            val time = first + i * step
            val x = viewport.pxAtTime(time).toFloat()
            drawLine(Color.Gray, Offset(x, 16 * density), Offset(x, 25 * density), density)
            val text = if (step >=
                1_000_000
            ) {
                "${(time / 1_000_000).toLong()}s"
            } else {
                "${(time / 1000).toLong()}ms"
            }
            drawContext.canvas.nativeCanvas.drawText(text, x + 3 * density, 12 * density, paint)
        }
    }
}

private fun tickStep(scale: Double, density: Float): Double {
    val desired = 65 * density / scale
    val magnitude = 10.0.pow(floor(log10(desired)))
    val normalized = desired / magnitude
    val multiple = when {
        normalized <= 1 -> 1
        normalized <= 2 -> 2
        normalized <= 5 -> 5
        else -> 10
    }
    return multiple * magnitude * 1_000_000
}
