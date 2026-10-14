package br.com.riseedit.editor

import android.content.Context
import android.content.Intent
import android.net.Uri
import br.com.riseedit.core.Project
import br.com.riseedit.core.ProjectRecovery
import br.com.riseedit.media.displayMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class ProjectLoader(
    private val context: Context,
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
            val missing = withContext(Dispatchers.IO) {
                ProjectRecovery.missing(usable, document.store::readable)
            }
            document.update {
                it.copy(project = usable, analyzed = analyses.keys, missingMedia = missing)
            }
            when {
                missing.isNotEmpty() -> document.message(
                    "${missing.size} arquivo(s) ausente(s). Use Recuperar mídia para reconectar."
                )
                else -> document.faults.values.firstOrNull()?.let {
                    document.message(it.displayMessage())
                }
            }
            media.trimCache()
        }
    }

    fun reconnect(oldUri: String, newUri: Uri) {
        if (document.current.busy != null) return
        operations.start("Reconectando mídia…", media::report) {
            val project = withContext(Dispatchers.IO) {
                if (newUri.scheme == "content") {
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(
                            newUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                }
                val updated = ProjectRecovery.reconnect(
                    document.current.project,
                    oldUri,
                    newUri.toString()
                )
                document.store.save(updated)
                updated
            }
            open(project)
        }
    }

    fun restoreVersion(stamp: Long) {
        if (document.current.busy != null) return
        operations.start("Recuperando versão…", media::report) {
            val restored = withContext(Dispatchers.IO) {
                document.store.restoreVersion(document.current.project.id, stamp)
            }
            if (restored == null) {
                document.message("Não foi possível recuperar essa versão.")
            } else {
                document.message("Versão recuperada como \"${restored.name}\".")
                open(restored)
            }
        }
    }
}
