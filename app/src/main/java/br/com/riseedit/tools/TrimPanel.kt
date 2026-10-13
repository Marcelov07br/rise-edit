package br.com.riseedit.tools

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.riseedit.ChoiceRow
import br.com.riseedit.EditorState
import br.com.riseedit.EditorViewModel
import br.com.riseedit.time

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrimPanel(vm: EditorViewModel, state: EditorState) {
    val p = state.project
    val clip = p.clips.getOrNull(state.selected) ?: return
    val enabled = state.busy == null
    Text(clip.name, style = MaterialTheme.typography.bodySmall)
    var trim by remember(clip.id, clip.startMs, clip.endMs) {
        mutableStateOf(
            clip.startMs.toFloat()..clip.endMs.toFloat()
        )
    }
    Text("${time(trim.start.toLong())} → ${time(trim.endInclusive.toLong())}")
    RangeSlider(trim, {
        if (it.endInclusive - it.start >=
            40
        ) {
            trim = it
        }
    }, valueRange = 0f..clip.sourceDurationMs.toFloat(), enabled = enabled)
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(onClick = {
            vm.trim(trim.start.toLong(), trim.endInclusive.toLong())
        }, enabled = enabled) { Text("Aplicar trim") }
        OutlinedButton(onClick = vm::split, enabled = enabled) { Text("Dividir no cursor") }
        TextButton(onClick = vm::remove, enabled = enabled) { Text("Excluir clipe") }
    }
    Row {
        TextButton(onClick = {
            vm.move(-1)
        }, enabled = enabled && state.selected > 0) { Text("← Mover") }
        TextButton(
            onClick = { vm.move(1) },
            enabled =
            enabled && state.selected < p.clips.lastIndex
        ) { Text("Mover →") }
    }
    Text(
        "A faixa principal fecha os espaços automaticamente ao cortar ou excluir.",
        style = MaterialTheme.typography.bodySmall
    )
    ChoiceRow(listOf(0L, 250L, 500L), clip.fadeMs, { vm.clipSettings(clip.copy(fadeMs = it)) }) {
        if (it ==
            0L
        ) {
            "Corte seco"
        } else {
            "Fade ${it}ms"
        }
    }
}
