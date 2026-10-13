package br.com.riseedit.editor

import br.com.riseedit.core.Project
import br.com.riseedit.media.displayMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class ProjectLoader(
    private val document: EditorDocument,
    private val operations: EditorOperations,
    private val media: EditorMedia
) {
    fun open(project: Project) {
        if (document.current.busy != null) return
        document.reset(project)
        operations.start("Abrindo projeto…", media::report) {
            val analyses = withContext(Dispatchers.IO) {
                (project.clips + project.overlays.map { it.clip }).map { it.uri }.distinct()
                    .mapNotNull { uri ->
                        document.store.loadAnalysis(uri)?.let { uri to it }
                    }.toMap()
            }
            document.analyses.putAll(analyses)
            val prepared = project.copy(
                clips = project.clips.map { media.prepare(it) },
                overlays = project.overlays.map { it.copy(clip = media.prepare(it.clip)) }
            )
            val usable = prepared.copy(
                clips = prepared.clips.map {
                    if (it.stabilization.enabled && !analyses.containsKey(it.uri)) {
                        it.copy(stabilization = it.stabilization.copy(enabled = false))
                    } else {
                        it
                    }
                }
            )
            if (usable != project) withContext(Dispatchers.IO) { document.store.save(usable) }
            document.update { it.copy(project = usable, analyzed = analyses.keys) }
            document.faults.values.firstOrNull()?.let { document.message(it.displayMessage()) }
            media.trimCache()
        }
    }
}
