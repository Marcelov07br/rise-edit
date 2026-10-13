package br.com.riseedit.editor

import android.content.Context
import br.com.riseedit.MotionAnalyzer
import br.com.riseedit.core.MediaFault
import br.com.riseedit.media.displayMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class MotionCoordinator(
    private val context: Context,
    private val document: EditorDocument,
    private val operations: EditorOperations
) {
    fun analyze() {
        val state = document.current
        val clip = state.project.clips.getOrNull(state.selected) ?: return
        operations.start("Analisando movimento…", ::report) {
            val analysis = withContext(Dispatchers.Default) {
                MotionAnalyzer(context).analyze(clip) { progress ->
                    document.update { it.copy(progress = progress) }
                }
            }
            withContext(Dispatchers.IO) { document.store.saveAnalysis(clip.uri, analysis) }
            document.analyses[clip.uri] = analysis
            document.update { it.copy(analyzed = document.analyses.keys.toSet()) }
            val enabled = clip.copy(stabilization = clip.stabilization.copy(enabled = true))
            if (document.commit(state.project.replace(state.selected, enabled))) {
                document.message(
                    "Análise pronta. ${analysis.unreliableFrames} amostras sem rastreamento; confira a prévia."
                )
            }
        }
    }

    private fun report(error: Exception) {
        document.message(
            (error as? MediaFault)?.displayMessage() ?: "Análise falhou: ${error.localizedMessage}"
        )
    }
}
