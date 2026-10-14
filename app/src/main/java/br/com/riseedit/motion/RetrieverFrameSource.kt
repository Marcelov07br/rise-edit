package br.com.riseedit.motion

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

/** Legacy fallback: one seek per sample. Slow, but works where MediaCodec output is unreadable. */
internal class RetrieverFrameSource(
    private val context: Context,
    private val uri: Uri,
    private val durationMs: Long
) : GrayFrameSource {
    override suspend fun read(
        minIntervalMs: Long,
        progress: (Float) -> Unit,
        onFrame: (Long, Mat) -> Unit
    ) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            var time = 0L
            while (time < durationMs) {
                currentCoroutineContext().ensureActive()
                val bitmap =
                    retriever.getScaledFrameAtTime(
                        time * 1000,
                        MediaMetadataRetriever.OPTION_CLOSEST,
                        ANALYSIS_MAX_SIDE,
                        ANALYSIS_MAX_SIDE
                    ) ?: error("Não foi possível decodificar o quadro em ${time}ms.")
                val rgba = Mat()
                val gray = Mat()
                try {
                    Utils.bitmapToMat(bitmap, rgba)
                    Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
                    onFrame(time, gray)
                } finally {
                    bitmap.recycle()
                    rgba.release()
                    gray.release()
                }
                progress(time.toFloat() / durationMs)
                time += LEGACY_STEP_MS
            }
        } finally {
            retriever.release()
        }
    }

    private companion object {
        const val LEGACY_STEP_MS = 42L
    }
}
