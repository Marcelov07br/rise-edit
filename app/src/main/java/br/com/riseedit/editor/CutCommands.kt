package br.com.riseedit.editor

import br.com.riseedit.core.editing.SeparateAudio

/** 0.18 "Corte": main-clip cut commands; each successful edit is one undo step. */
internal class CutCommands(
    private val document: EditorDocument,
    private val commands: ProjectCommands
) {
    private fun relativeToSelected(state: br.com.riseedit.EditorState) =
        state.positionMs - state.project.clips.take(state.selected).sumOf { it.durationMs }

    /** 0.18: copy of the selected clip right after it (fresh ids), selected. */
    fun duplicate() {
        val state = document.current
        val clip = state.project.clips.getOrNull(state.selected) ?: return
        val next = br.com.riseedit.core.editing.ClipEdits.duplicate(state.project, state.selected)
        if (document.edit(next)) {
            val copy = next.clips[state.selected + 1]
            document.update { it.copy(selected = state.selected + 1, selectionId = copy.id) }
            android.util.Log.i("RiseCut", "duplicate ${clip.id} -> ${copy.id}")
        }
    }

    /** 0.18: removes the part before ([left]) or after the cursor from the selected clip. */
    fun cutToCursor(left: Boolean) {
        val state = document.current
        val next = br.com.riseedit.core.editing.ClipEdits.cutToCursor(
            state.project,
            state.selected,
            relativeToSelected(state),
            left
        ) ?: return document.message("Posicione o cursor dentro do clipe selecionado.")
        document.edit(next)
    }

    /** 0.18: moves one trim edge of the selected clip by whole frames. */
    fun nudge(edge: br.com.riseedit.core.editing.TrimEdge, frames: Int) {
        val state = document.current
        val clip = state.project.clips.getOrNull(state.selected) ?: return
        val grid = br.com.riseedit.core.keys.FrameGrid(state.project.export.fps)
        val moved = br.com.riseedit.core.editing.ClipEdits.nudge(clip, edge, frames, grid)
        if (moved != clip) commands.trimClip(clip, moved)
    }

    /** 0.18: constant speed 0.1–10× + "manter tom". */
    fun speed(value: Double, keepPitch: Boolean) {
        val state = document.current
        val clip = state.project.clips.getOrNull(state.selected) ?: return
        val next = br.com.riseedit.core.editing.ClipEdits.speed(clip, value, keepPitch)
        if (next != clip) {
            document.edit(SeparateAudio.sync(state.project.replace(state.selected, next), clip.id))
        }
    }
}
