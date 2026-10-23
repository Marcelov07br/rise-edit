package br.com.riseedit.editor

import android.content.Context
import android.net.Uri
import br.com.riseedit.MediaImporter
import br.com.riseedit.MediaKind
import br.com.riseedit.core.Clip
import br.com.riseedit.core.MediaFault
import br.com.riseedit.media.MediaPreparation
import br.com.riseedit.media.ProxyTranscoder
import br.com.riseedit.media.displayMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class EditorMedia(private val context: Context, private val document: EditorDocument) {
    suspend fun prepare(clip: Clip): Clip = try {
        MediaPreparation(context).prepare(clip, document::progress).also {
            document.faults.remove(clip.id)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        document.faults[clip.id] = error as? MediaFault ?: MediaFault.ImportFailed(cause = error)
        clip
    }

    suspend fun import(uris: List<Uri>, kind: MediaKind): List<Clip> {
        val clips = mutableListOf<Clip>()
        val faults = mutableListOf<String>()
        uris.forEachIndexed { index, uri ->
            try {
                document.progress(
                    index.toFloat() / uris.size,
                    "Verificando mídia ${index + 1}/${uris.size}…"
                )
                clips += readAny(uri, kind)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                faults += (error as? MediaFault)?.displayMessage()
                    ?: "Um arquivo não pôde ser importado. Selecione uma cópia compatível."
            }
        }
        if (faults.isNotEmpty()) {
            val summary = "${clips.size}/${uris.size} arquivo(s) importado(s)."
            document.message("$summary ${faults.distinct().joinToString(" ")}")
        }
        return clips
    }

    /** 0.19: photos (picker "Imagens") become still clips of Configurações › duração da imagem. */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    internal suspend fun readAny(uri: Uri, kind: MediaKind): Clip {
        val mime = withContext(Dispatchers.IO) { context.contentResolver.getType(uri) }
        if (mime?.startsWith("image/") !=
            true
        ) {
            return MediaImporter.readClip(context, uri, kind, document::progress)
        }
        val duration = br.com.riseedit.settings.SettingsStore.current.imageDurationMs
        document.progress(0f, "Preparando foto…")
        val name = MediaImporter.displayName(context, uri) ?: "Foto"
        val still = br.com.riseedit.media.StillClip.render(context, uri, duration)
        return MediaImporter.readClip(context, still, kind, document::progress).copy(name = name)
    }

    suspend fun trimCache() {
        val histories = document.history.projects + document.current.project
        val keep = withContext(Dispatchers.IO) { document.store.list() } + histories
        val uris = keep.flatMap { it.clips + it.overlays.map { overlay -> overlay.clip } }
            .mapNotNull { it.proxyUri }.toSet()
        ProxyTranscoder(context).trimCache(uris)
    }

    fun report(error: Exception) {
        val fallback = "Não foi possível ler este arquivo. " +
            "Selecione uma cópia compatível e tente novamente."
        document.message(
            (error as? MediaFault)?.displayMessage()
                ?: fallback
        )
    }
}
