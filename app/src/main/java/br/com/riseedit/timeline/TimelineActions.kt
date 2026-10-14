package br.com.riseedit.timeline

import br.com.riseedit.EditorState
import br.com.riseedit.EditorViewModel
import kotlin.math.roundToLong

internal class TimelineActions(
    private val vm: EditorViewModel,
    private val state: () -> EditorState,
    private val onSelect: () -> Unit = {}
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
        val original = ui.trim?.original ?: return
        val candidate = ui.draft ?: return
        vm.trimClip(original, candidate)
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
}
