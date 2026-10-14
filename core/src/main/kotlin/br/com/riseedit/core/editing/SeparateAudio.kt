package br.com.riseedit.core.editing

import br.com.riseedit.core.AudioClip
import br.com.riseedit.core.Project

/** Detaches a main clip's audio onto an audio track and mutes the source. Linked for trim sync. */
object SeparateAudio {
    fun alreadySeparated(project: Project, clipId: String) =
        project.audio.any { it.linkedClipId == clipId }

    fun apply(project: Project, clipIndex: Int, preferredTrack: Int = 0): Project {
        val clip = project.clips.getOrNull(clipIndex) ?: return project
        if (alreadySeparated(project, clip.id)) return project
        val offset = project.clips.take(clipIndex).sumOf { it.durationMs }
        val track = preferredTrack.coerceIn(0, 2)
        val volume = clip.volume.takeIf { it > 0f } ?: 0.7f
        val audio = AudioClip(
            uri = clip.uri,
            name = "Áudio · ${clip.name}",
            sourceDurationMs = clip.sourceDurationMs,
            offsetMs = offset,
            startMs = clip.startMs,
            endMs = clip.endMs,
            track = track,
            volume = volume,
            fadeMs = clip.fadeMs.coerceAtLeast(0),
            linkedClipId = clip.id
        )
        return project.copy(
            clips = project.clips.mapIndexed { i, c ->
                if (i == clipIndex) c.copy(muted = true) else c
            },
            audio = project.audio + audio
        )
    }

    /** Keeps linked audio start/end/offset aligned with its main clip after a trim or move. */
    fun sync(project: Project, clipId: String): Project {
        val index = project.clips.indexOfFirst { it.id == clipId }
        if (index < 0) return project
        val clip = project.clips[index]
        val offset = project.clips.take(index).sumOf { it.durationMs }
        var changed = false
        val audio = project.audio.map { a ->
            if (a.linkedClipId != clipId) return@map a
            val next = a.syncedTo(clip, offset)
            if (next != a) changed = true
            next
        }
        return if (changed) project.copy(audio = audio) else project
    }

    fun syncAll(project: Project): Project = project.clips.fold(project) { p, c -> sync(p, c.id) }
}
