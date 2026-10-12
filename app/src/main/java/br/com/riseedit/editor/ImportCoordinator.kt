package br.com.riseedit.editor

import android.net.Uri
import br.com.riseedit.MediaKind
import br.com.riseedit.core.AudioClip
import br.com.riseedit.core.VideoOverlay

internal class ImportCoordinator(
    private val document: EditorDocument,
    private val operations: EditorOperations,
    private val media: EditorMedia
) {
    fun videos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        operations.start("Importando vídeos…", media::report) {
            val clips = media.import(uris, MediaKind.MAIN_VIDEO)
            val saved = document.commit(
                document.current.project.copy(
                    clips =
                    document.current.project.clips + clips
                )
            )
            if (saved && clips.size == uris.size) {
                document.message("${clips.size} vídeo(s) importado(s). Projeto salvo no aparelho.")
            }
            media.trimCache()
        }
    }

    fun layers(uris: List<Uri>, track: Int, video: Boolean) {
        if (uris.isEmpty() || document.current.project.clips.isEmpty()) return
        operations.start("Importando mídia…", media::report) {
            val clips = media.import(uris, if (video) MediaKind.OVERLAY else MediaKind.AUDIO)
            val project = document.current.project
            var offset = document.current.positionMs.coerceIn(0, maxOf(0, project.durationMs - 1))
            val edited = if (video) {
                project.copy(
                    overlays = project.overlays + clips.map { clip ->
                        VideoOverlay(clip = clip, offsetMs = offset, track = track).also {
                            offset +=
                                clip.durationMs
                        }
                    }
                )
            } else {
                project.copy(
                    audio = project.audio + clips.map { clip ->
                        AudioClip(
                            uri = clip.uri,
                            name = clip.name,
                            sourceDurationMs = clip.sourceDurationMs,
                            offsetMs = offset,
                            track = track
                        ).also { offset += clip.durationMs }
                    }
                )
            }
            document.commit(edited)
            media.trimCache()
        }
    }
}
