package br.com.riseedit.media

import android.content.Context
import android.graphics.BitmapFactory
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
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 0.19: a photo becomes a short H.264 clip (with a silent AAC track) so it behaves like any other
 * video in preview, timeline and export. Stored in filesDir/stills (survives "Limpar cache").
 */
@UnstableApi
object StillClip {
    const val MAX_LONG_SIDE = 1920
    const val FPS = 30

    /** Output size: aspect kept, long side ≤ [MAX_LONG_SIDE], even dimensions. */
    fun size(width: Int, height: Int, maxLong: Int = MAX_LONG_SIDE): Pair<Int, Int> {
        require(width > 0 && height > 0)
        val scale = minOf(1f, maxLong.toFloat() / maxOf(width, height))
        fun even(v: Float) = maxOf(2, (v.toInt() / 2) * 2)
        return even(width * scale) to even(height * scale)
    }

    suspend fun render(context: Context, image: Uri, durationMs: Long): Uri {
        val dir = File(context.filesDir, "stills").apply { mkdirs() }
        val out = File(dir, "${(image.toString() + durationMs).hashCode().toUInt()}.mp4")
        if (out.length() > 0) return Uri.fromFile(out)
        val bounds = withContext(Dispatchers.IO) {
            BitmapFactory.Options().apply {
                inJustDecodeBounds = true
                context.contentResolver.openInputStream(image)?.use {
                    BitmapFactory.decodeStream(it, null, this)
                }
            }
        }
        val (w, h) = size(bounds.outWidth.coerceAtLeast(2), bounds.outHeight.coerceAtLeast(2))
        val partial = File(dir, out.nameWithoutExtension + ".partial.mp4")
        withContext(Dispatchers.Main.immediate) {
            val done = CompletableDeferred<Unit>()
            val engine = Transformer.Builder(context).setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        done.complete(Unit)
                    }
                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        done.completeExceptionally(exportException)
                    }
                }).build()
            val item = EditedMediaItem.Builder(
                MediaItem.Builder().setUri(image).setImageDurationMs(durationMs).build()
            ).setFrameRate(FPS)
                .setEffects(
                    Effects(
                        emptyList(),
                        listOf(
                            Presentation.createForWidthAndHeight(
                                w,
                                h,
                                Presentation.LAYOUT_SCALE_TO_FIT
                            )
                        )
                    )
                )
                .build()
            val composition = Composition.Builder(
                EditedMediaItemSequence.withAudioAndVideoFrom(listOf(item))
            )
                .experimentalSetForceAudioTrack(true).build()
            try {
                engine.start(composition, partial.absolutePath)
                done.await()
            } finally {
                engine.cancel()
            }
        }
        withContext(Dispatchers.IO) {
            check(partial.length() > 0 && partial.renameTo(out)) { "Foto não convertida." }
        }
        return Uri.fromFile(out)
    }
}
