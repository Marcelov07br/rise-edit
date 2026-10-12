package br.com.riseedit.editor

import br.com.riseedit.core.Clip
import br.com.riseedit.core.ExportSettings
import br.com.riseedit.core.Stabilization

internal class ProjectCommands(private val document: EditorDocument) {
    fun projectSettings(ratio: Float, export: ExportSettings) {
        document.edit(document.current.project.copy(aspectRatio = ratio, export = export))
    }

    fun clipSettings(clip: Clip) {
        val state = document.current
        if (state.project.clips.getOrNull(state.selected)?.id == clip.id) {
            document.edit(state.project.replace(state.selected, clip))
        }
    }

    fun trim(start: Long, end: Long) {
        val state = document.current
        val clip = state.project.clips.getOrNull(state.selected) ?: return
        try {
            document.edit(state.project.replace(state.selected, clip.trim(start, end)))
        } catch (_: IllegalArgumentException) {
            document.message("O corte precisa ter duração maior que zero.")
        }
    }

    fun split() {
        val state = document.current
        val clip = state.project.clips.getOrNull(state.selected) ?: return
        val relative =
            state.positionMs - state.project.clips.take(state.selected).sumOf { it.durationMs }
        if (relative <= 0 || relative >= clip.durationMs) {
            document.message("Posicione o cursor dentro do clipe selecionado.")
            return
        }
        document.edit(state.project.split(state.selected, relative))
    }

    fun remove() {
        val state = document.current
        document.edit(
            state.project.copy(
                clips = state.project.clips.filterIndexed { i, _ ->
                    i !=
                        state.selected
                }
            )
        )
    }

    fun move(delta: Int) {
        val state = document.current
        if (document.edit(state.project.move(state.selected, delta))) {
            document.update {
                it.copy(
                    selected = (state.selected + delta).coerceIn(
                        0,
                        maxOf(0, state.project.clips.lastIndex)
                    )
                )
            }
        }
    }

    fun settings(settings: Stabilization) {
        val state = document.current
        val clip = state.project.clips.getOrNull(state.selected) ?: return
        if (settings.enabled && !document.analyses.containsKey(clip.uri)) {
            document.message("Analise o movimento antes de ativar.")
            return
        }
        document.edit(state.project.replace(state.selected, clip.copy(stabilization = settings)))
    }
}
