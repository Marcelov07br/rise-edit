package br.com.riseedit.tools

import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import br.com.riseedit.EditorState
import br.com.riseedit.EditorViewModel
import br.com.riseedit.core.TransformKeyframe
import br.com.riseedit.time

@Composable
internal fun MovementPanel(vm: EditorViewModel, state: EditorState) {
    val p = state.project
    val clip = p.clips.getOrNull(state.selected) ?: return
    val enabled = state.busy == null
    val offset = p.clips.take(state.selected).sumOf { it.durationMs }
    val source = clip.sourceAt(
        state.positionMs - offset
    )
    val key = br.com.riseedit.core.Timeline.keyframe(clip, source)
    fun save(k: TransformKeyframe) {
        vm.clipSettings(
            clip.copy(
                keyframes = (
                    clip.keyframes.filter {
                        it.sourceMs !=
                            source
                    } +
                        k.copy(sourceMs = source)
                    ).sortedBy { it.sourceMs }
            )
        )
    }
    Text(
        "${clip.keyframes.size} keyframes · cursor ${time(state.positionMs)}",
        style = MaterialTheme.typography.bodySmall
    )
    Button(onClick = { save(key) }) { Text("◆ Adicionar keyframe no cursor") }
    ValueSlider("Posição X", key.x, -1f..1f) { save(key.copy(x = it)) }
    ValueSlider("Posição Y", key.y, -1f..1f) { save(key.copy(y = it)) }
    ValueSlider("Escala", key.scale, 0.5f..2f) { save(key.copy(scale = it)) }
    ValueSlider("Rotação", key.rotation, -180f..180f) { save(key.copy(rotation = it)) }
    ValueSlider("Opacidade", key.opacity, 0f..1f) { save(key.copy(opacity = it)) }
    TextButton(onClick = {
        vm.clipSettings(clip.copy(keyframes = emptyList()))
    }) { Text("Limpar keyframes") }
}
