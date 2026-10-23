package br.com.riseedit.core.editing

import br.com.riseedit.core.Clip
import br.com.riseedit.core.Project

/** 0.21 "Substituir mídia" and "Congelar quadro" project edits (pure, unit-tested). */
object MediaReplace {
    /**
     * Swaps [old]'s media for [fresh]'s, keeping id, effects, color, keys, masks, speed and the
     * timeline length. The source in-point is kept when the new file is long enough (so all
     * SOURCE-time keys still line up); otherwise the range slides/shrinks to fit. Stabilization
     * analysis belongs to the old pixels, so it is dropped and stabilization turned off.
     */
    fun replace(old: Clip, fresh: Clip): Clip {
        val len = old.endMs - old.startMs
        val dur = fresh.sourceDurationMs
        val start = if (len >= dur) 0L else old.startMs.coerceAtMost(dur - len)
        val end = minOf(dur, start + len)
        val shift = start - old.startMs
        val points = old.speedPoints.map { it.copy(sourceMs = (it.sourceMs + shift).coerceAtLeast(0)) }
            .distinctBy { it.sourceMs }
        return old.copy(
            uri = fresh.uri,
            name = fresh.name,
            sourceDurationMs = dur,
            startMs = start,
            endMs = end,
            sourceInfo = fresh.sourceInfo,
            proxyUri = fresh.proxyUri,
            proxyRequired = fresh.proxyRequired,
            speedPoints = points,
            stabilization = old.stabilization.copy(enabled = false)
        )
    }

    /** Inserts [still] at [index] in the main track (after a split, before the right half). */
    fun insert(project: Project, index: Int, still: Clip): Project =
        project.copy(clips = project.clips.toMutableList().apply { add(index.coerceIn(0, size), still) })
}
