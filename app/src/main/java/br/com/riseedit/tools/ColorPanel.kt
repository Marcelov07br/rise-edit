package br.com.riseedit.tools

import androidx.compose.runtime.Composable
import br.com.riseedit.ChoiceRow
import br.com.riseedit.EditorState
import br.com.riseedit.EditorViewModel
import br.com.riseedit.core.Adjustments

@Composable
internal fun ColorPanel(vm: EditorViewModel, state: EditorState) {
    val p = state.project
    val clip = p.clips.getOrNull(state.selected) ?: return
    val enabled = state.busy == null
    val a = clip.adjustments
    ChoiceRow(listOf("Natural", "Cinema", "Vivo", "P&B"), "", {
        vm.clipSettings(
            clip.copy(
                adjustments = when (it) {
                    "Cinema" -> Adjustments(-0.03f, 0.15f, -0.15f, 0.25f)
                    "Vivo" -> Adjustments(0.03f, 0.1f, 0.25f, 0.1f)
                    "P&B" -> Adjustments(saturation = -1f)
                    else -> Adjustments()
                }
            )
        )
    }) { it }
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
