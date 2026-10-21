package br.com.riseedit.editor

import br.com.riseedit.core.Clip
import br.com.riseedit.core.ExportSettings
import br.com.riseedit.core.Stabilization
import br.com.riseedit.core.editing.SeparateAudio

internal class ProjectCommands(private val document: EditorDocument) {
    fun trimClip(original: Clip, trimmed: Clip): Boolean {
        val state = document.current
        val index = state.project.clips.indexOfFirst { it.id == original.id }
        if (index < 0 || state.project.clips[index] != original) return false
        if (trimmed != original.trim(trimmed.startMs, trimmed.endMs)) return false
        val project = SeparateAudio.sync(state.project.replace(index, trimmed), trimmed.id)
        return document.edit(project)
    }

    fun separateAudio(track: Int = 0): Boolean {
        val state = document.current
        val index = state.selected
        if (state.project.clips.getOrNull(index) == null) return false
        if (SeparateAudio.alreadySeparated(state.project, state.project.clips[index].id)) {
            document.message("Áudio já separado deste clipe.")
            return false
        }
        return document.edit(SeparateAudio.apply(state.project, index, track))
    }

    fun moveClip(id: String, target: Int) {
        val state = document.current
        val index = state.project.clips.indexOfFirst { it.id == id }
        if (index < 0) return
        val destination = target.coerceIn(0, state.project.clips.lastIndex)
        val moved = state.project.move(index, destination - index)
        if (document.edit(SeparateAudio.sync(moved, id))) {
            document.update { it.copy(selected = destination, selectionId = id) }
        }
    }

    fun projectSettings(ratio: Float, export: ExportSettings) {
        document.edit(document.current.project.copy(aspectRatio = ratio, export = export))
    }

    fun clipSettings(clip: Clip) {
        val state = document.current
        if (state.project.clips.getOrNull(state.selected)?.id == clip.id) {
            document.edit(state.project.replace(state.selected, clip))
        }
    }

