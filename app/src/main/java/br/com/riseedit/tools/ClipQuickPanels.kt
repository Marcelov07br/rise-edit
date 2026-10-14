package br.com.riseedit.tools

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import br.com.riseedit.ChoiceRow
import br.com.riseedit.EditorState
import br.com.riseedit.EditorViewModel
import br.com.riseedit.core.SpeedPoint
import br.com.riseedit.time
import kotlin.math.abs

private val speeds = listOf(0.25, 0.5, 1.0, 1.5, 2.0, 4.0)

private fun speedLabel(speed: Double) = if (speed == 1.0) {
    "1× Normal"
} else {
    "$speed×".replace(".0×", "×")
}

/** Constant speed for the selected main clip; speed curves arrive in a later stage. */
@Composable
internal fun SpeedPanel(vm: EditorViewModel, state: EditorState) {
    val clip = state.project.clips.getOrNull(state.selected) ?: return
    val current = clip.speedAt(clip.startMs)
    val selected = speeds.minBy { abs(it - current) }
    ChoiceRow(speeds, selected, { speed ->
        val points = if (speed == 1.0) emptyList() else listOf(SpeedPoint(clip.startMs, speed))
        vm.clipSettings(clip.copy(speedPoints = points))
    }, ::speedLabel)
    Text(
        "Duração no projeto: ${time(clip.durationMs)}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Original sound of the selected main clip, or the selected audio layer. */
@Composable
internal fun VolumePanel(vm: EditorViewModel, state: EditorState) {
    val audio = state.project.audio.firstOrNull { it.id == state.layerId }
    if (audio != null) {
        Text(audio.name, style = MaterialTheme.typography.bodySmall)
        AudioMuteButton(vm, audio)
        ValueSlider("Volume", audio.volume, 0f..2f) { vm.audio(audio.copy(volume = it)) }
        return
    }
    val clip = state.project.clips.getOrNull(state.selected) ?: return
    Text(clip.name, style = MaterialTheme.typography.bodySmall)
    ValueSlider("Volume", clip.volume, 0f..2f) { vm.clipSettings(clip.copy(volume = it)) }
}
