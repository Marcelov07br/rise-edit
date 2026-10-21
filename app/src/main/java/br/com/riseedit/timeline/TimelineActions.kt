package br.com.riseedit.timeline

import br.com.riseedit.EditorState
import br.com.riseedit.EditorViewModel
import kotlin.math.roundToLong

internal class TimelineActions(
    private val vm: EditorViewModel,
    private val state: () -> EditorState,
    private val onSelect: () -> Unit = {},
    private val onKeyframe: () -> Unit = {}
) {
    fun select(entry: TimelineEntry) {
        entry.mainIndex?.let { vm.select(it) } ?: vm.selectLayer(entry.id)
        onSelect()
    }

    fun beginScrub() {
        vm.pausePreview()
        vm.scrubbing(true)
    }

    fun seek(timeUs: Double) = vm.seek((timeUs / 1000).roundToLong())
    fun endScrub() = vm.scrubbing(false)
    fun beginTrim() = vm.pausePreview()

    fun commitTrim(ui: TimelineUiState) {
        when (val draft = ui.trimDraft) {
            is TrimDraft.Main -> vm.trimClip(draft.session.original, draft.draft)
            is TrimDraft.Overlay -> vm.trimOverlay(draft.session.original, draft.draft)
            is TrimDraft.Audio -> vm.trimAudio(draft.session.original, draft.draft)
            is TrimDraft.Adjust -> vm.trimAdjustment(draft.original, draft.draft)
            null -> Unit
        }
    }

    fun move(entry: TimelineEntry, offsetUs: Double, snapping: Boolean) {
        val ms = (offsetUs / 1000).roundToLong().coerceAtLeast(0)
        if (entry.mainIndex == null) {
            vm.moveLayer(entry.id, ms, snapping)
        } else {
            val clips = state().project.clips
            val target = clips.runningFold(0L) { t, clip -> t + clip.durationMs }
                .indexOfLast { it <= ms }.coerceIn(0, clips.lastIndex)
            vm.moveClip(entry.id, target)
        }
    }

    fun openKeyframe(entry: TimelineEntry, timeMs: Long, ui: TimelineUiState) {
        select(entry)
        vm.seek((entry.startUs / 1000).roundToLong() + timeMs)
        ui.keyframePanel = KeyframeTarget(entry.id, timeMs)
        onKeyframe()
    }
}