    fun trim(start: Long, end: Long): Boolean {
        val state = document.current
        val clip = state.project.clips.getOrNull(state.selected) ?: return false
        return try {
            document.edit(state.project.replace(state.selected, clip.trim(start, end)))
        } catch (_: IllegalArgumentException) {
            document.message("O corte precisa ter duração maior que zero.")
            false
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

    private var clipboard: br.com.riseedit.core.editing.ClipAttributes? = null
    val hasClipboard get() = clipboard != null

    /** Opens the copy checklist with every group the selected clip actually has. */
    fun copyAttributes() {
        val clip = document.current.project.clips.getOrNull(document.current.selected) ?: return
        val shown = br.com.riseedit.core.editing.ClipAttributes.of(clip).available
        if (shown.isEmpty()) return document.message("Este clipe não tem atributos alterados para copiar.")
        document.update { it.copy(attributeDialog = br.com.riseedit.AttributeDialog(false, shown, shown.toSet())) }
    }

    fun confirmCopy(groups: Set<br.com.riseedit.core.editing.AttributeGroup>) {
        closeAttributeDialog()
        val clip = document.current.project.clips.getOrNull(document.current.selected) ?: return
        if (groups.isEmpty()) return
        clipboard = br.com.riseedit.core.editing.ClipAttributes.of(clip, groups)
        document.message("Copiado: " + clipboard!!.available.joinToString { it.label } + ".")
    }

    /** Opens the paste checklist, listing only what is in the clipboard. */
    fun pasteAttributes() {
        val attributes = clipboard ?: return document.message("Copie atributos de um clipe primeiro.")
        val shown = attributes.available
        document.update { it.copy(attributeDialog = br.com.riseedit.AttributeDialog(true, shown, shown.toSet())) }
    }

    fun closeAttributeDialog() = document.update { it.copy(attributeDialog = null) }

    fun confirmPaste(groups: Set<br.com.riseedit.core.editing.AttributeGroup>, allClips: Boolean) {
        closeAttributeDialog()
        val state = document.current
        val attributes = clipboard ?: return
        var use = groups
        // dependency on an external file: the user's .cube must still exist
        val missing = attributes.resources(use).filterNot { uri ->
            runCatching { java.io.File(java.net.URI(uri)).exists() }.getOrDefault(false)
        }
        if (missing.isNotEmpty()) use = br.com.riseedit.core.editing.AttributeCopy.close(use - br.com.riseedit.core.editing.AttributeGroup.LUT)
        if (use.isEmpty()) return
        var noAnalysis = false
        val targets = if (allClips) state.project.clips.indices.toList() else listOf(state.selected)
        val clips = state.project.clips.mapIndexed { i, clip ->
            if (i !in targets) return@mapIndexed clip
            var out = attributes.applyTo(clip, use)
            if (out.stabilization.enabled && !document.analyses.containsKey(clip.uri)) {
                out = out.copy(stabilization = out.stabilization.copy(enabled = false)); noAnalysis = true
            }
            out
        }
        if (!document.edit(state.project.copy(clips = clips))) return
        document.message(
            when {
                missing.isNotEmpty() -> "Colado sem o LUT: arquivo .cube não encontrado."
                noAnalysis -> "Colado. Analise o movimento para ativar a estabilização."
                else -> "Colado em ${targets.size} clipe(s)."
            },
            undo = true
        )
    }

    /** Applies this clip's whole color (adjust + grade) to every clip; undoable. */
    fun applyColorToAll(source: Clip) {
        val state = document.current
        val clips = state.project.clips.map { it.copy(adjustments = source.adjustments, grade = source.grade) }
        if (document.edit(state.project.copy(clips = clips))) {
            document.message("Cor aplicada a ${clips.size} clipes.", undo = true)
        }
    }

    /** Combinar cor: per clip (source ms, matched adjust); keyed clips get a key at that time. Undoable. */
    fun applyColorMatch(result: Map<String, Pair<Long, br.com.riseedit.core.ColorAdjust>>, reference: String) {
        val state = document.current
        val clips = state.project.clips.map { c ->
            val (ms, a) = result[c.id] ?: return@map c
            br.com.riseedit.core.color.ColorKeyEdits.editAdjust(c, ms, a)
        }
        if (document.edit(state.project.copy(clips = clips))) {
            document.message("Cor combinada com \"$reference\" em ${result.size} clipe(s).", undo = true)
        }
    }

    /** "Resetar todos" in Ajustar: neutral ColorAdjust (sliders, curves, HSL, wheels); undoable. */
    fun resetColor() {
        val state = document.current
        val clip = state.project.clips.getOrNull(state.selected) ?: return
        if (clip.adjustments.isNeutral && clip.colorKeys.isEmpty) return
        // Keeps only LUT-intensity keys (grade is not part of "Resetar todos").
        val keys = br.com.riseedit.core.color.ColorKeyframes(clip.colorKeys.tracks.filterKeys { it == br.com.riseedit.core.color.ColorKeyGroup.LUT })
        if (document.edit(state.project.replace(state.selected, clip.copy(adjustments = br.com.riseedit.core.ColorAdjust.NEUTRAL, colorKeys = keys)))) {
            document.message("Ajustes de cor resetados.", undo = true)
        }
    }

    fun rippleDelete() {
        val state = document.current
        if (state.project.clips.getOrNull(state.selected) == null) return
        document.edit(br.com.riseedit.core.editing.RippleDelete.clip(state.project, state.selected))
    }

    fun toggleMarker() {
        val state = document.current
        document.edit(br.com.riseedit.core.editing.Markers.toggle(state.project, state.positionMs))
    }

    fun jlCut(audioId: String, leadMs: Long, tailMs: Long) {
        document.edit(
            br.com.riseedit.core.editing.JLCut.set(
                document.current.project,
                audioId,
                leadMs,
                tailMs
            )
        )
    }

    fun addTexts(layers: List<br.com.riseedit.core.TextLayer>) {
        if (layers.isEmpty()) return document.message("Nenhuma fala reconhecida.")
        val project = document.current.project
        document.edit(project.copy(texts = project.texts + layers))
        document.message("${layers.size} legendas adicionadas.")
    }
}
