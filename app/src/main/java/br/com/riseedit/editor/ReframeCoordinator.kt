package br.com.riseedit.editor

import android.content.Context
import br.com.riseedit.core.MediaFault
import br.com.riseedit.core.Timeline
import br.com.riseedit.core.keys.TimeDomain
import br.com.riseedit.core.motion.Reframe
import br.com.riseedit.media.SubjectDetector
import br.com.riseedit.media.displayMessage

/** 0.24.0: Formato → Enquadrar → "Reenquadrar automaticamente". Result = editable keyframes. */
internal class ReframeCoordinator(
    private val context: Context,
    private val document: EditorDocument,
    private val operations: EditorOperations
) {
    fun run(sourceRatio: Float) {
        val state = document.current
        val clip = state.project.clips.getOrNull(state.selected) ?: return
        val out = state.project.aspectRatio
        if (out <= 0f || sourceRatio <= out) {
            document.message("Escolha uma proporção mais estreita que o vídeo (ex.: 9:16).")
            return
        }
        operations.start(BUSY, ::report) {
            val samples = SubjectDetector(context).samples(clip) { f ->
                document.update { it.copy(progress = f) }
            }
            val found = samples.count { it.centerX != null }
            val base = Timeline.keyframe(clip, clip.startMs).copy(x = 0f)
            val keys = Reframe.keyframes(samples, sourceRatio, out, base)
            val next = clip.copy(
                orientation = clip.orientation.copy(scaleMode = br.com.riseedit.core.ScaleMode.FILL),
                keyframes = keys,
                tracks = br.com.riseedit.core.TransformEdits.syncTracks(null, keys, TimeDomain.SOURCE)
            )
            if (document.commit(state.project.replace(state.selected, next))) {
                document.message(
                    if (found == 0) "Nenhum rosto encontrado; quadro centralizado."
                    else "Reenquadrado: ${keys.size} keyframes ($found de ${samples.size} amostras com rosto)."
                )
            }
        }
    }

    private fun report(error: Exception) {
        document.message(
            (error as? MediaFault)?.displayMessage() ?: "Reenquadramento falhou: ${error.localizedMessage}"
        )
    }

    companion object {
        const val BUSY = "Reenquadrando…"
    }
}
