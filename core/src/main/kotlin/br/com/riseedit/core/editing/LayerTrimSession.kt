package br.com.riseedit.core.editing

import br.com.riseedit.core.AudioClip
import br.com.riseedit.core.VideoOverlay
import kotlin.math.roundToLong

/**
 * Edge trim for overlay and audio layers. Start trim keeps the right edge on the timeline
 * (offset grows); end trim shortens the visible span. Minimum length matches the main clip (40 ms).
 */
class OverlayTrimSession(val original: VideoOverlay, val edge: TrimEdge) {
    private val clipSession = ClipTrimSession(original.clip, edge)

    fun dragged(deltaOutputUs: Double): VideoOverlay {
        val clip = clipSession.dragged(deltaOutputUs)
        return when (edge) {
            TrimEdge.START -> {
                val shifted = original.clip.durationMs - clip.durationMs
                original.copy(
                    clip = clip,
                    offsetMs = (original.offsetMs + shifted).coerceAtLeast(0)
                )
            }
            TrimEdge.END -> original.copy(clip = clip)
        }
    }
}

class AudioTrimSession(val original: AudioClip, val edge: TrimEdge) {
    private val minimumMs = minOf(40L, original.durationMs)
    private val initialEdgeMs =
        if (edge == TrimEdge.START) original.startMs else original.endMs

    fun dragged(deltaOutputUs: Double): AudioClip {
        require(deltaOutputUs.isFinite())
        val sourceMs = (initialEdgeMs + deltaOutputUs / 1000.0).roundToLong()
        return when (edge) {
            TrimEdge.START -> {
                val start = sourceMs.coerceIn(0, original.endMs - minimumMs)
                original.copy(
                    startMs = start,
                    offsetMs = (original.offsetMs + (start - original.startMs)).coerceAtLeast(0)
                )
            }
            TrimEdge.END -> original.copy(
                endMs = sourceMs.coerceIn(original.startMs + minimumMs, original.sourceDurationMs)
            )
        }
    }
}
