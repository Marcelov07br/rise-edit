package br.com.riseedit

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import br.com.riseedit.core.Clip

/** What a picked document must contain to be accepted, and how it is named when the provider gives no name. */
enum class MediaKind(val metadataKey: Int, val defaultName: String, val error: String) {
    MAIN_VIDEO(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO, "Vídeo", "O arquivo não contém vídeo."),
    OVERLAY(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO, "Overlay", "Mídia incompatível."),
    AUDIO(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO, "Áudio", "Mídia incompatível.")
}

/** Reads metadata of documents chosen with the Android picker. Blocking: call from a background dispatcher. */
object MediaImporter {
    /** Persists read access to [uri], validates its contents and returns a clip covering the whole source. */
    fun readClip(context: Context, uri: Uri, kind: MediaKind): Clip {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            check(retriever.extractMetadata(kind.metadataKey) == "yes") { kind.error }
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            check(duration > 0) { "Não foi possível ler a duração do arquivo." }
            var name = kind.defaultName
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) name = it.getString(0)
            }
            return Clip(uri = uri.toString(), name = name, sourceDurationMs = duration)
        } finally {
            retriever.release()
        }
    }
}
