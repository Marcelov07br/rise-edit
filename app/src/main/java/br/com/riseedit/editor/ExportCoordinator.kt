package br.com.riseedit.editor

import android.content.Context
import android.net.Uri
import android.util.Log
import br.com.riseedit.core.Project
import br.com.riseedit.media.DeviceCapability
import br.com.riseedit.media.displayMessage
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal class ExportCoordinator(
    private val context: Context,
    private val document: EditorDocument,
    private val operations: EditorOperations
) {
    fun export(destination: Uri) {
        val project = document.current.project
        if (document.current.busy != null || project.clips.isEmpty()) return
        if (!validateDestination(project, destination)) return
        operations.start("Verificando exportação…", ::report) {
            if (!hasDecoderCapacity(project)) return@start
            val file = File.createTempFile("export-", ".mp4", context.cacheDir)
            try {
                // Transformer expects a path that does not already contain a file.
                check(file.delete()) { "Não foi possível preparar o arquivo temporário." }
                document.progress(0f, "Exportando MP4 ${project.export.height}p…")
                ExportRenderer(
                    context
                ).render(project, document.analyses.toMap(), file) { progress ->
                    document.update { it.copy(progress = progress) }
                }
                document.progress(1f, "Salvando vídeo…")
                ExportDestinationWriter(context).write(file, destination)
                document.message("Vídeo exportado. Os arquivos originais foram preservados.")
            } finally {
                // The owner cannot release busy or rebuild preview until this cleanup completes.
                withContext(NonCancellable + Dispatchers.IO) { file.delete() }
            }
        }
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

    private suspend fun hasDecoderCapacity(project: Project): Boolean {
        val required = 1 + project.overlays.count { it.offsetMs < project.durationMs }
        if (required <= 1) return true
        val available = DeviceCapability.profile(context).maxDecoders
        if (required <= available) return true
        Log.w("RiseExport", "Decoder budget exceeded: $required > $available")
        document.message(
            "Este aparelho não tem recursos suficientes para ${required - 1} overlay(s). Remova uma camada de vídeo e tente exportar novamente."
        )
        return false
    }

    private fun report(error: Exception) {
        document.message(
            "Não foi possível exportar o vídeo: ${error.localizedMessage}. Tente novamente em 720p."
        )
    }
}
