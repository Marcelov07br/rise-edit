package br.com.riseedit.preview

import br.com.riseedit.core.Clip

/** Positions exposed by the editor are output time; playlist positions are clipped source time. */
internal class PreviewTimeline(val clips: List<Clip>) {
    val starts = clips.runningFold(0L) { start, clip -> start + clip.durationMs }
    val durationMs: Long get() = starts.last()
    fun indexAt(positionMs: Long): Int {
        if (clips.isEmpty()) return -1
        val time = positionMs.coerceIn(0, durationMs)
        return starts.indexOfLast { it <= time }.coerceAtMost(clips.lastIndex)
    }
    fun localOutput(index: Int, positionMs: Long) = (positionMs - starts[index]).coerceIn(0, clips[index].durationMs)
    fun sourcePosition(index: Int, positionMs: Long) = clips[index].sourceAt(localOutput(index, positionMs)) - clips[index].startMs
    fun globalPosition(index: Int, sourcePositionMs: Long, placeholder: Boolean): Long {
        val clip = clips.getOrNull(index) ?: return 0
        if (placeholder) return starts[index] + sourcePositionMs.coerceIn(0, clip.durationMs)
        val target = (clip.startMs + sourcePositionMs).coerceIn(clip.startMs, clip.endMs)
        var low = 0L
        var high = clip.durationMs
        // Inverse of sourceAt also preserves the model's rounded output durations.
        while (low < high) {
            val middle = low + (high - low) / 2
            if (clip.sourceAt(middle) < target) low = middle + 1 else high = middle
        }
        return starts[index] + low
    }
}

internal data class PreviewSlot(
    val id: String, val uri: String, val startMs: Long, val endMs: Long,
    val durationMs: Long, val unavailable: Boolean
)
