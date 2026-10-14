package br.com.riseedit.editor

import android.content.Context
import br.com.riseedit.MotionAnalyzer
import br.com.riseedit.core.MediaFault
import br.com.riseedit.core.MotionAnalysis
import br.com.riseedit.core.MotionAnalysisEvent
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
            var analysis: MotionAnalysis? = null
            MotionAnalyzer(context).analysisFlow(clip).collect { event ->
                when (event) {
                    is MotionAnalysisEvent.Progress -> document.update {
                        it.copy(progress = event.fraction)
                    }
                    is MotionAnalysisEvent.Finished -> analysis = event.analysis
                }
            }
            val result = requireNotNull(analysis)
            withContext(Dispatchers.IO) { document.store.saveAnalysis(clip.uri, result) }
            document.analyses[clip.uri] = result
            document.update { it.copy(analyzed = document.analyses.keys.toSet()) }
            val enabled = clip.copy(stabilization = clip.stabilization.copy(enabled = true))
            if (document.commit(state.project.replace(state.selected, enabled))) {
                document.message(
                    "Análise pronta. ${result.unreliableFrames} amostras sem rastreamento; confira a prévia."
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
