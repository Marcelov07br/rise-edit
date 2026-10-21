package br.com.riseedit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.riseedit.timeline.TimelineActions
import br.com.riseedit.timeline.TimelineStage
import br.com.riseedit.timeline.TimelineUiState

@Composable
fun TimelinePanel(
    vm: EditorViewModel,
    state: EditorState,
    trackAreaHeight: Dp = 270.dp,
    onSelect: () -> Unit = {},
    onAddToLane: (Int) -> Unit = {},
    onKeyframe: () -> Unit = {}
) {
    val density = LocalDensity.current.density
    val ui = remember(state.project.id, density) { TimelineUiState(density) }
    val latest = rememberUpdatedState(state)
    val select = rememberUpdatedState(onSelect)
    val keyframe = rememberUpdatedState(onKeyframe)
    val actions = remember(vm, state.project.id) {
        TimelineActions(vm, { latest.value }, { select.value() }) { keyframe.value() }
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                hint(ui),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.weight(1f).padding(start = 4.dp)
            )
            br.com.riseedit.designsystem.PanelIconButton(
                androidx.compose.material.icons.Icons.Rounded.DeleteSweep,
                if (vm.rippleMode) "Ripple ligado" else "Ripple desligado",
                tint = if (vm.rippleMode) RiseWhite else RiseMuted
            ) { vm.rippleMode = !vm.rippleMode }
            FilterChip(ui.snapping, { ui.snapping = !ui.snapping }, label = { Text("Ímã") })
            TextButton(onClick = {
                zoomTimeline(ui, 1 / 1.4, state.project.durationMs)
            }) { Text("−") }
            TextButton(onClick = { zoomTimeline(ui, 1.4, state.project.durationMs) }) { Text("+") }
        }
        TimelineStage(ui, latest, actions, trackAreaHeight, onAddToLane)
    }
}

private fun hint(ui: TimelineUiState): String = when (val d = ui.trimDraft) {
    is br.com.riseedit.timeline.TrimDraft.Main ->
        "Corte: ${d.draft.startMs}–${d.draft.endMs} ms · solte para salvar"
    is br.com.riseedit.timeline.TrimDraft.Overlay ->
        "Corte: ${d.draft.clip.startMs}–${d.draft.clip.endMs} ms · solte para salvar"
    is br.com.riseedit.timeline.TrimDraft.Audio ->
        "Corte: ${d.draft.startMs}–${d.draft.endMs} ms · solte para salvar"
    is br.com.riseedit.timeline.TrimDraft.Adjust ->
        "Camada de ajuste: ${d.draft.startMs}–${d.draft.endMs} ms · solte para salvar"
    null -> "Pinça para zoom · segure para mover"
}

private fun zoomTimeline(ui: TimelineUiState, factor: Double, durationMs: Long) {
    ui.viewport = ui.viewport.zoomed(factor, ui.viewport.widthPx / 2, durationMs * 1000)
}
