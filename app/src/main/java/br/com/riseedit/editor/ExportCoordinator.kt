package br.com.riseedit.editor

import android.content.Context
import android.net.Uri
import android.util.Log
import br.com.riseedit.core.Project
import br.com.riseedit.media.DeviceCapability
import br.com.riseedit.media.displayMessage
import kotlinx.coroutines.runBlocking

internal class ExportCoordinator(
    private val context: Context,
    private val document: EditorDocument,
    private val operations: EditorOperations
) {
    fun export(destination: Uri) {
        val project = document.current.project
        if (project.clips.isEmpty()) return
        if (ExportService.isRunning || ExportBus.state.value.active) {
            document.message("Já existe uma exportação em andamento.")
            return
        }
        if (!validateDestination(project, destination)) return
        if (!hasDecoderCapacity(project)) return
        // Background service: do not take the editor's busy ownership or suspend the preview.
        ExportService.start(context, project, destination)
        document.message("Exportação iniciada em segundo plano. Você pode continuar editando.")
    }

    private fun validateDestination(project: Project, destination: Uri): Boolean {
        val clips = project.clips + project.overlays.map { it.clip }
        val ids = clips.map { it.id }.toSet()
        document.faults.entries.firstOrNull { it.key in ids }?.value?.let {
            document.message(it.displayMessage())
            return false
        }
        val sources =
            clips.flatMap { listOfNotNull(it.uri, it.proxyUri) } + project.audio.map { it.uri }
        if (destination.toString() in sources) {
            document.message("Escolha um novo arquivo para preservar o original.")
            return false
        }
        return true
    }

    private fun hasDecoderCapacity(project: Project): Boolean {
        val required = 1 + project.overlays.count { it.offsetMs < project.durationMs }
        if (required <= 1) return true
        val available = runBlocking { DeviceCapability.profile(context).maxDecoders }
        if (required <= available) return true
        Log.w("RiseExport", "Decoder budget exceeded: $required > $available")
        document.message(
            "Este aparelho não tem recursos suficientes para ${required - 1} overlay(s). " +
                "Remova uma camada de vídeo e tente exportar novamente."
        )
        return false
    }
}
