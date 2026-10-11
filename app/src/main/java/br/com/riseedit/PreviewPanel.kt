@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class, androidx.media3.common.util.ExperimentalApi::class])
package br.com.riseedit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView

/** Video preview. Tapping it toggles playback, like most mobile editors. */
@Composable
fun PreviewPanel(vm: EditorViewModel, state: EditorState, modifier: Modifier = Modifier) {
    Box(modifier.background(Color.Black, RoundedCornerShape(16.dp)).clickable { vm.togglePlay() }, contentAlignment = Alignment.Center) {
        if (state.project.clips.isEmpty()) Text("PRÉVIA", color = Color.Gray)
        else AndroidView(
            factory = { context -> PlayerView(context).apply { player = vm.player; useController = false; setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING) } },
            modifier = Modifier.fillMaxSize(),
            onRelease = { it.player = null }
        )
        if (state.comparing) Surface(Modifier.align(Alignment.TopStart).padding(8.dp), color = Color.Black.copy(alpha = 0.7f), shape = RoundedCornerShape(6.dp)) {
            Text("Original · sem estabilização", Modifier.padding(6.dp), style = MaterialTheme.typography.labelSmall)
        }
    }
}
