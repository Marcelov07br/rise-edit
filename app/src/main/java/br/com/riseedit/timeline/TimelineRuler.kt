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
import br.com.riseedit.core.editing.TimelineZoom
import kotlin.math.ceil
import kotlin.math.floor

@Composable
internal fun TimelineRuler(
    viewport: TimelineViewport,
    durationUs: Long,
    markersMs: List<Long> = emptyList()
) {
    val density = LocalDensity.current.density
    Canvas(Modifier.fillMaxWidth().height(28.dp)) {
        val stepUs = TimelineZoom.tickStepUs(viewport.pixelsPerSecond, 64.0 * density)
        val step = stepUs.toDouble()
        val minor = step / 5
        val first = (floor(viewport.originUs / minor) * minor).coerceAtLeast(0.0)
        val last = minOf(durationUs.toDouble(), viewport.timeAtPx(size.width.toDouble()))
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.LTGRAY
            textSize = 9 * density
            isAntiAlias = true
        }
        val count = ceil((last - first) / minor).toInt().coerceIn(0, 400)
        repeat(count + 1) { i ->
            val time = first + i * minor
            val x = viewport.pxAtTime(time).toFloat()
            val major = Math.floorMod((time / minor).toLong() + 0, 5L) == 0L
            val top = if (major) 16 * density else 21 * density
            drawLine(Color.Gray, Offset(x, top), Offset(x, 25 * density), density)
            if (major) {
                val text = TimelineZoom.label(time, stepUs)
                drawContext.canvas.nativeCanvas.drawText(text, x + 3 * density, 12 * density, paint)
            }
        }
        markersMs.forEach { ms ->
            val x = viewport.pxAtTime(ms * 1000.0).toFloat()
            drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), 2 * density)
            drawCircle(Color.White, 3 * density, Offset(x, 3 * density))
        }
    }
}
