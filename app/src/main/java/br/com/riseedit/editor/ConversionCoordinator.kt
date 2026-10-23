package br.com.riseedit.editor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import androidx.core.app.NotificationCompat
import br.com.riseedit.MediaImporter
import br.com.riseedit.MediaKind
import br.com.riseedit.core.Clip
import br.com.riseedit.transcode.ProxyConverter
import br.com.riseedit.transcode.ProxyPlan
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 0.21: runs "Converter para edição" as a cancellable editor operation (progress dialog with
 * Cancelar) plus an ongoing progress notification. Output in filesDir/converted (not cache, so
 * "Limpar cache" keeps it); the clip points at the copy and keeps [Clip.originalUri].
 */
internal class ConversionCoordinator(
    private val context: Context,
    private val document: EditorDocument,
    private val operations: EditorOperations,
    private val media: EditorMedia
) {
    fun dismiss() = document.update { it.copy(conversionOffer = null) }

    fun convert() {
        val offer = document.current.conversionOffer ?: return
        dismiss()
        operations.start("Convertendo para edição…", media::report) {
            val dir = File(context.filesDir, "converted").apply { mkdirs() }
            val clips = mutableListOf<Clip>()
            var summary = ""
            try {
                offer.uris.forEachIndexed { i, s ->
                    val uri = Uri.parse(s)
                    val name = MediaImporter.displayName(context, uri) ?: "Vídeo"
                    val (size, modified) = withContext(Dispatchers.IO) { stat(uri) }
                    val out = File(dir, ProxyPlan.name(s, size, modified))
                    if (out.length() == 0L) {
                        val r = ProxyConverter(context).convert(uri, out) { p ->
                            val label = "Convertendo ${i + 1}/${offer.count} · ${(p * 100).toInt()}%"
                            document.progress(p, label)
                            notify(p, "$name · ${(p * 100).toInt()}%")
                        }
                        summary = (if (r.tenBit) "HEVC 10 bits" else "${r.codec.uppercase()} 8 bits") + " · áudio ${r.audio}"
                    }
                    clips += MediaImporter.readClip(context, Uri.fromFile(out), MediaKind.MAIN_VIDEO, document::progress)
                        .copy(name = name, originalUri = s)
                }
            } finally {
                cancelNotification()
            }
            val project = document.current.project
            if (document.commit(project.copy(clips = project.clips + clips))) {
                document.message(
                    "${clips.size} vídeo(s) convertido(s) para edição" + (if (summary.isNotEmpty()) " ($summary)." else ".") +
                        " O original foi preservado."
                )
            }
        }
    }

    private fun stat(uri: Uri): Pair<Long, Long> = runCatching {
        context.contentResolver.query(
            uri, arrayOf(android.provider.OpenableColumns.SIZE, "last_modified"), null, null, null
        )?.use { c ->
            if (!c.moveToFirst()) null else c.getLong(0) to (runCatching { c.getLong(1) }.getOrDefault(0L))
        }
    }.getOrNull() ?: (0L to 0L)

    private val manager get() = context.getSystemService(NotificationManager::class.java)

    private fun notify(progress: Float, text: String) = runCatching {
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Conversão", NotificationManager.IMPORTANCE_LOW))
        manager.notify(
            ID,
            NotificationCompat.Builder(context, CHANNEL).setContentTitle("Convertendo para edição")
                .setContentText(text).setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true).setOnlyAlertOnce(true).setProgress(1000, (progress * 1000).toInt(), false)
                .build()
        )
    }

    private fun cancelNotification() = runCatching { manager.cancel(ID) }

    private companion object {
        const val CHANNEL = "rise_convert"
        const val ID = 43
    }
}
