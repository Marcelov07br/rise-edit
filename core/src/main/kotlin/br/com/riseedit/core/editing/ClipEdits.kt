package br.com.riseedit.core.editing

import br.com.riseedit.core.Clip
import br.com.riseedit.core.MAX_SPEED
import br.com.riseedit.core.MIN_SPEED
import br.com.riseedit.core.Project
import br.com.riseedit.core.SpeedPoint
import br.com.riseedit.core.keys.FrameGrid
import java.util.UUID
import kotlin.math.roundToLong

/** 0.18 "Corte": pure main-track clip edits (each one is a single undo step in the editor). */
object ClipEdits {
    /** Minimum source length kept by any cut (same floor as the trim slider). */
    const val MIN_MS = 40L

    /**
     * Inserts a copy right after [index]: new clip id, new mask ids (keys kept, as in 0.17 paste),
     * and separated (linked) audio is duplicated too with a new id linked to the copy.
     */
    fun duplicate(project: Project, index: Int): Project {
        val clip = project.clips.getOrNull(index) ?: return project
        val copy = clip.copy(id = UUID.randomUUID().toString(), masks = clip.masks.withNewIds())
        val clips = project.clips.toMutableList().apply { add(index + 1, copy) }
        val audio = project.audio + project.audio.filter { it.linkedClipId == clip.id }
            .map { it.copy(id = UUID.randomUUID().toString(), linkedClipId = copy.id) }
        return SeparateAudio.syncAll(project.copy(clips = clips, audio = audio))
    }

    /**
     * Removes the part of the clip before ([left] = true) or after the cursor, given as project
     * output time relative to the clip start. Returns null when the cursor is not inside the clip.
     */
    fun cutToCursor(project: Project, index: Int, relativeMs: Long, left: Boolean): Project? {
        val clip = project.clips.getOrNull(index) ?: return null
        if (relativeMs <= 0 || relativeMs >= clip.durationMs) return null
        val cut = clip.sourceAt(relativeMs)
        val trimmed = if (left) {
            if (clip.endMs - cut < MIN_MS) return null
            clip.trim(cut, clip.endMs)
        } else {
            if (cut - clip.startMs < MIN_MS) return null
            clip.trim(clip.startMs, cut)
        }
        return SeparateAudio.syncAll(project.replace(index, trimmed))
    }

    /** Moves one edge by [frames] project frames (source step scales with the clip speed). */
    fun nudge(clip: Clip, edge: TrimEdge, frames: Int, grid: FrameGrid = FrameGrid()): Clip {
        val at = if (edge == TrimEdge.START) clip.startMs else (clip.endMs - 1).coerceAtLeast(0)
        val step = (frames * 1000.0 / grid.fps * clip.speedAt(at)).roundToLong()
        return when (edge) {
            TrimEdge.START -> clip.trim(
                (clip.startMs + step).coerceIn(0, clip.endMs - MIN_MS),
                clip.endMs
            )
            TrimEdge.END -> clip.trim(
                clip.startMs,
                (clip.endMs + step).coerceIn(clip.startMs + MIN_MS, clip.sourceDurationMs)
            )
        }
    }

    /** Constant speed (0.1–10×); 1× clears the speed points. */
    fun speed(clip: Clip, speed: Double, keepPitch: Boolean = clip.keepPitch): Clip {
        val s = (speed * 100).roundToLong() / 100.0
        val v = s.coerceIn(MIN_SPEED, MAX_SPEED)
        val points = if (v == 1.0) emptyList() else listOf(SpeedPoint(clip.startMs, v))
        return clip.copy(speedPoints = points, keepPitch = keepPitch)
    }
}
