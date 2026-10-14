package br.com.riseedit.editor

import br.com.riseedit.core.AudioClip
import br.com.riseedit.core.TextLayer
import br.com.riseedit.core.Timeline
import br.com.riseedit.core.VideoOverlay

internal class LayerCommands(private val document: EditorDocument) {
    fun select(id: String?) {
        if (document.current.busy == null) document.update { it.copy(selectionId = id) }
    }

    fun text(layer: TextLayer) {
        val project = document.current.project
        val texts = if (project.texts.any { it.id == layer.id }) {
            project.texts.map { if (it.id == layer.id) layer else it }
        } else {
            project.texts + layer
        }
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

    fun remove() {
        val project = document.current.project
        val id = document.current.layerId
        val edited = project.copy(
            audio = project.audio.filter { it.id != id },
            texts = project.texts.filter { it.id != id },
            overlays = project.overlays.filter { it.id != id }
        )
        if (document.edit(edited)) select(null)
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
                audio = project.audio.map { if (it.id == id) it.copy(offsetMs = time) else it },
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
                }
            )
        )
    }
}
