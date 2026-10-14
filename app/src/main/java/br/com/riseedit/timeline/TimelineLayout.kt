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
    val mainIndex: Int? = null
) {
    val endUs get() = startUs + durationUs
}

internal fun timelineEntries(state: EditorState, ui: TimelineUiState): List<TimelineEntry> =
    buildList {
        addAll(mainEntries(state, ui))
        state.project.overlays.forEach {
            add(
                TimelineEntry(
                    it.id,
                    it.clip.name,
                    it.track + 1,
                    it.offsetMs * 1000.0,
                    it.clip.durationMs * 1000,
                    state.selection == it.id,
                    LaneOverlay,
                    it.clip
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
                    state.selection == it.id,
                    LaneText
                )
            )
        }
        state.project.audio.forEach {
            add(
                TimelineEntry(
                    it.id,
                    it.name,
                    it.track + 4,
                    it.offsetMs * 1000.0,
                    it.durationMs * 1000,
                    state.selection == it.id,
                    LaneAudio,
                    audio = it
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
                clip.durationMs * 1000, state.selection == null && index == state.selected,
                LaneVideo, original, mainIndex = index
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
