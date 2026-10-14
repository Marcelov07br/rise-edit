package br.com.riseedit.tools

import androidx.compose.runtime.Composable
import br.com.riseedit.ChoiceRow
import br.com.riseedit.EditorState
import br.com.riseedit.EditorViewModel
import br.com.riseedit.core.Adjustments

private val filters = listOf(
    "Natural" to Adjustments(),
    "Cinema" to Adjustments(-0.03f, 0.15f, -0.15f, 0.25f),
    "Vivo" to Adjustments(0.03f, 0.1f, 0.25f, 0.1f),
    "Quente" to Adjustments(0.02f, 0.05f, 0.1f, 0.5f),
    "Frio" to Adjustments(0f, 0.05f, -0.05f, -0.45f),
    "P&B" to Adjustments(saturation = -1f)
)

@Composable
internal fun FiltersPanel(vm: EditorViewModel, state: EditorState) {
    val clip = state.project.clips.getOrNull(state.selected) ?: return
    val current = filters.firstOrNull { it.second == clip.adjustments }?.first ?: ""
    ChoiceRow(filters.map { it.first }, current, { name ->
        vm.clipSettings(clip.copy(adjustments = filters.first { it.first == name }.second))
    }) { it }
}

@Composable
internal fun AdjustPanel(vm: EditorViewModel, state: EditorState) {
    val clip = state.project.clips.getOrNull(state.selected) ?: return
    val a = clip.adjustments
    ValueSlider("Brilho", a.brightness, -0.5f..0.5f) {
        vm.clipSettings(clip.copy(adjustments = a.copy(brightness = it)))
    }
    ValueSlider("Contraste", a.contrast, -0.8f..0.8f) {
        vm.clipSettings(clip.copy(adjustments = a.copy(contrast = it)))
    }
    ValueSlider("Saturação", a.saturation, -1f..1f) {
        vm.clipSettings(clip.copy(adjustments = a.copy(saturation = it)))
    }
    ValueSlider("Temperatura", a.warmth, -1f..1f) {
        vm.clipSettings(clip.copy(adjustments = a.copy(warmth = it)))
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
