package br.com.riseedit.tools

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import br.com.riseedit.riseSpring
import kotlinx.coroutines.launch

/** Slider with label; a double tap springs it back to [default] (light haptic). */
@Composable internal fun ValueSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean = true,
    default: Float = 0f,
    change: (Float) -> Unit
) {
    var local by remember(value) { mutableFloatStateOf(value.coerceIn(range)) }
    var resetting by remember { mutableFloatStateOf(0f) } // 1f while a double-tap reset is pending
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val target = default.coerceIn(range)
    Text("$label · ${"%.2f".format(local)}", style = MaterialTheme.typography.labelLarge)
    Slider(
        local,
        { if (resetting == 0f) local = it },
        modifier = Modifier.pointerInput(enabled) {
            if (!enabled) return@pointerInput
            var last = 0L
            awaitEachGesture {
                // Observe only (Initial pass, nothing consumed) so dragging keeps working.
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (down.uptimeMillis - last < viewConfiguration.doubleTapTimeoutMillis) {
                    resetting = 1f
                    last = 0L
                } else {
                    last = down.uptimeMillis
                }
            }
        },
        onValueChangeFinished = {
            if (resetting == 1f) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                val anim = Animatable(local)
                scope.launch {
                    anim.animateTo(target, riseSpring()) { local = this.value }
                    resetting = 0f
                    change(target)
                }
            } else {
                change(local)
            }
        },
        valueRange = range,
        enabled = enabled
    )
}
