package br.com.riseedit.tools

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import br.com.riseedit.EditorState
import br.com.riseedit.EditorViewModel

/** 0.18: "Velocidade" opens the cut panel on its speed tab (0.1–10×, manter tom). */
@Composable
internal fun SpeedPanel(vm: EditorViewModel, state: EditorState) =
    TrimPanel(vm, state, CutTab.SPEED)

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
