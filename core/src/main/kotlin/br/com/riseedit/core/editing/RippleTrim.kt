package br.com.riseedit.core.editing

import br.com.riseedit.core.Clip
import br.com.riseedit.core.Project
import java.util.UUID

/**
 * Premiere-style ripple trim at the playhead (Marce's Z / D, Premiere's Q / W):
 * removes the part of the clip under the playhead before ([toStart] = true) or after it, and
 * closes the gap — every later layer (overlays, texts, free audio, adjustment layers, markers)
 * moves back by the removed duration so the rest of the edit stays in sync.
 */
object RippleTrim {
    data class Result(val project: Project, val playheadMs: Long)

    fun atPlayhead(project: Project, playheadMs: Long, toStart: Boolean): Result? {
        var start = 0L
        val index = project.clips.indexOfFirst { c ->
            val hit = playheadMs >= start && playheadMs < start + c.durationMs
            if (!hit) start += c.durationMs
            hit
        }
        if (index < 0) return null
        val clip = project.clips[index]
        val rel = playheadMs - start
        val trimmed = ClipEdits.cutToCursor(project, index, rel, left = toStart) ?: return null
        val newClip = trimmed.clips[index]
        val removed = clip.durationMs - newClip.durationMs
        if (removed <= 0) return null
        // Removed range on the output timeline.
        val a = if (toStart) start else start + newClip.durationMs
        val b = a + removed
        val shifted = shiftAfter(trimmed, clip.id, a, b)
        return Result(shifted, if (toStart) start else start + newClip.durationMs)
    }

    /** Moves every independent layer that starts at or after [b] back by b − a. */
    private fun shiftAfter(project: Project, clipId: String, a: Long, b: Long): Project {
        val gap = b - a
        fun t(x: Long) = when {
            x >= b -> x - gap
            x > a -> a
            else -> x
        }
        val audio = project.audio.map { au ->
            if (au.linkedClipId == null && au.offsetMs >= b) au.copy(offsetMs = au.offsetMs - gap) else au
        }
        val overlays = project.overlays.map { o -> if (o.offsetMs >= b) o.copy(offsetMs = o.offsetMs - gap) else o }
        val texts = project.texts.map { x ->
            if (x.startMs >= b) x.copy(startMs = x.startMs - gap, endMs = x.endMs - gap) else x
        }
        val adjust = project.adjustmentLayers.map { l ->
            if (l.startMs >= b) l.copy(startMs = l.startMs - gap) else l
        }
        val markers = project.markers.filter { it.timeMs !in a until b }
            .map { m -> m.copy(timeMs = t(m.timeMs)) }
        return SeparateAudio.syncAll(
            project.copy(audio = audio, overlays = overlays, texts = texts, adjustmentLayers = adjust, markers = markers)
        )
    }
}

/** Ctrl+C / Ctrl+V on the main track: paste a copy of a clip at the edit nearest after the playhead. */
object ClipClipboard {
    fun paste(project: Project, clip: Clip, playheadMs: Long): Pair<Project, Int> {
        var t = 0L
        var at = project.clips.size
        for ((i, c) in project.clips.withIndex()) {
            if (playheadMs <= t) { at = i; break }
            if (playheadMs < t + c.durationMs) { at = i + 1; break }
            t += c.durationMs
        }
        val copy = clip.copy(id = UUID.randomUUID().toString(), masks = clip.masks.withNewIds())
        val clips = project.clips.toMutableList().apply { add(at, copy) }
        return SeparateAudio.syncAll(project.copy(clips = clips)) to at
    }
}
