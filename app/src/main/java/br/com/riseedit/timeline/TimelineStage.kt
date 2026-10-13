package br.com.riseedit.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.riseedit.EditorState
import br.com.riseedit.RiseGreen
import br.com.riseedit.RiseSurface

@Composable
internal fun TimelineStage(
    ui: TimelineUiState,
    state: State<EditorState>,
    actions: TimelineActions,
    height: Dp
) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current.density
    val entries = rememberUpdatedState(timelineEntries(state.value, ui))
    val duration = state.value.project.durationMs * 1000
    LaunchedEffect(duration) { ui.viewport = ui.viewport.bounded(duration) }
    Row(Modifier.fillMaxWidth().height(height)) {
        Column(Modifier.width(62.dp)) {
            Box(Modifier.height(28.dp))
            Column(Modifier.verticalScroll(scroll)) {
                trackNames.forEach {
                    Text(
                        it,
                        Modifier.height(52.dp).padding(top = 14.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
        Box(
            Modifier.weight(1f).clipToBounds()
                .onSizeChanged {
                    ui.viewport = ui.viewport.resized(it.width.toDouble(), duration)
                }
                .semantics { contentDescription = "Timeline interativa" }
                .timelineGestures(ui, state, entries, actions, density) { scroll.value }
        ) {
            Column {
                TimelineRuler(ui.viewport, duration)
                Column(Modifier.verticalScroll(scroll)) {
                    repeat(trackNames.size) { row ->
                        Box(
                            Modifier.fillMaxWidth().height(52.dp).padding(vertical = 3.dp)
                                .background(
                                    RiseSurface.copy(alpha = 0.7f),
                                    RoundedCornerShape(6.dp)
                                )
                        ) {
                            entries.value.filter {
                                it.row == row
                            }.sortedBy { it.selected }.forEach {
                                TimelineClip(it, ui, actions)
                            }
                        }
                    }
                }
            }
            Canvas(Modifier.matchParentSize()) {
                val x = ui.viewport.pxAtTime(state.value.positionMs * 1000.0).toFloat()
                drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), 2 * density)
                drawCircle(RiseGreen, 5 * density, Offset(x, 5 * density))
            }
        }
    }
}
