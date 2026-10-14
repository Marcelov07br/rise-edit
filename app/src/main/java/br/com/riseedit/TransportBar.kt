package br.com.riseedit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/** Time readout, mute, play/pause and fullscreen between the preview and the timeline. */
@Composable
fun TransportBar(
    vm: EditorViewModel,
    state: EditorState,
    enabled: Boolean,
    onFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasClips = state.project.clips.isNotEmpty()
    Row(
        modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            time(state.positionMs) + " / " + time(state.project.durationMs),
            style = MaterialTheme.typography.labelMedium,
            color = RiseMuted,
            modifier = Modifier.weight(1f).padding(start = 6.dp)
        )
        IconButton(onClick = { vm.togglePlay() }, enabled = enabled && hasClips) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(RiseWhite),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (state.playing) "Pausar" else "Reproduzir",
                    tint = RiseBlack
                )
            }
        }
        Row(
            Modifier.weight(1f),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End
        ) {
            IconButton(onClick = { vm.mute() }) {
                Icon(
                    if (state.muted) {
                        Icons.AutoMirrored.Outlined.VolumeOff
                    } else {
                        Icons.AutoMirrored.Outlined.VolumeUp
                    },
                    if (state.muted) "Mudo" else "Som",
                    tint = RiseWhite
                )
            }
            IconButton(onClick = onFullscreen) {
                Icon(Icons.Outlined.Fullscreen, "Tela cheia", tint = RiseWhite)
            }
        }
    }
}
