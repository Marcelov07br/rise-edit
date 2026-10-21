package br.com.riseedit.timeline

import androidx.compose.ui.graphics.Color
import br.com.riseedit.EditorState
import br.com.riseedit.LaneAudio
import br.com.riseedit.LaneOverlay
import br.com.riseedit.LaneText
import br.com.riseedit.LaneVideo
import br.com.riseedit.core.AudioClip
import br.com.riseedit.core.Clip
import br.com.riseedit.core.editing.TrimEdge

internal val trackNames =
    listOf("Vídeo", "Overlay 1", "Overlay 2", "Texto", "Áudio 1", "Áudio 2", "Áudio 3")

/** Video, one overlay and one audio lane are always present; others appear on demand. */
internal val BASE_LANES = listOf(0, 1, 4)
internal const val LANE_DP = 44
internal const val RULER_DP = 28

internal fun visibleLanes(entries: List<TimelineEntry>, extra: Set<Int>): List<Int> = (
    BASE_LANES + entries.map {
        it.row
    } + extra
    ).distinct().filter { it in trackNames.indices }.sorted()

/** Next hidden lane of a kind: overlay (1-2), text (3) or audio (4-6). */
internal fun nextLane(kind: IntRange, shown: List<Int>): Int? = kind.firstOrNull { it !in shown }

internal data class TimelineEntry(
    val id: String,
    val name: String,
    val row: Int,
    val startUs: Double,
    val durationUs: Long,
    val selected: Boolean,
    val color: Color,
    val clip: Clip? = null,
    val audio: AudioClip? = null,
    val mainIndex: Int? = null,
    val overlay: br.com.riseedit.core.VideoOverlay? = null,
    /** Keyframe times relative to the entry start, drawn as diamonds. */
    val keyOffsetsUs: List<Double> = emptyList()
) {
    val endUs get() = startUs + durationUs
}

internal fun timelineEntries(state: EditorState, ui: TimelineUiState): List<TimelineEntry> =
    buildList {
        addAll(mainEntries(state, ui))
        state.project.overlays.forEach { live ->
            val it = (ui.trimDraft as? TrimDraft.Overlay)?.draft?.takeIf { d -> d.id == live.id }
                ?: live
            add(
                TimelineEntry(
                    it.id,
                    it.clip.name,
                    it.track + 1,
                    it.offsetMs * 1000.0,
                    it.clip.durationMs * 1000,
                    state.layerId == it.id,
                    LaneOverlay,
                    it.clip,
                    overlay = live,
                    keyOffsetsUs = it.keyframes.map { key -> key.sourceMs * 1000.0 }
                )
            )
        }
        state.project.texts.forEach {
            add(
                TimelineEntry(
                    it.id,
                    it.text,
                    3,
                    it.startMs * 1000.0,
                    (it.endMs - it.startMs) * 1000,
                    state.layerId == it.id,
                    LaneText,
                    keyOffsetsUs = it.keyframes.map { key -> key.sourceMs * 1000.0 }
                )
            )
        }
        state.project.audio.forEach { live ->
            val it = (ui.trimDraft as? TrimDraft.Audio)?.draft?.takeIf { d -> d.id == live.id }
                ?: live
            add(
                TimelineEntry(
                    it.id,
                    if (it.muted) "MUDO · ${it.name}" else it.name,
                    it.track + 4,
                    it.offsetMs * 1000.0,
                    it.durationMs * 1000,
                    state.layerId == it.id,
                    LaneAudio,
                    audio = live
                )
            )
        }
    }.map {
        if (it.id ==
            ui.movingId
        ) {
            it.copy(startUs = (it.startUs + ui.movingUs).coerceAtLeast(0.0))
        } else {
            it
        }
    }

private fun mainEntries(state: EditorState, ui: TimelineUiState): List<TimelineEntry> = buildList {
    var offset = 0L
    state.project.clips.forEachIndexed { index, original ->
        val draft = ui.draft?.takeIf { it.id == original.id }
        val clip = draft ?: original
        val leftDelta = if (draft != null && ui.trim?.edge == TrimEdge.START) {
            (original.durationMs - clip.durationMs) * 1000.0
        } else {
            0.0
        }
        add(
            TimelineEntry(
                original.id, original.name, 0, offset * 1000.0 + leftDelta,
                clip.durationMs * 1000, state.layerId == null && index == state.selected,
                LaneVideo, original, mainIndex = index,
                keyOffsetsUs = (clip.keyframes.map { it.sourceMs } + clip.colorKeys.times()).distinct()
                    .mapNotNull { outputMsOf(clip, it)?.times(1000.0) }
            )
        )
        offset +=
            if (draft != null &&
                ui.trim?.edge == TrimEdge.END
            ) {
                clip.durationMs
            } else {
                original.durationMs
            }
    }
}

/** Output time inside [clip] that maps to [sourceMs] (speed curves are monotonic). */
private fun outputMsOf(clip: br.com.riseedit.core.Clip, sourceMs: Long): Long? {
    if (sourceMs < clip.startMs || sourceMs > clip.endMs) return null
    var low = 0L
    var high = clip.durationMs
    while (low < high) {
        val mid = (low + high) / 2
        if (clip.sourceAt(mid) < sourceMs) low = mid + 1 else high = mid
    }
    return low
}
