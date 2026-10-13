package br.com.riseedit.editor

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** A single job owns processing until native resources and destination streams are closed. */
internal class EditorOperations(
    private val scope: CoroutineScope,
    private val document: EditorDocument,
    private val suspendPreview: () -> Unit,
    private val rebuildPreview: () -> Unit
) {
    private var owner: Job? = null

    fun start(label: String, onError: (Exception) -> Unit, work: suspend () -> Unit) {
        if (owner != null || document.current.busy != null) return
        suspendPreview()
        document.update { it.copy(busy = label, progress = 0f, playing = false) }
        // Lazy start establishes ownership even when work completes without suspension.
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                work()
            } catch (cancelled: CancellationException) {
                if (scope.isActive) document.message("Operação cancelada.")
                throw cancelled
            } catch (error: Exception) {
                onError(error)
            } finally {
                owner = null
                document.update { it.copy(busy = null) }
                if (scope.isActive) rebuildPreview()
            }
        }
        owner = job
        job.start()
    }

    fun cancel() {
        val job = owner ?: return
        document.update { it.copy(busy = "Cancelando…") }
        // Cleanup runs in the same owner. Busy stays set until its finally block is reached.
        job.cancel()
    }
}
