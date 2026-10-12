package br.com.riseedit.tools

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable internal fun ValueSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean = true,
    change: (Float) -> Unit
) {
    var local by remember(value) { mutableFloatStateOf(value.coerceIn(range)) }
    Text("$label · ${"%.2f".format(local)}", style = MaterialTheme.typography.labelLarge)
    Slider(local, {
        local = it
    }, onValueChangeFinished = { change(local) }, valueRange = range, enabled = enabled)
}
