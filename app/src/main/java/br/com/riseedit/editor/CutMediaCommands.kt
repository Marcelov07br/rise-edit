package br.com.riseedit.editor

import android.content.Context
import android.net.Uri
import br.com.riseedit.MediaImporter
import br.com.riseedit.MediaKind
import br.com.riseedit.core.editing.MediaReplace
import br.com.riseedit.core.editing.SeparateAudio
import br.com.riseedit.core.editing.SplitAtPlayhead
import br.com.riseedit.media.CutMedia
import br.com.riseedit.media.StillClip

/** 0.21 cut tools that create media: freeze-frame, replace media, reverse. One undo step each. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class CutMediaCommands(
    private val context: Context,
    private val document: EditorDocument,
    private val operations: EditorOperations,
    private val media: EditorMedia
) {
    private companion object { const val STILL_SOURCE_MS = 10_000L }

    /** Splits at the playhead (if inside) and inserts a still of that frame for [durationMs]. */
    fun freezeFrame(durationMs: Long) {
        val state = document.current
        val project = state.project
        val clip = project.clips.getOrNull(state.selected) ?: return
        val before = project.clips.take(state.selected).sumOf { it.durationMs }
        val relative = (state.positionMs - before).coerceIn(0, clip.durationMs - 1)
        val sourceMs = clip.sourceAt(relative)
        operations.start("Congelando quadro…", media::report) {
            val png = CutMedia.frameAt(context, clip, sourceMs)
            // Rendered longer than needed so the still can be extended with the trim handles.
            val mp4 = StillClip.render(context, png, STILL_SOURCE_MS)
            val read = MediaImporter.readClip(context, mp4, MediaKind.MAIN_VIDEO, document::progress)
            val still = read.copy(
                name = "Congelado · ${clip.name}",
                endMs = durationMs.coerceAtMost(read.sourceDurationMs)
            )
            val split = SplitAtPlayhead.apply(
                project, br.com.riseedit.core.Selection.Clip(clip.id), state.positionMs, project.export.fps
            )
            val base = split?.project ?: project
            val at = split?.let { r -> base.clips.indexOfFirst { it.id == r.rightId } }
                ?: (state.selected + if (relative > clip.durationMs / 2) 1 else 0)
            if (document.edit(MediaReplace.insert(base, at, still))) {
                document.update { it.copy(selected = at, selectionId = still.id) }
                document.message("Quadro congelado por ${durationMs / 1000.0} s.")
            }
        }
    }

    /** Swaps the selected clip's media (picked in the app's own picker), keeping effects. */
    fun replace(uri: Uri?) {
        uri ?: return
        val state = document.current
        val old = state.project.clips.getOrNull(state.selected) ?: return
        operations.start("Substituindo mídia…", media::report) {
            val fresh = media.readAny(uri, MediaKind.MAIN_VIDEO)
            val next = MediaReplace.replace(old, fresh)
            val index = document.current.project.clips.indexOfFirst { it.id == old.id }
            if (index < 0) return@start
            val project = SeparateAudio.sync(document.current.project.replace(index, next), old.id)
            if (document.edit(project)) {
                document.message(
                    if (old.stabilization.enabled) "Mídia substituída. Analise a estabilização de novo."
                    else "Mídia substituída; duração e efeitos mantidos."
                )
            }
        }
    }

    /** Reverses the selected clip's trimmed range (video only; see [CutMedia.reverse]). */
    fun reverse() {
        val state = document.current
        val old = state.project.clips.getOrNull(state.selected) ?: return
        operations.start("Invertendo clipe…", media::report) {
            val mp4 = CutMedia.reverse(context, old, document::progress)
            val fresh = MediaImporter.readClip(context, mp4, MediaKind.MAIN_VIDEO, document::progress)
            val next = MediaReplace.replace(old, fresh).copy(name = "${old.name} (invertido)", speedPoints = emptyList())
            val index = document.current.project.clips.indexOfFirst { it.id == old.id }
            if (index < 0) return@start
            if (document.edit(SeparateAudio.sync(document.current.project.replace(index, next), old.id))) {
                document.message("Clipe invertido (só vídeo; o áudio fica em silêncio).")
            }
        }
    }
}
