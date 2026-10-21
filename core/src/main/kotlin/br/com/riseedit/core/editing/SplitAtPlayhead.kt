package br.com.riseedit.core.editing

import br.com.riseedit.core.Project
import br.com.riseedit.core.Selection
import br.com.riseedit.core.TransformKeyframe
import br.com.riseedit.core.keys.ParameterTrack
import br.com.riseedit.core.keys.TransformTracks
import java.util.UUID

/**
 * 0.18.1 "Dividir na agulha": one clear rule for what the split button cuts.
 * - A selected layer (audio/overlay/text/adjustment) → only that layer.
 * - Otherwise (main clip or nothing selected) → the main-track clip under the playhead.
 * Disabled when the playhead is less than one frame from the target's edges.
 */
object SplitAtPlayhead {
    data class Result(val project: Project, val rightId: String, val selection: Selection)

    private class Span(val start: Long, val end: Long)

    private fun span(
        project: Project,
        selection: Selection,
        playheadMs: Long
    ): Pair<Selection, Span>? = when (selection) {
        is Selection.Audio -> project.audio.firstOrNull { it.id == selection.id }
            ?.let { selection to Span(it.offsetMs, it.offsetMs + it.durationMs) }
        is Selection.Overlay -> project.overlays.firstOrNull { it.id == selection.id }
            ?.let { selection to Span(it.offsetMs, it.offsetMs + it.clip.durationMs) }
        is Selection.Text -> project.texts.firstOrNull { it.id == selection.id }
            ?.let { selection to Span(it.startMs, it.endMs) }
        is Selection.Adjustment -> project.adjustmentLayers.firstOrNull {
            it.id == selection.id
        }
            ?.let { selection to Span(it.startMs, it.endMs) }
        is Selection.Clip, Selection.None -> mainAt(project, playheadMs)
    }

    private fun mainAt(project: Project, playheadMs: Long): Pair<Selection, Span>? {
        var t = 0L
        project.clips.forEach { c ->
            val end = t + c.durationMs
            if (playheadMs in t until end) return Selection.Clip(c.id) to Span(t, end)
            t = end
        }
        return null
    }

    /** What would be split, or null when nothing can be (outside, or < 1 frame from an edge). */
    fun target(project: Project, selection: Selection, playheadMs: Long, fps: Int): Selection? {
        val (target, s) = span(project, selection, playheadMs) ?: return null
        val frame = (1000.0 / fps.coerceAtLeast(1)).toLong().coerceAtLeast(1)
        return target.takeIf { playheadMs - s.start >= frame && s.end - playheadMs >= frame }
    }

    fun apply(project: Project, selection: Selection, playheadMs: Long, fps: Int): Result? {
        val target = target(project, selection, playheadMs, fps) ?: return null
        val id = target.id ?: return null
        val newId = UUID.randomUUID().toString()
        val next = when (target) {
            is Selection.Clip -> return splitMain(project, id, playheadMs)
            is Selection.Audio -> splitAudio(project, id, newId, playheadMs)
            is Selection.Overlay -> splitOverlay(project, id, newId, playheadMs)
            is Selection.Text -> splitText(project, id, newId, playheadMs)
            is Selection.Adjustment -> splitAdjustment(project, id, newId, playheadMs)
            Selection.None -> return null
        } ?: return null
        return Result(next, newId, layerSelection(target, newId))
    }

    private fun layerSelection(target: Selection, newId: String): Selection = when (target) {
        is Selection.Audio -> Selection.Audio(newId)
        is Selection.Overlay -> Selection.Overlay(newId)
        is Selection.Text -> Selection.Text(newId)
        else -> Selection.Adjustment(newId)
    }

    private fun splitMain(project: Project, id: String, playheadMs: Long): Result? {
        val index = project.clips.indexOfFirst { it.id == id }
        val start = project.clips.take(index).sumOf { it.durationMs }
        val split =
            runCatching { project.split(index, playheadMs - start) }.getOrNull() ?: return null
        val right = split.clips[index + 1]
        // Right half gets its own mask ids (as duplicate/paste do).
        return Result(
            split.replace(index + 1, right.copy(masks = right.masks.withNewIds())),
            right.id,
            Selection.Clip(right.id)
        )
    }

    private fun splitAudio(p: Project, id: String, newId: String, at: Long): Project {
        val i = p.audio.indexOfFirst { it.id == id }
        val a = p.audio[i]
        val cut = a.startMs + (at - a.offsetMs)
        val left = a.copy(endMs = cut, tailMs = 0)
        // The right half is a free audio item (only the left keeps the link to its clip).
        val right = a.copy(
            id = newId,
            startMs = cut,
            offsetMs = at,
            leadMs = 0,
            linkedClipId = null
        )
        return p.copy(
            audio = p.audio.toMutableList().apply {
                set(i, left)
                add(i + 1, right)
            }
        )
    }

    private fun splitOverlay(p: Project, id: String, newId: String, at: Long): Project? {
        val i = p.overlays.indexOfFirst { it.id == id }
        val o = p.overlays[i]
        val local = at - o.offsetMs
        val parts = runCatching { o.clip.split(local) }.getOrNull() ?: return null
        val left = o.copy(clip = parts[0])
        val right = o.copy(
            id = newId,
            clip = parts[1].copy(masks = parts[1].masks.withNewIds()),
            offsetMs = at,
            keyframes = shiftKeys(o.keyframes, local),
            tracks = o.tracks?.let { shiftTracks(it, local) }
        )
        return p.copy(
            overlays = p.overlays.toMutableList().apply {
                set(i, left)
                add(i + 1, right)
            }
        )
    }

    private fun splitText(p: Project, id: String, newId: String, at: Long): Project {
        val i = p.texts.indexOfFirst { it.id == id }
        val t = p.texts[i]
        val local = at - t.startMs
        val left = t.copy(endMs = at)
        val right = t.copy(
            id = newId,
            startMs = at,
            keyframes = shiftKeys(t.keyframes, local),
            tracks = t.tracks?.let { shiftTracks(it, local) }
        )
        return p.copy(
            texts = p.texts.toMutableList().apply {
                set(i, left)
                add(i + 1, right)
            }
        )
    }

    private fun splitAdjustment(p: Project, id: String, newId: String, at: Long): Project {
        val i = p.adjustmentLayers.indexOfFirst { it.id == id }
        val a = p.adjustmentLayers[i]
        val local = at - a.startMs
        val left = a.copy(durationMs = local)
        val right = a.copy(
            id = newId,
            startMs = at,
            durationMs = a.durationMs - local,
            colorKeys = a.colorKeys.shifted(-local),
            masks = a.masks.shifted(-local).withNewIds()
        )
        return p.copy(
            adjustmentLayers = p.adjustmentLayers.toMutableList().apply {
                set(i, left)
                add(i + 1, right)
            }
        )
    }

    private fun shiftKeys(keys: List<TransformKeyframe>, d: Long) =
        keys.map { it.copy(sourceMs = it.sourceMs - d) }

    private fun <T> shiftTrack(t: ParameterTrack<T>, d: Long) =
        t.copy(keys = t.keys.map { it.copy(timeMs = it.timeMs - d) })

    private fun shiftTracks(t: TransformTracks, d: Long) = TransformTracks(
        shiftTrack(t.x, d),
        shiftTrack(t.y, d),
        shiftTrack(t.scale, d),
        shiftTrack(t.rotation, d),
        shiftTrack(t.opacity, d)
    )
}
