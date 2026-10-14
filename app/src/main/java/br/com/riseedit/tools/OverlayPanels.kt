package br.com.riseedit.tools

import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import br.com.riseedit.ChoiceRow
import br.com.riseedit.EditorState
import br.com.riseedit.EditorViewModel
import br.com.riseedit.core.OverlayBlend
import br.com.riseedit.core.OverlayMask
import br.com.riseedit.core.VideoOverlay

/** The selected overlay, else the one under the playhead, else the newest. */
internal fun overlayTarget(state: EditorState): VideoOverlay? {
    val overlays = state.project.overlays
    return overlays.firstOrNull { it.id == state.selection }
        ?: overlays.lastOrNull {
            state.positionMs in it.offsetMs until it.offsetMs + it.clip.durationMs
        }
        ?: overlays.lastOrNull()
}

@Composable
private fun withOverlay(state: EditorState, content: @Composable (VideoOverlay) -> Unit) {
    val overlay = overlayTarget(state)
    if (overlay == null) {
        Text(
            "Adicione um overlay primeiro (Overlay › Adicionar overlay).",
            style = MaterialTheme.typography.bodySmall
        )
    } else {
        content(overlay)
    }
}

@Composable
internal fun OverlayOpacityPanel(vm: EditorViewModel, state: EditorState) = withOverlay(state) {
    ValueSlider("Opacidade", it.opacity, 0f..1f) { value -> vm.overlay(it.copy(opacity = value)) }
}

@Composable
internal fun OverlayBlendPanel(vm: EditorViewModel, state: EditorState) = withOverlay(state) {
    ChoiceRow(OverlayBlend.entries, it.blend, { blend ->
        vm.overlay(it.copy(blend = blend))
    }) { b ->
        b.label
    }
    Text(
        "Clarear some com o preto, Escurecer some com o branco, Chroma remove o verde. " +
            "Resultado exato na prévia Fiel e na exportação.",
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
internal fun OverlayMaskPanel(vm: EditorViewModel, state: EditorState) = withOverlay(state) {
    ChoiceRow(OverlayMask.entries, it.mask, { mask -> vm.overlay(it.copy(mask = mask)) }) { m ->
        m.label
    }
    if (it.mask != OverlayMask.NONE) {
        ValueSlider("Suavização", it.maskFeather, 0f..1f) { value ->
            vm.overlay(it.copy(maskFeather = value))
        }
        FilterChip(
            it.maskInvert,
            { vm.overlay(it.copy(maskInvert = !it.maskInvert)) },
            label = { Text("Inverter") }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OverlayTrimPanel(vm: EditorViewModel, state: EditorState) = withOverlay(state) { o ->
    var trim by remember(o.id, o.clip.startMs, o.clip.endMs) {
        mutableStateOf(o.clip.startMs.toFloat()..o.clip.endMs.toFloat())
    }
    RangeSlider(
        trim,
        { if (it.endInclusive - it.start >= 40) trim = it },
        valueRange = 0f..o.clip.sourceDurationMs.toFloat()
    )
    Button(onClick = {
        vm.overlay(o.copy(clip = o.clip.trim(trim.start.toLong(), trim.endInclusive.toLong())))
    }) { Text("Aplicar corte") }
}
