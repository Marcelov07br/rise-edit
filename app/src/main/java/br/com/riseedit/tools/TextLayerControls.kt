package br.com.riseedit.tools

import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import br.com.riseedit.ChoiceRow
import br.com.riseedit.EditorViewModel
import br.com.riseedit.core.TextLayer
import kotlin.math.max

@Composable
internal fun TextLayerControls(vm: EditorViewModel, layer: TextLayer, durationMs: Long) {
    ValueSlider(
        "Início (s)",
        layer.startMs / 1000f,
        0f..max(0.001f, (durationMs - 1) / 1000f)
    ) {
        val start = (
            it *
                1000
            ).toLong()
        vm.text(layer.copy(startMs = start, endMs = max(start + 1, layer.endMs)))
    }
    ValueSlider(
        "Duração (s)",
        (layer.endMs - layer.startMs) / 1000f,
        0.001f..max(0.001f, (durationMs - layer.startMs) / 1000f)
    ) {
        vm.text(
            layer.copy(
                endMs =
                layer.startMs + (it * 1000).toLong().coerceAtLeast(1)
            )
        )
    }
    ValueSlider("Tamanho", layer.size, 0.5f..2f) { vm.text(layer.copy(size = it)) }
    ValueSlider("Posição horizontal", layer.x, -0.8f..0.8f) { vm.text(layer.copy(x = it)) }
    ValueSlider("Posição vertical", layer.y, -0.8f..0.8f) { vm.text(layer.copy(y = it)) }
    ChoiceRow(listOf(-1, 0xFFBEFF68.toInt(), 0xFFFFC66D.toInt()), layer.color, {
        vm.text(layer.copy(color = it))
    }) {
        when (it) {
            -1 -> "Branco"
            0xFFBEFF68.toInt() -> "Verde"
            else -> "Dourado"
        }
    }
    FilterChip(layer.animated, {
        vm.text(layer.copy(animated = !layer.animated))
    }, label = { Text("Entrada e saída suaves") })
    TextButton(onClick = vm::removeLayer) { Text("Excluir texto") }
}
