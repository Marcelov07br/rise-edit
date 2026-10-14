package br.com.riseedit.core

import kotlin.math.abs

object Timeline {
    fun snap(time: Long, boundaries: List<Long>, toleranceMs: Long = 120): Long =
        boundaries.minByOrNull { abs(it - time) }?.takeIf { abs(it - time) <= toleranceMs } ?: time

    /** Clip transform at a source time: smooth path shared by preview and export. */
    fun keyframe(clip: Clip, sourceMs: Long): TransformKeyframe = TransformPath.at(
        clip.keyframes,
        sourceMs,
        domain = br.com.riseedit.core.keys.TimeDomain.SOURCE
    )
}
