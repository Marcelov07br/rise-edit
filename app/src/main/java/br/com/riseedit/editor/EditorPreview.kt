@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package br.com.riseedit.editor

import android.content.Context
import br.com.riseedit.preview.PreviewEngine
import br.com.riseedit.preview.PreviewQuality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class EditorPreview(
    context: Context,
    scope: CoroutineScope,
    private val document: EditorDocument
) {
    val engine: PreviewEngine = PreviewEngine(context, scope, ::changed, document::message)

    init {
        document.onProjectChanged = ::rebuild
        scope.launch {
            while (isActive) {
                engine.updatePlayback()
                if (document.current.busy == null) {
                    document.update {
                        it.copy(
                            positionMs = engine.positionMs,
                            playing = engine.player.isPlaying,
                            previewQuality = engine.quality,
                            previewFault = engine.fault
                        )
                    }
                }
                delay(33)
            }
        }
    }

    private fun changed() = document.update {
        it.copy(
            previewQuality = engine.quality,
            previewRequested = engine.requestedQuality,
            previewGeneration = it.previewGeneration + 1
        )
    }

    fun rebuild() {
        if (document.current.busy != null) return
        try {
            val state = document.current
            val project = if (state.comparing) {
                state.project.copy(
                    clips = state.project.clips.map {
                        it.copy(stabilization = it.stabilization.copy(enabled = false))
                    }
                )
            } else {
                state.project
            }
            engine.update(
                project,
                document.analyses,
                document.faults,
                state.positionMs,
                state.previewRequested
            )
        } catch (error: Exception) {
            document.message("Prévia indisponível: ${error.localizedMessage}")
        }
    }

    fun quality(value: PreviewQuality) {
        document.update { it.copy(previewRequested = value) }
        rebuild()
    }

    fun compare() {
        document.update { it.copy(comparing = !it.comparing) }
        rebuild()
    }

    fun mute() {
        val muted = !document.current.muted
        engine.setMuted(muted)
        document.update { it.copy(muted = muted) }
    }

    fun seek(ms: Long) {
        if (document.current.busy != null) return
        val bounded = ms.coerceIn(0, document.current.project.durationMs)
        engine.seek(bounded)
        document.update { it.copy(positionMs = bounded, previewFault = engine.fault) }
    }

    fun select(index: Int) {
        if (document.current.busy != null) return
        document.update { it.copy(selected = index, selection = null) }
        seek(document.current.project.clips.take(index).sumOf { it.durationMs })
    }

    /** Selects a main clip without moving the playhead (used by direct preview manipulation). */
    fun focus(index: Int) {
        if (document.current.busy != null) return
        document.update { it.copy(selected = index, selection = null) }
    }

    fun togglePlay() {
        if (document.current.busy == null) engine.togglePlay()
    }
}
