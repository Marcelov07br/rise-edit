package br.com.riseedit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
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
import br.com.riseedit.core.Timeline

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
    val fps = state.project.export.fps
    Row(
        modifier = modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            Timeline.timecode(state.positionMs, fps) + " / " +
                Timeline.timecode(state.project.durationMs, fps),
            style = MaterialTheme.typography.labelMedium,
            color = RiseMuted,
            maxLines = 1,
            modifier = Modifier.weight(1f).padding(start = 4.dp)
        )
        TransportControls(vm, state, enabled, hasClips)
        TransportExtraActions(state, vm::mute, onFullscreen, Modifier.weight(1f))
    }
}

@Composable
private fun TransportControls(
    vm: EditorViewModel,
    state: EditorState,
    enabled: Boolean,
    hasClips: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        IconButton(
            onClick = { vm.stepFrame(-1) },
            enabled = enabled && hasClips,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                "Quadro anterior",
                tint = if (enabled && hasClips) RiseWhite else RiseMuted,
                modifier = Modifier.size(20.dp)
            )
        }
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
        IconButton(
            onClick = { vm.stepFrame(1) },
            enabled = enabled && hasClips,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                "Próximo quadro",
                tint = if (enabled && hasClips) RiseWhite else RiseMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun TransportExtraActions(
    state: EditorState,
    onMute: () -> Unit,
    onFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.End
    ) {
        IconButton(onClick = onMute) {
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
