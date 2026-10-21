package br.com.riseedit.core

import kotlin.math.abs

object Timeline {
    fun snap(time: Long, boundaries: List<Long>, toleranceMs: Long = 120): Long =
        boundaries.minByOrNull { abs(it - time) }?.takeIf { abs(it - time) <= toleranceMs } ?: time

    /** Clip transform at a source time: smooth path shared by preview and export. */
    fun keyframe(clip: Clip, sourceMs: Long): TransformKeyframe = TransformPath.at(
        clip.keyframes,
        sourceMs,
        domain = br.com.riseedit.core.keys.TimeDomain.SOURCE,
        tracks = clip.tracks
    )

    /** SMPTE timecode (MM:SS:FF or HH:MM:SS:FF) for frame-accurate timeline display. */
    fun timecode(ms: Long, fps: Int = 30): String {
        val safeFps = fps.coerceIn(1, 120)
        val boundedMs = ms.coerceAtLeast(0)
        val totalSeconds = boundedMs / 1000
        val frames = ((boundedMs % 1000) * safeFps / 1000).toInt().coerceIn(0, safeFps - 1)
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600
        return if (hours > 0) {
            "%02d:%02d:%02d:%02d".format(hours, minutes, seconds, frames)
        } else {
            "%02d:%02d:%02d".format(minutes, seconds, frames)
        }
    }
}
