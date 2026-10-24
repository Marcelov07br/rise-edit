package br.com.riseedit.editor

import br.com.riseedit.core.AudioClip
import br.com.riseedit.core.TextLayer
import br.com.riseedit.core.Timeline
import br.com.riseedit.core.VideoOverlay

internal class LayerCommands(private val document: EditorDocument) {
    fun select(id: String?) {
        if (document.current.busy == null) document.update { it.copy(selectionId = id) }
    }

    /** 0.25.0: a caption look edit with [LookScope.ALL] restyles its whole track in ONE undo step. */
    fun text(layer: TextLayer, scope: br.com.riseedit.core.LookScope = br.com.riseedit.core.LookScope.ONLY_THIS) {
        val project = document.current.project
        val texts = br.com.riseedit.core.CaptionScope.apply(project.texts, layer, scope)
        if (document.edit(project.copy(texts = texts))) select(layer.id)
    }

    fun audio(layer: AudioClip) {
        val project = document.current.project
        document.edit(
            project.copy(
                audio = project.audio.map {
                    if (it.id ==
                        layer.id
                    ) {
                        layer
                    } else {
                        it
                    }
                }
            )
        )
    }

    fun overlay(layer: VideoOverlay) {
        val project = document.current.project
        document.edit(
            project.copy(
                overlays = project.overlays.map {
                    if (it.id ==
                        layer.id
                    ) {
                        layer
                    } else {
                        it
                    }
                }
            )
        )
    }

    fun adjustment(layer: br.com.riseedit.core.AdjustmentLayer) {
        val project = document.current.project
        document.edit(project.copy(adjustmentLayers = project.adjustmentLayers.map { if (it.id == layer.id) layer else it }))
    }

    /** New camada de ajuste at the playhead (3 s), selected; undoable. */
    fun addAdjustment() {
        val state = document.current
        if (state.project.clips.isEmpty()) return document.message("Adicione um vídeo antes da camada de ajuste.")
        val layer = br.com.riseedit.core.AdjustmentLayer.at(state.project, state.positionMs)
        android.util.Log.i("RiseColor", "adjustment layer added ${layer.id} ${layer.startMs}+${layer.durationMs}ms")
        if (document.edit(state.project.copy(adjustmentLayers = state.project.adjustmentLayers + layer))) {
            select(layer.id)
            document.message("Camada de ajuste adicionada. Toque em Ajustar para editar a cor.", undo = true)
        }
    }

    fun trimAdjustment(original: br.com.riseedit.core.AdjustmentLayer, trimmed: br.com.riseedit.core.AdjustmentLayer): Boolean {
        val project = document.current.project
        if (project.adjustmentLayers.none { it == original }) return false
        return document.edit(project.copy(adjustmentLayers = project.adjustmentLayers.map { if (it.id == original.id) trimmed else it }))
    }

    fun remove() {
        val project = document.current.project
        val id = document.current.layerId
        val edited = project.copy(
            adjustmentLayers = project.adjustmentLayers.filter { it.id != id },
            audio = project.audio.filter { it.id != id },
            texts = project.texts.filter { it.id != id },
            overlays = project.overlays.filter { it.id != id }
        )
        if (document.edit(edited)) select(null)
    }

    fun trimOverlay(
        original: br.com.riseedit.core.VideoOverlay,
        trimmed: br.com.riseedit.core.VideoOverlay
    ): Boolean {
        val project = document.current.project
        if (project.overlays.none { it.id == original.id && it == original }) return false
        return document.edit(
            project.copy(
                overlays = project.overlays.map {
                    if (it.id ==
                        original.id
                    ) {
                        trimmed
                    } else {
                        it
                    }
                }
            )
        )
    }

    fun trimAudio(
        original: br.com.riseedit.core.AudioClip,
        trimmed: br.com.riseedit.core.AudioClip
    ): Boolean {
        val project = document.current.project
        if (project.audio.none { it.id == original.id && it == original }) return false
        return document.edit(
            project.copy(
                audio = project.audio.map {
                    if (it.id == original.id) trimmed.copy(linkedClipId = null) else it
                }
            )
        )
    }

    fun move(id: String, offset: Long, snapping: Boolean) {
        val project = document.current.project
        val boundaries =
            listOf(0L, project.durationMs) +
                project.clips.runningFold(0L) { t, c -> t + c.durationMs }
        val time = (if (snapping) Timeline.snap(offset, boundaries) else offset)
            .coerceIn(0, maxOf(0, project.durationMs - 1))
        document.edit(
            project.copy(
                audio = project.audio.map {
                    if (it.id == id) it.copy(offsetMs = time, linkedClipId = null) else it
                },
                overlays = project.overlays.map {
                    if (it.id ==
                        id
                    ) {
                        it.copy(offsetMs = time)
                    } else {
                        it
                    }
                },
                texts = project.texts.map {
                    if (it.id ==
                        id
                    ) {
                        it.copy(startMs = time, endMs = time + it.endMs - it.startMs)
                    } else {
                        it
                    }
                },
                adjustmentLayers = project.adjustmentLayers.map {
                    if (it.id == id) br.com.riseedit.core.AdjustmentStack.move(it, time) else it
                }
            )
        )
    }
}
