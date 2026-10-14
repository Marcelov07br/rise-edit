package br.com.riseedit.tools

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.riseedit.EditorState
import br.com.riseedit.EditorViewModel
import br.com.riseedit.RiseWhite
import br.com.riseedit.core.MotionGoal
import br.com.riseedit.core.Stabilization

@Composable
internal fun StabilizePanel(vm: EditorViewModel, state: EditorState) {
    val p = state.project
    val clip = p.clips.getOrNull(state.selected) ?: return
    val enabled = state.busy == null
    Text("✦ De tremido a suave", color = RiseWhite, fontWeight = FontWeight.Bold)
    Text(
        "1. Escolha um modo  2. Analise  3. Compare e ajuste",
        style = MaterialTheme.typography.bodySmall
    )
    val analyzed = clip.uri in state.analyzed
    val s = clip.stabilization
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf("Suave", "Forte", "Câmera fixa").forEach { mode ->
            OutlinedButton(onClick = {
                vm.settings(
                    when (mode) {
                        "Suave" -> Stabilization(analyzed, MotionGoal.SMOOTH, 0.65, 0.4, 1.10)
                        "Forte" -> Stabilization(analyzed, MotionGoal.SMOOTH, 1.0, 1.1, 1.25)
                        else -> Stabilization(analyzed, MotionGoal.FIXED, 1.0, 0.8, 1.25)
                    }
                )
            }, enabled = enabled) { Text(mode) }
        }
    }
    Button(
        onClick = vm::analyze,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) {
        Text(if (analyzed) "Analisar novamente" else "Analisar e estabilizar")
    }
    if (analyzed) {
        Row {
            FilterChip(s.enabled, {
                vm.settings(s.copy(enabled = !s.enabled))
            }, label = { Text(if (s.enabled) "Ativada" else "Desativada") })
            TextButton(onClick = vm::compare) {
                Text(if (state.comparing) "Ver estabilizado" else "Antes / depois")
            }
        }
        ValueSlider("Intensidade", s.strength.toFloat(), 0f..1f, enabled) {
            vm.settings(s.copy(strength = it.toDouble()))
        }
        ValueSlider("Suavidade (s)", s.smoothSeconds.toFloat(), 0.1f..2f, enabled) {
            vm.settings(s.copy(smoothSeconds = it.toDouble().coerceIn(0.1, 2.0)))
        }
        ValueSlider("Crop automático · limite de zoom", s.maxZoom.toFloat(), 1f..1.25f, enabled) {
            vm.settings(s.copy(maxZoom = it.toDouble().coerceIn(1.0, 1.25)))
        }
    }
    Text(
        "Até 2 minutos por fonte. Confira o resultado: cenas com pouca textura ou objetos grandes em movimento podem exigir ajustes.",
        style = MaterialTheme.typography.bodySmall
    )
}
