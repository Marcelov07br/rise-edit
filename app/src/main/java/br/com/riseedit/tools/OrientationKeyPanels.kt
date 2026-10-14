package br.com.riseedit.tools

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.riseedit.ChoiceRow
import br.com.riseedit.EditorState
import br.com.riseedit.EditorViewModel
import br.com.riseedit.core.Clip
import br.com.riseedit.core.KeyframeEdits
import br.com.riseedit.core.ScaleMode
import br.com.riseedit.core.Selection
import br.com.riseedit.core.TransformPath
import br.com.riseedit.core.keys.Easing
import br.com.riseedit.core.keys.TransformTracks
import br.com.riseedit.time

/** Static orientation of the selected main clip: 90° steps, flips, Fit vs Fill. */
@Composable
internal fun OrientationPanel(vm: EditorViewModel, state: EditorState) {
    val clip = state.project.clips.getOrNull(state.selected) ?: return
    val o = clip.orientation
    val enabled = state.busy == null
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = { vm.clipSettings(clip.copy(orientation = o.rotate90())) },
            enabled = enabled
        ) { Text("Girar 90° (${o.turns * 90}°)") }
        OutlinedButton(
            onClick = { vm.clipSettings(clip.copy(orientation = o.flipH())) },
            enabled = enabled
        ) { Text(if (o.flipHorizontal) "Espelhar ✓" else "Espelhar") }
        OutlinedButton(
            onClick = { vm.clipSettings(clip.copy(orientation = o.flipV())) },
            enabled = enabled
        ) { Text(if (o.flipVertical) "Inverter ✓" else "Inverter") }
    }
    ChoiceRow(ScaleMode.entries, o.scaleMode, {
        vm.clipSettings(clip.copy(orientation = o.copy(scaleMode = it)))
    }) { if (it == ScaleMode.FIT) "Ajustar" else "Preencher" }
    Text(
        "Preencher corta para cobrir o quadro; Ajustar mostra o quadro inteiro com barras.",
        style = MaterialTheme.typography.bodySmall
    )
}

private val easings: List<Pair<String, Easing>> = listOf(
    "Manter" to Easing.Hold,
    "Linear" to Easing.Linear,
    "Suave" to Easing.Smooth,
    "Entrada" to Easing.CubicBezier(0.42, 0.0, 1.0, 1.0),
    "Saída" to Easing.CubicBezier(0.0, 0.0, 0.58, 1.0),
    "Entrada e saída" to Easing.CubicBezier(0.42, 0.0, 0.58, 1.0)
)

/** The selected item's key context: tracks, local time at the playhead and time mapping. */
private class KeyContext(
    val tracks: TransformTracks,
    val localMs: Long,
    val toTimeline: (Long) -> Long?,
    val write: (TransformTracks) -> Unit
)

private fun keyContext(vm: EditorViewModel, state: EditorState): KeyContext? {
    val p = state.project
    val pos = state.positionMs
    return when (val sel = state.selection) {
        is Selection.Overlay -> p.overlays.firstOrNull { it.id == sel.id }?.let { o ->
            KeyContext(KeyframeEdits.resolve(o), pos - o.offsetMs, { it + o.offsetMs }) {
                vm.overlay(KeyframeEdits.applyToOverlay(o, it))
            }
        }
        is Selection.Text -> p.texts.firstOrNull { it.id == sel.id }?.let { t ->
            KeyContext(KeyframeEdits.resolve(t), pos - t.startMs, { it + t.startMs }) {
                vm.text(KeyframeEdits.applyToText(t, it))
            }
        }
        is Selection.Audio -> null
        else -> p.clips.getOrNull(state.selected)?.let { c ->
            val offset = p.clips.take(state.selected).sumOf { it.durationMs }
            val local = c.sourceAt((pos - offset).coerceAtLeast(0))
            KeyContext(KeyframeEdits.resolve(c), local, { s -> outputOf(c, s)?.plus(offset) }) {
                vm.clipSettings(KeyframeEdits.applyToClip(c, it))
            }
        }
    }
}

private fun outputOf(clip: Clip, sourceMs: Long): Long? {
    if (sourceMs < clip.startMs || sourceMs > clip.endMs) return null
    var low = 0L
    var high = clip.durationMs
    while (low < high) {
        val mid = (low + high) / 2
        if (clip.sourceAt(mid) < sourceMs) low = mid + 1 else high = mid
    }
    return low
}

/**
 * Low docked panel opened from a keyframe diamond: easing, delete and jump. Position, scale and
 * rotation stay on the preview (touch only).
 */
@Composable
internal fun KeyframePanel(vm: EditorViewModel, state: EditorState) {
    val ctx = keyContext(vm, state)
    if (ctx == null) {
        Text("Selecione um clipe, overlay ou texto com quadros-chave.")
        return
    }
    val grid = TransformPath.DEFAULT_GRID
    val times = KeyframeEdits.times(ctx.tracks)
    val current = times.firstOrNull { grid.sameFrame(it, ctx.localMs) }
    val prev = times.lastOrNull { it < ctx.localMs && !grid.sameFrame(it, ctx.localMs) }
    val next = times.firstOrNull { it > ctx.localMs && !grid.sameFrame(it, ctx.localMs) }
    val enabled = state.busy == null
    Text(
        if (current != null) {
            "Quadro-chave ${times.indexOf(current) + 1}/${times.size} · ${time(current)}"
        } else {
            "Sem quadro-chave no cursor · ${times.size} no item"
        },
        style = MaterialTheme.typography.bodySmall
    )
    if (current != null) {
        val easing = KeyframeEdits.easingAt(ctx.tracks, current)
        val selected = easings.firstOrNull { it.second == easing }?.first ?: "Suave"
        ChoiceRow(easings.map { it.first }, selected, { label ->
            val e = easings.first { it.first == label }.second
            ctx.write(KeyframeEdits.setEasing(ctx.tracks, current, e))
        }) { it }
    }
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = { prev?.let(ctx.toTimeline)?.let(vm::seek) },
            enabled = enabled && prev != null
        ) { Text("◀ Anterior") }
        OutlinedButton(
            onClick = { next?.let(ctx.toTimeline)?.let(vm::seek) },
            enabled = enabled && next != null
        ) { Text("Próximo ▶") }
        OutlinedButton(
            onClick = { current?.let { ctx.write(KeyframeEdits.remove(ctx.tracks, it)) } },
            enabled = enabled && current != null
        ) { Text("Excluir") }
    }
    Text(
        "A curva vale do quadro-chave até o próximo. Posição, escala e giro: direto na prévia.",
        style = MaterialTheme.typography.bodySmall
    )
}
