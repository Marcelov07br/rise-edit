package br.com.riseedit.tools

import androidx.compose.runtime.Composable
import br.com.riseedit.ChoiceRow
import br.com.riseedit.EditorState
import br.com.riseedit.EditorViewModel
import br.com.riseedit.core.ColorAdjust
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import br.com.riseedit.color.ColorLive
import br.com.riseedit.redrawColor
import br.com.riseedit.riseSpring

private val filters = listOf(
    "Natural" to ColorAdjust(),
    "Cinema" to ColorAdjust(-0.03f, 0.15f, -0.15f, 0.25f),
    "Vivo" to ColorAdjust(0.03f, 0.1f, 0.25f, 0.1f),
    "Quente" to ColorAdjust(0.02f, 0.05f, 0.1f, 0.5f),
    "Frio" to ColorAdjust(0f, 0.05f, -0.05f, -0.45f),
    "P&B" to ColorAdjust(saturation = -1f)
)

@Composable
internal fun FiltersPanel(vm: EditorViewModel, state: EditorState) {
    val clip = state.project.clips.getOrNull(state.selected) ?: return
    val current = filters.firstOrNull { it.second == clip.adjustments }?.first ?: ""
    ChoiceRow(filters.map { it.first }, current, { name ->
        vm.clipSettings(clip.copy(adjustments = filters.first { it.first == name }.second))
    }) { it }
}

private data class AdjustRow(
    val label: String,
    val range: ClosedFloatingPointRange<Float>,
    val get: (ColorAdjust) -> Float,
    val set: (ColorAdjust, Float) -> ColorAdjust
)

/** Display order follows the shader order (ColorAdjustMath). */
private val adjustRows = listOf(
    AdjustRow("Exposição (EV)", -2f..2f, { it.exposure }) { a, v -> a.copy(exposure = v) },
    AdjustRow("Brilho", -0.5f..0.5f, { it.brightness }) { a, v -> a.copy(brightness = v) },
    AdjustRow("Temperatura", -1f..1f, { it.warmth }) { a, v -> a.copy(warmth = v) },
    AdjustRow("Tint", -1f..1f, { it.tint }) { a, v -> a.copy(tint = v) },
    AdjustRow("Contraste", -0.8f..0.8f, { it.contrast }) { a, v -> a.copy(contrast = v) },
    AdjustRow("Sombras", -1f..1f, { it.shadows }) { a, v -> a.copy(shadows = v) },
    AdjustRow("Realces", -1f..1f, { it.highlights }) { a, v -> a.copy(highlights = v) },
    AdjustRow("Saturação", -1f..1f, { it.saturation }) { a, v -> a.copy(saturation = v) },
    AdjustRow("Vibrance", -1f..1f, { it.vibrance }) { a, v -> a.copy(vibrance = v) }
)

@Composable
internal fun AdjustPanel(vm: EditorViewModel, state: EditorState) {
    val clip = state.project.clips.getOrNull(state.selected) ?: return
    val a = clip.adjustments
    CompareHoldButton(vm, enabled = !a.isNeutral || !clip.grade.isIdentity)
    adjustRows.forEach { row ->
        ValueSlider(row.label, row.get(a), row.range) {
            vm.clipSettings(clip.copy(adjustments = row.set(a, it)))
        }
    }
}

/** Hold to see the original (adjust + grade bypassed in preview only). */
@Composable
private fun CompareHoldButton(vm: EditorViewModel, enabled: Boolean) {
    var held by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (held) 0.96f else 1f, riseSpring(), label = "compare")
    val haptic = LocalHapticFeedback.current
    DisposableEffect(Unit) { onDispose { ColorLive.holdOriginal = false } }
    OutlinedButton(
        onClick = {},
        enabled = enabled,
        modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(onPress = {
                    held = true
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    ColorLive.holdOriginal = true
                    redrawPreview(vm.player)
                    tryAwaitRelease()
                    held = false
                    ColorLive.holdOriginal = false
                    redrawPreview(vm.player)
                })
            }
    ) { Text(if (held) "Original" else "Segure para comparar") }
}

private fun redrawPreview(player: androidx.media3.common.Player?) {
    if (player is androidx.media3.exoplayer.ExoPlayer) {
        redrawColor(player)
    } else if (player != null && !player.isPlaying) {
        player.seekTo(player.currentPosition)
    }
}

private val fades = listOf(0L to "Nenhuma", 300L to "Suave", 600L to "Média", 1000L to "Longa")

/** Entry/exit fade of the selected clip (fade to black and back). */
@Composable
internal fun ClipAnimationPanel(vm: EditorViewModel, state: EditorState) {
    val clip = state.project.clips.getOrNull(state.selected) ?: return
    val current = fades.minBy { kotlin.math.abs(it.first - clip.fadeMs) }.first
    ChoiceRow(fades.map { it.first }, current, { vm.clipSettings(clip.copy(fadeMs = it)) }) { ms ->
        fades.first { it.first == ms }.second
    }
}
