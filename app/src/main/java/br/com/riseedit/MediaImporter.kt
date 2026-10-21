package br.com.riseedit

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import br.com.riseedit.core.Clip
import br.com.riseedit.core.MediaFault
import br.com.riseedit.media.MediaPreparation
import br.com.riseedit.media.MediaProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class MediaKind(val defaultName: String) {
    MAIN_VIDEO("Vídeo"),
    OVERLAY("Overlay"),
    AUDIO("Áudio")
}

/** Probes picked documents before inserting them into a project. Originals are never modified. */
object MediaImporter {
    fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull()

    suspend fun readClip(
        context: Context,
        uri: Uri,
        kind: MediaKind,
        progress: (Float, String) -> Unit = {
                _,
                _
            ->
        }
    ): Clip {
        val name = withContext(Dispatchers.IO) {
            // 0.19: MediaStore URIs (in-app picker) are not persistable; the permission covers them.
            if (uri.scheme ==
                "content"
            ) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }
            var displayName = kind.defaultName
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use {
                if (it.moveToFirst()) displayName = it.getString(0) ?: displayName
            }
            displayName
        }
        val probe = MediaProbe.probe(context, uri)
        probe.fault?.let { throw it }
        if (probe.durationMs <=
            0
        ) {
            throw MediaFault.ImportFailed("Não foi possível ler a duração do arquivo.")
        }
        val clip = Clip(uri = uri.toString(), name = name, sourceDurationMs = probe.durationMs)
        if (kind != MediaKind.AUDIO) return MediaPreparation(context).prepare(clip, progress)
        if (!probe.hasAudio) throw MediaFault.ImportFailed("O arquivo não contém áudio.")
        if (!probe.audioDecoderFound) throw MediaFault.MissingDecoder(probe.audioMime)
        return clip
    }
}
