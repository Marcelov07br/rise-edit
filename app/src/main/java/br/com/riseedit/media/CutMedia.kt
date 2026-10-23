package br.com.riseedit.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import br.com.riseedit.core.Clip
import br.com.riseedit.core.MediaFault
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * 0.21 media side of the cut tools.
 *
 * Reverse: Media3 1.9.0 Transformer has no reverse/"play backwards" API (checked in the
 * transformer/effect sources), so the clip's trimmed range is sampled backwards with
 * MediaMetadataRetriever (OPTION_CLOSEST, [REVERSE_FPS]) into JPEG frames, and those frames are
 * encoded as a Transformer image sequence (one [REVERSE_FPS] frame per image) with a silent AAC
 * track — the same path as photos ([StillClip]). Video only; limited to [MAX_REVERSE_MS].
 */
@UnstableApi
object CutMedia {
    const val REVERSE_FPS = 25
    const val MAX_REVERSE_MS = 20_000L
    private const val LONG_SIDE = 1280

    private fun retriever(context: Context, clip: Clip) = MediaMetadataRetriever().apply {
        setDataSource(context, Uri.parse(clip.uri))
    }

    /** Freeze-frame: the source frame at [sourceMs] as a PNG (then rendered by [StillClip]). */
    suspend fun frameAt(context: Context, clip: Clip, sourceMs: Long): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "freeze").apply { mkdirs() }
        val out = File(dir, "${(clip.uri + sourceMs).hashCode().toUInt()}.png")
        if (out.length() == 0L) {
            val r = retriever(context, clip)
            try {
                val bmp = r.getFrameAtTime(sourceMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
                    ?: throw MediaFault.AssetDecodeFailed()
                out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bmp.recycle()
            } finally {
                r.release()
            }
        }
        Uri.fromFile(out)
    }

    suspend fun reverse(context: Context, clip: Clip, progress: (Float, String) -> Unit): Uri {
        val len = clip.endMs - clip.startMs
        if (len > MAX_REVERSE_MS) {
            throw MediaFault.ImportFailed("Inverter funciona em trechos de até 20 s. Divida o clipe antes.")
        }
        val dir = File(context.filesDir, "reversed").apply { mkdirs() }
        val key = "${(clip.uri + clip.startMs + clip.endMs).hashCode().toUInt()}"
        val out = File(dir, "$key.mp4")
        if (out.length() > 0) return Uri.fromFile(out)
        val frames = File(context.cacheDir, "reverse-$key").apply { deleteRecursively(); mkdirs() }
        val step = 1000L / REVERSE_FPS
        val count = maxOf(1, (len / step).toInt())
        var size = 2 to 2
        withContext(Dispatchers.IO) {
            val r = retriever(context, clip)
            try {
                for (i in 0 until count) {
                    coroutineContext.ensureActive()
                    val t = clip.endMs - 1 - i * step
                    val bmp = r.getFrameAtTime(t * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
                        ?: continue
                    if (i == 0) size = StillClip.size(bmp.width, bmp.height, LONG_SIDE)
                    File(frames, "%05d.jpg".format(i)).outputStream().use {
                        bmp.compress(Bitmap.CompressFormat.JPEG, 92, it)
                    }
                    bmp.recycle()
                    progress(0.6f * i / count, "Invertendo: lendo quadros…")
                }
            } finally {
                r.release()
            }
        }
        val files = frames.listFiles()?.sortedBy { it.name }.orEmpty()
        if (files.isEmpty()) throw MediaFault.AssetDecodeFailed()
        val (w, h) = size
        val partial = File(dir, "$key.partial.mp4")
        try {
            withContext(Dispatchers.Main.immediate) {
                val done = CompletableDeferred<Unit>()
                val engine = Transformer.Builder(context).setVideoMimeType(MimeTypes.VIDEO_H264)
                    .apply { FrameCompat.factory()?.let { setVideoFrameProcessorFactory(it) } }
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(c: Composition, r: ExportResult) { done.complete(Unit) }
                        override fun onError(c: Composition, r: ExportResult, e: ExportException) {
                            done.completeExceptionally(e)
                        }
                    }).build()
                val fit = Effects(emptyList(), listOf(Presentation.createForWidthAndHeight(w, h, Presentation.LAYOUT_SCALE_TO_FIT)))
                val items = files.map {
                    EditedMediaItem.Builder(
                        MediaItem.Builder().setUri(Uri.fromFile(it)).setImageDurationMs(step).build()
                    ).setFrameRate(REVERSE_FPS).setEffects(fit).build()
                }
                val composition = Composition.Builder(EditedMediaItemSequence.withAudioAndVideoFrom(items))
                    .experimentalSetForceAudioTrack(true).build()
                progress(0.6f, "Invertendo: codificando…")
                try {
                    engine.start(composition, partial.absolutePath)
                    done.await()
                } finally {
                    engine.cancel()
                }
            }
            withContext(Dispatchers.IO) {
                check(partial.length() > 0 && partial.renameTo(out)) { "Clipe não invertido." }
            }
        } finally {
            withContext(Dispatchers.IO) { frames.deleteRecursively() }
        }
        return Uri.fromFile(out)
    }
}
