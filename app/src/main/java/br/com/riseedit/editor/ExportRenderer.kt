@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package br.com.riseedit.editor

import android.content.Context
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import br.com.riseedit.VideoEngine
import br.com.riseedit.core.MotionAnalysis
import br.com.riseedit.core.Project
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Media3 callbacks complete the caller's coroutine instead of starting another operation. */
internal class ExportRenderer(private val context: Context) {
    suspend fun render(
        project: Project,
        analyses: Map<String, MotionAnalysis>,
        file: File,
        progress: (Float) -> Unit
    ) = coroutineScope {
        val completed = CompletableDeferred<Unit>()
        val transformer = Transformer.Builder(context)
            .setEncoderFactory(
                DefaultEncoderFactory.Builder(context)
                    .setRequestedVideoEncoderSettings(
                        VideoEncoderSettings.Builder().setBitrate(project.export.bitrate).build()
                    )
                    .build()
            )
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    completed.complete(Unit)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    completed.completeExceptionally(exportException)
                }
            }).build()
        val polling = launch {
            val holder = ProgressHolder()
            while (isActive) {
                if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    progress(holder.progress / 100f)
                }
                delay(300)
            }
        }
        try {
            transformer.start(VideoEngine.composition(project, analyses), file.absolutePath)
            completed.await()
        } finally {
            withContext(NonCancellable) {
                polling.cancelAndJoin()
                transformer.cancel()
            }
        }
    }
}
