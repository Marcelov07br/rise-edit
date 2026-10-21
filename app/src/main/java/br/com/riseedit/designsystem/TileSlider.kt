package br.com.riseedit.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.riseedit.RiseMuted
import br.com.riseedit.RiseSurfaceHigh
import br.com.riseedit.riseSpring
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private const val TICKS = 40

/**
 * Compact version of Rise Cam's ISO wheel: a rounded tile holding a horizontal tick ruler
 * that slides under a fixed white center marker, with the value (monospace) on top and the
 * label below the tile. Drag horizontally to change; double tap springs back to [default]
 * with a light haptic.
 */
@Composable
fun TileSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    default: Float = 0f,
    format: (Float) -> String = { v -> (if (v > 0.005f) "+" else "") + "%.2f".format(v) },
    change: (Float) -> Unit
) {
    var local by remember(value) { mutableFloatStateOf(value.coerceIn(range)) }
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val span = range.endInclusive - range.start
    val target = default.coerceIn(range)
    val changed = abs(local - target) > span / 400f
    val valueScale by animateFloatAsState(if (changed) 1.08f else 0.94f, riseSpring(), label = "v")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(16.dp)).background(RiseSurfaceHigh)
                .pointerInput(enabled, range) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(onDoubleTap = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val anim = Animatable(local)
                        scope.launch {
                            anim.animateTo(target, riseSpring()) { local = this.value }
                            change(target)
                        }
                    })
                }
                .pointerInput(enabled, range) {
                    if (!enabled) return@pointerInput
                    // Full tile width = half the range, like flicking a few ISO stops.
                    val perPx = span / (size.width * 2f)
                    detectHorizontalDragGestures(onDragEnd = { change(local) }) { c, dx ->
                        c.consume()
                        local = (local - dx * perPx).coerceIn(range)
                    }
                },
            contentAlignment = Alignment.TopCenter
        ) {
            Text(
                format(local),
                color = if (changed) Color.White else RiseMuted,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (changed) FontWeight.Bold else FontWeight.Normal,
                fontSize = 13.sp, lineHeight = 13.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 9.dp).graphicsLayer { scaleX = valueScale; scaleY = valueScale }
            )
            Canvas(Modifier.fillMaxSize().padding(top = 30.dp, bottom = 10.dp)) {
                val cx = size.width / 2f
                val pxPerUnit = size.width * 2f / span
                val step = span / TICKS
                val shift = (local - range.start) * pxPerUnit
                val zeroIndex = ((target - range.start) / step).roundToInt()
                for (i in 0..TICKS) {
                    val x = cx + i * step * pxPerUnit - shift
                    if (x < 0f || x > size.width) continue
                    val near = 1f - (abs(x - cx) / cx).coerceIn(0f, 1f)
                    val major = i == zeroIndex
                    val h = size.height * (if (major) 0.9f else if (i % 5 == 0) 0.6f else 0.38f)
                    drawLine(
                        Color.White.copy(alpha = 0.15f + 0.55f * near + if (major) 0.2f else 0f),
                        Offset(x, size.height - h), Offset(x, size.height), strokeWidth = 1.dp.toPx()
                    )
                }
            }
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).width(18.dp).height(2.dp).background(Color.White))
        }
        Text(label, color = RiseMuted, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
    }
}
