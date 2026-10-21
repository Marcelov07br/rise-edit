package br.com.riseedit.tools

import br.com.riseedit.EditorState
import br.com.riseedit.EditorViewModel
import br.com.riseedit.core.AdjustmentLayer
import br.com.riseedit.core.Clip
import br.com.riseedit.core.Selection

/**
 * What the color panels edit: the selected take (clip) or the selected adjustment layer seen as a
 * clip ([AdjustmentLayer.asClip], source time = layer-local time). Same components either way.
 */
internal class ColorTarget(val clip: Clip, val offsetMs: Long, val layer: AdjustmentLayer?) {
    val isLayer get() = layer != null

    /** Source (clip) / local (layer) time under the playhead. */
    fun sourceMs(positionMs: Long): Long = if (layer != null) {
        (positionMs - layer.startMs).coerceIn(0, layer.durationMs)
    } else {
        clip.sourceAt((positionMs - offsetMs).coerceIn(0, clip.durationMs))
    }

    /** Project time of a source/local time. */
    fun projectMs(sourceMs: Long): Long =
        offsetMs + if (layer != null) sourceMs else outputOfSource(clip, sourceMs)

    /** Key times inside the visible range. */
    fun inRange(t: Long) = if (layer !=
        null
    ) {
        t in 0..layer.durationMs
    } else {
        t in clip.startMs..clip.endMs
    }
}

internal fun colorTarget(state: EditorState): ColorTarget? {
    val sel = state.selection
    if (sel is Selection.Adjustment) {
        val l = state.project.adjustmentLayers.firstOrNull { it.id == sel.id } ?: return null
        return ColorTarget(l.asClip(), l.startMs, l)
    }
    val clip = state.project.clips.getOrNull(state.selected) ?: return null
    return ColorTarget(clip, state.project.clips.take(state.selected).sumOf { it.durationMs }, null)
}

/** Commits an edited (clip-shaped) color back to its owner. */
internal fun EditorViewModel.commitColor(target: ColorTarget, edited: Clip) {
    val layer = target.layer
    if (layer != null) {
        val live = state.value.project.adjustmentLayers.firstOrNull { it.id == layer.id } ?: return
        adjustmentLayer(live.withColorOf(edited))
    } else {
        clipSettings(edited)
    }
}

/** Latest version of the target's clip from the current state (for async callbacks). */
internal fun EditorViewModel.latestColorClip(target: ColorTarget): Clip? {
    val p = state.value.project
    return if (target.layer !=
        null
    ) {
        p.adjustmentLayers.firstOrNull { it.id == target.layer.id }?.asClip()
    } else {
        p.clips.firstOrNull { it.id == target.clip.id }
    }
}
