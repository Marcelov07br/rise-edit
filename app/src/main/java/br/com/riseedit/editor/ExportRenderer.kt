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
    /**
     * 0.19.2: an empty-frame-queue failure (see FrameCompat) is retried once in the tolerant
     * frame mode; any other failure, or a second one, reaches the caller with a full RiseColor log.
     */
    suspend fun render(
        project: Project,
        analyses: Map<String, MotionAnalysis>,
        file: File,
        progress: (Float) -> Unit
    ) {
        try {
            renderOnce(
                project,
                analyses,
                file,
                br.com.riseedit.media.FrameCompat.factory(),
                progress
            )
        } catch (error: ExportException) {
            br.com.riseedit.diagnostics.ColorDiagnostics.report(
                context,
                "exportação",
                error,
                project
            )
            if (!br.com.riseedit.media.FrameCompat.isFrameRegistrationFailure(error) ||
                br.com.riseedit.media.FrameCompat.tolerant
            ) {
                throw error
            }
            br.com.riseedit.media.FrameCompat.enable(context)
            br.com.riseedit.diagnostics.Diagnostics.log(
                "cor",
                "exportação: repetindo no modo compatível de quadros"
            )
            file.delete()
            progress(0f)
            renderOnce(
                project,
                analyses,
                file,
                br.com.riseedit.media.FrameCompat.tolerantFactory(),
                progress
            )
        }
    }

    private suspend fun renderOnce(
        project: Project,
        analyses: Map<String, MotionAnalysis>,
        file: File,
        frames: androidx.media3.common.VideoFrameProcessor.Factory?,
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
            .setVideoMimeType(
                if (project.export.codec == br.com.riseedit.core.VideoCodec.HEVC) {
                    MimeTypes.VIDEO_H265
                } else {
                    MimeTypes.VIDEO_H264
                }
            )
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .apply { frames?.let { setVideoFrameProcessorFactory(it) } }
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
