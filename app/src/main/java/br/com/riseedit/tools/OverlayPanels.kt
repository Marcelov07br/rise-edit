package br.com.riseedit.tools

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import br.com.riseedit.ChoiceRow
import br.com.riseedit.EditorState
import br.com.riseedit.EditorViewModel
import br.com.riseedit.core.OverlayBlend
import br.com.riseedit.core.OverlayMask
import br.com.riseedit.core.Selection
import br.com.riseedit.core.VideoOverlay

/** The selected overlay only — never falls back to "last under playhead" or "newest". */
internal fun overlayTarget(state: EditorState): VideoOverlay? {
    val id = (state.selection as? Selection.Overlay)?.id ?: return null
    return state.project.overlays.firstOrNull { it.id == id }
}

@Composable
private fun withOverlay(state: EditorState, content: @Composable (VideoOverlay) -> Unit) {
    val overlay = overlayTarget(state)
    if (overlay == null) {
        Text(
            "Selecione um overlay na timeline para editar (ou Overlay › Adicionar overlay).",
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
    // 0.18: same film strip as the main clip; applied on release (no "Aplicar" button).
    var trim by remember(o.id, o.clip.startMs, o.clip.endMs) {
        mutableStateOf(o.clip.startMs..o.clip.endMs)
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    br.com.riseedit.designsystem.GraphicSurface {
        TrimStrip(
            clip = o.clip,
            range = trim,
            cursorSourceMs = null,
            enabled = state.busy == null,
            onDrag = { trim = it },
            onCommit = { vm.overlay(o.copy(clip = o.clip.trim(it.first, it.last))) },
            thumbnail = { ms ->
                runCatching {
                    br.com.riseedit.MediaAssets.thumbnail(context, o.clip.effectiveUri(true), ms)
                }
                    .getOrNull()?.asImageBitmap()
            }
        )
        Text(
            "${br.com.riseedit.time(trim.first)} → ${br.com.riseedit.time(trim.last)}",
            color = br.com.riseedit.RiseMuted,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
    }
}
