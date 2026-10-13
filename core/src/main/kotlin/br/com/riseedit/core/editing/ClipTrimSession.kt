package br.com.riseedit.core.editing

import br.com.riseedit.core.Clip
import br.com.riseedit.core.Project
import br.com.riseedit.core.timeline.toComposition
import kotlin.math.roundToLong

enum class TrimEdge { START, END }

/** An immutable gesture snapshot. Drafts never mutate the project or its history. */
class ClipTrimSession(val original: Clip, val edge: TrimEdge) {
    private val fullSource = original.copy(startMs = 0, endMs = original.sourceDurationMs)
    private val map = Project(clips = listOf(fullSource)).toComposition()
        .tracks.first().segments.single().timeMap
    private val initialUs = map.sourceToTarget(
        Math.multiplyExact(if (edge == TrimEdge.START) original.startMs else original.endMs, 1000)
    )
    private val minimumMs = minOf(40L, original.sourceLengthMs)

    fun dragged(deltaOutputUs: Double): Clip {
        require(deltaOutputUs.isFinite())
        val targetUs = (initialUs + deltaOutputUs).roundToLong()
        val sourceMs = (map.targetToSource(targetUs) / 1000.0).roundToLong()
        return when (edge) {
            TrimEdge.START -> original.trim(
                sourceMs.coerceIn(0, original.endMs - minimumMs),
                original.endMs
            )
            TrimEdge.END -> original.trim(
                original.startMs,
                sourceMs.coerceIn(original.startMs + minimumMs, original.sourceDurationMs)
            )
        }
    }
}
