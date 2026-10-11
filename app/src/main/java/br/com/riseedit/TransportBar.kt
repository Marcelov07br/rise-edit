package br.com.riseedit

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Time readout, undo/redo, mute, play/pause and fullscreen: one row between the preview and the timeline. */
@Composable
fun TransportBar(vm: EditorViewModel, state: EditorState, enabled: Boolean, onFullscreen: () -> Unit, modifier: Modifier = Modifier) {
    val hasClips = state.project.clips.isNotEmpty()
    Row(modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            time(state.positionMs) + " / " + time(state.project.durationMs),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1f).padding(start = 8.dp)
        )
        BarButton("↶", enabled && state.canUndo) { vm.undo() }
        BarButton("↷", enabled && state.canRedo) { vm.redo() }
        BarButton(if (state.muted) "Mudo" else "Som", true) { vm.mute() }
        FilledTonalButton(
            onClick = { vm.togglePlay() },
            enabled = enabled && hasClips,
            modifier = Modifier.size(width = 64.dp, height = 44.dp),
            contentPadding = PaddingValues(0.dp)
        ) { Text(if (state.playing) "Ⅱ" else "▶", style = MaterialTheme.typography.titleMedium) }
        BarButton("⛶", true, onFullscreen)
    }
}

@Composable
private fun BarButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 10.dp),
        modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 44.dp)
    ) { Text(label, style = MaterialTheme.typography.titleMedium) }
}
