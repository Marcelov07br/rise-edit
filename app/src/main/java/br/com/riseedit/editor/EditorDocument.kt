package br.com.riseedit.editor

import br.com.riseedit.EditorState
import br.com.riseedit.ProjectStore
import br.com.riseedit.core.HistoryController
import br.com.riseedit.core.MediaFault
import br.com.riseedit.core.MotionAnalysis
import br.com.riseedit.core.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Owns the document and history. Disk save succeeds before a new revision is published. */
internal class EditorDocument(val store: ProjectStore) {
    private val mutable = MutableStateFlow(EditorState())
    val state = mutable.asStateFlow()
    val current: EditorState get() = state.value
    val history = HistoryController()
    val analyses = mutableMapOf<String, MotionAnalysis>()
    val faults = mutableMapOf<String, MediaFault>()
    var onProjectChanged: () -> Unit = {}

    fun update(transform: (EditorState) -> EditorState) = mutable.update(transform)
    fun message(text: String?) = update { it.copy(message = text) }
    fun progress(value: Float, phase: String) = update { it.copy(progress = value, busy = phase) }

    fun reset(project: Project) {
        history.clear()
        analyses.clear()
        faults.clear()
        mutable.value = EditorState(project = project)
    }

    fun edit(candidate: Project): Boolean {
        if (current.busy != null) return false
        return commit(candidate)
    }

    /** Only the active operation calls this while its busy ownership remains held. */
    fun commit(candidate: Project): Boolean {
        if (candidate == current.project) return false
        val project = candidate.copy(modifiedAt = System.currentTimeMillis())
        if (!save(project)) return false
        history.record(current.project)
        publish(project, current.selected)
        return true
    }

    fun undo() = restore(undo = true)
    fun redo() = restore(undo = false)

    private fun restore(undo: Boolean) {
        if (current.busy != null) return
        val persist: (Project) -> Project? = { candidate ->
            candidate.copy(modifiedAt = System.currentTimeMillis()).takeIf { save(it) }
        }
        val project = if (undo) {
            history.undo(
                current.project,
                persist
            )
        } else {
            history.redo(current.project, persist)
        }
        if (project == null) return
        publish(project, selected = 0)
    }

    private fun save(project: Project): Boolean = try {
        store.save(project)
        true
    } catch (error: Exception) {
        message("Não foi possível salvar: ${error.localizedMessage}")
        false
    }

    private fun publish(project: Project, selected: Int) {
        update {
            it.copy(
                project = project,
                selected = selected.coerceIn(0, maxOf(0, project.clips.lastIndex)),
                canUndo = history.canUndo,
                canRedo = history.canRedo
            )
        }
        if (current.busy == null) onProjectChanged()
    }
}
