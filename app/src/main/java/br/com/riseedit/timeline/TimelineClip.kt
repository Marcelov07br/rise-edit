package br.com.riseedit.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.riseedit.MediaThumbnail
import br.com.riseedit.RiseWhite

@Composable
internal fun BoxScope.TimelineClip(
    entry: TimelineEntry,
    ui: TimelineUiState,
    actions: TimelineActions
) {
    val density = LocalDensity.current.density
    val left = ui.viewport.pxAtTime(entry.startUs)
    val right = ui.viewport.pxAtTime(entry.endUs)
    val visibleLeft = left.coerceAtLeast(0.0)
    val visibleRight = right.coerceAtMost(ui.viewport.widthPx)
    if (visibleRight <= visibleLeft) return
    Box(
        Modifier.offset(x = (visibleLeft / density).dp)
            .width(((visibleRight - visibleLeft) / density).dp).fillMaxHeight()
            .clip(RoundedCornerShape(6.dp)).background(entry.color.copy(alpha = 0.3f))
            .semantics {
                val selection = if (entry.selected) ", selecionado" else ""
                contentDescription = "Clipe ${entry.name}$selection"
                onClick("Selecionar clipe") {
                    actions.select(entry)
                    true
                }
            }
    ) {
        ClipBackground(entry)
        Text(
            entry.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.6f)).padding(3.dp)
        )
        KeyDiamonds(entry, left - visibleLeft, ui.viewport.pixelsPerSecond)
        Box(
            Modifier.fillMaxSize().border(
                if (entry.selected) 2.dp else 1.dp,
                if (entry.selected) Color.White else entry.color,
                RoundedCornerShape(6.dp)
            )
        )
    }
    if (entry.selected &&
        (entry.mainIndex != null || entry.overlay != null || entry.audio != null)
    ) {
        if (left in 0.0..ui.viewport.widthPx) TrimHandle(left.toFloat(), "Aresta inicial")
        if (right in 0.0..ui.viewport.widthPx) TrimHandle(right.toFloat(), "Aresta final")
    }
}

@Composable
private fun ClipBackground(entry: TimelineEntry) {
    val clip = entry.clip
    if (clip != null) {
        Row(Modifier.fillMaxSize()) {
            repeat(3) { frame ->
                MediaThumbnail(
                    clip.effectiveUri(true),
                    clip.sourceAt(clip.durationMs * frame / 3),
                    Modifier.weight(1f).fillMaxHeight()
                )
            }
        }
    }
    entry.audio?.let { TimelineWaveform(it) }
}

@Composable
private fun BoxScope.TrimHandle(x: Float, label: String) {
    val density = LocalDensity.current.density
    Box(
        Modifier.offset(x = (x / density - 7).dp).width(14.dp).fillMaxHeight()
            .background(RiseWhite, RoundedCornerShape(4.dp)).semantics {
                contentDescription = label
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.width(
                3.dp
            ).fillMaxHeight(0.55f).background(Color.Black, RoundedCornerShape(2.dp))
        )
    }
}

@Composable
private fun KeyDiamonds(entry: TimelineEntry, shiftPx: Double, pixelsPerSecond: Double) {
    if (entry.keyOffsetsUs.isEmpty()) return
    Canvas(Modifier.fillMaxSize()) {
        val r = 4.5f * density
        val y = r + 2 * density
        entry.keyOffsetsUs.forEach { us ->
            val x = (shiftPx + us * pixelsPerSecond / 1_000_000).toFloat()
            if (x < -r || x > size.width + r) return@forEach
            val path = Path().apply {
                moveTo(x, y - r)
                lineTo(x + r, y)
                lineTo(x, y + r)
                lineTo(x - r, y)
                close()
            }
            drawPath(path, RiseWhite)
            drawPath(path, Color.Black, style = Stroke(density))
        }
    }
}
