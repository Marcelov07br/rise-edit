package br.com.riseedit.core.editing

import br.com.riseedit.core.Adjustments
import br.com.riseedit.core.AudioClip
import br.com.riseedit.core.Clip
import br.com.riseedit.core.Marker
import br.com.riseedit.core.Orientation
import br.com.riseedit.core.Project
import br.com.riseedit.core.Stabilization
import br.com.riseedit.core.color.ColorGrade

/** Removes a main clip and pulls every later layer and marker left by its duration. */
object RippleDelete {
    fun clip(project: Project, index: Int): Project {
        val clip = project.clips.getOrNull(index) ?: return project
        val start = project.clips.take(index).sumOf { it.durationMs }
        val end = start + clip.durationMs
        val gap = clip.durationMs
        fun shift(t: Long) = when {
            t >= end -> t - gap
            t > start -> start
            else -> t
        }
        val audio = project.audio.filter { it.linkedClipId != clip.id }.map { a ->
            if (a.offsetMs >= end) a.copy(offsetMs = a.offsetMs - gap) else a
        }
        val texts = project.texts.mapNotNull { t ->
            when {
                t.startMs >= end -> t.copy(startMs = t.startMs - gap, endMs = t.endMs - gap)
                t.startMs >= start && t.endMs <= end -> null
                t.endMs <= start -> t
                else -> {
                    val s = shift(t.startMs)
                    val e = shift(t.endMs).coerceAtLeast(s + 1)
                    t.copy(startMs = s, endMs = e)
                }
            }
        }
        val overlays = project.overlays.map { o ->
            if (o.offsetMs >= end) o.copy(offsetMs = o.offsetMs - gap) else o
        }
        val markers = project.markers.filter { it.timeMs !in start until end }
            .map { if (it.timeMs >= end) it.copy(timeMs = it.timeMs - gap) else it }
        return SeparateAudio.syncAll(
            project.copy(
                clips = project.clips.filterIndexed { i, _ -> i != index },
                audio = audio,
                texts = texts,
                overlays = overlays,
                markers = markers
            )
        )
    }
}

/** Timeline markers: add at the playhead (toggles an existing one nearby), jump between them. */
object Markers {
    const val SNAP_MS = 150L

    fun toggle(project: Project, timeMs: Long): Project {
        val near = project.markers.firstOrNull { kotlin.math.abs(it.timeMs - timeMs) <= SNAP_MS }
        val markers = if (near != null) {
            project.markers - near
        } else {
            (project.markers + Marker(timeMs = timeMs.coerceAtLeast(0))).sortedBy { it.timeMs }
        }
        return project.copy(markers = markers)
    }

    fun next(project: Project, timeMs: Long): Long? =
        project.markers.map { it.timeMs }.filter { it > timeMs + SNAP_MS / 3 }.minOrNull()

    fun previous(project: Project, timeMs: Long): Long? =
        project.markers.map { it.timeMs }.filter { it < timeMs - SNAP_MS / 3 }.maxOrNull()
}

/**
 * J/L cuts on separated (linked) audio: the audio starts [AudioClip.leadMs] before its clip
 * (J) and/or keeps playing [AudioClip.tailMs] after it (L). Bounded by the source and by 0.
 */
object JLCut {
    fun set(project: Project, audioId: String, leadMs: Long, tailMs: Long): Project {
        val a = project.audio.firstOrNull { it.id == audioId && it.linkedClipId != null }
            ?: return project
        val updated = project.copy(
            audio = project.audio.map {
                if (it.id == a.id) {
                    it.copy(leadMs = leadMs.coerceAtLeast(0), tailMs = tailMs.coerceAtLeast(0))
                } else {
                    it
                }
            }
        )
        return SeparateAudio.sync(updated, a.linkedClipId!!)
    }
}

/** Copy/paste of look and treatment between clips. Timing (trim, speed) is never pasted. */
data class ClipAttributes(
    val adjustments: Adjustments,
    val grade: ColorGrade,
    val orientation: Orientation,
    val stabilization: Stabilization,
    val volume: Float,
    val fadeMs: Long
) {
    fun applyTo(clip: Clip) = clip.copy(
        adjustments = adjustments,
        grade = grade,
        orientation = orientation,
        stabilization = stabilization,
        volume = volume,
        fadeMs = fadeMs
    )

    companion object {
        fun of(clip: Clip) = ClipAttributes(
            clip.adjustments,
            clip.grade,
            clip.orientation,
            clip.stabilization,
            clip.volume,
            clip.fadeMs
        )
    }
}

internal fun AudioClip.syncedTo(clip: Clip, clipOffset: Long): AudioClip {
    val end = (clip.endMs + tailMs).coerceIn(clip.startMs + 1, sourceDurationMs)
    val lead = leadMs.coerceAtMost(clipOffset).coerceAtMost(clip.startMs)
    val start = (clip.startMs - lead).coerceIn(0, end - 1)
    return copy(startMs = start, endMs = end, offsetMs = clipOffset - (clip.startMs - start))
}
