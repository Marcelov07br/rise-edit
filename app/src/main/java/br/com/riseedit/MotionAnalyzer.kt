package br.com.riseedit

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import br.com.riseedit.core.Clip
import br.com.riseedit.core.FrameDelta
import br.com.riseedit.core.MediaFault
import br.com.riseedit.core.MotionAnalysis
import br.com.riseedit.core.MotionAnalysisEvent
import br.com.riseedit.core.MotionSample
import br.com.riseedit.core.MotionTracking
import br.com.riseedit.motion.CodecFrameSource
import br.com.riseedit.motion.FeatureTracker
import br.com.riseedit.motion.GrayFrameSource
import br.com.riseedit.motion.MotionAnalysisCache
import br.com.riseedit.motion.RetrieverFrameSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import org.opencv.android.OpenCVLoader
import org.opencv.core.Mat

/**
 * Global rigid motion analysis. Frames come from a sequential MediaCodec decode (fast path) or,
 * if the device codec output cannot be read, from per-frame MediaMetadataRetriever seeks.
 * UI-agnostic: collect [analysisFlow] for progress and the result; results are cached per source.
 */
class MotionAnalyzer(
    private val context: Context,
    private val cache: MotionAnalysisCache? = MotionAnalysisCache(context)
) {
    fun analysisFlow(clip: Clip): Flow<MotionAnalysisEvent> = channelFlow {
        val cached = cache?.load(clip.uri)
        if (cached != null) {
            send(MotionAnalysisEvent.Finished(cached, fromCache = true))
            return@channelFlow
        }
        val analysis = analyzeUncached(clip) { trySend(MotionAnalysisEvent.Progress(it)) }
        runCatching { cache?.save(clip.uri, analysis) }
        send(MotionAnalysisEvent.Finished(analysis, fromCache = false))
    }.buffer(Channel.CONFLATED).flowOn(Dispatchers.Default)

    suspend fun analyze(clip: Clip, progress: (Float) -> Unit): MotionAnalysis {
        var result: MotionAnalysis? = null
        analysisFlow(clip).collect { event ->
            when (event) {
                is MotionAnalysisEvent.Progress -> progress(event.fraction)
                is MotionAnalysisEvent.Finished -> result = event.analysis
            }
        }
        progress(1f)
        return requireNotNull(result)
    }

    private suspend fun analyzeUncached(clip: Clip, progress: (Float) -> Unit): MotionAnalysis {
        check(OpenCVLoader.initLocal()) { "OpenCV não pôde iniciar neste aparelho." }
        require(clip.sourceDurationMs <= MAX_DURATION_MS) {
            "Nesta versão, analise vídeos de até 20 minutos."
        }
        val uri = Uri.parse(clip.effectiveUri(preferProxy = true))
        val fast = CodecFrameSource(
            context,
            uri,
            clip.sourceInfo?.rotationDegrees ?: rotationOf(uri)
        )
        return try {
            track(fast, clip, progress)
        } catch (e: CancellationException) {
            throw e
        } catch (e: MediaFault) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception
        ) {
            Log.w(TAG, "MediaCodec analysis failed; falling back to MediaMetadataRetriever", e)
            require(clip.sourceDurationMs <= LEGACY_MAX_DURATION_MS) {
                "Este aparelho não decodificou o vídeo em sequência; analise trechos de até 2 minutos."
            }
            track(RetrieverFrameSource(context, uri, clip.sourceDurationMs), clip, progress)
        }
    }

    private suspend fun track(
        source: GrayFrameSource,
        clip: Clip,
        progress: (Float) -> Unit
    ): MotionAnalysis {
        var previous: Mat? = null
        var firstMs = -1L
        val deltas = mutableListOf<FrameDelta>()
        var width = 0
        var height = 0
        try {
            source.read(MIN_INTERVAL_MS, progress) { timeMs, gray ->
                width = gray.cols()
                height = gray.rows()
                val prev = previous
                if (prev ==
                    null
                ) {
                    firstMs = timeMs
                } else {
                    deltas +=
                        FrameDelta(timeMs, FeatureTracker.estimate(prev, gray))
                }
                prev?.release()
                previous = gray.clone()
            }
        } finally {
            previous?.release()
        }
        check(firstMs >= 0 && width > 0) { "Nenhum quadro decodificado." }
        val unreliable = deltas.count { it.delta == null }
        if (deltas.isEmpty() ||
            unreliable >= (deltas.size + 1) * 0.5
        ) {
            throw MediaFault.StabilizationNoTexture
        }
        val samples = MotionTracking.trajectory(firstMs, deltas).toMutableList()
        val last = samples.last()
        if (last.timeMs <
            clip.sourceDurationMs
        ) {
            samples +=
                MotionSample(clip.sourceDurationMs, last.pose, last.segment)
        }
        return MotionAnalysis(width, height, samples, unreliable)
    }

    private fun rotationOf(uri: Uri): Int = runCatching {
        MediaMetadataRetriever().run {
            try {
                setDataSource(context, uri)
                extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
                )?.toIntOrNull()
                    ?: 0
            } finally {
                release()
            }
        }
    }.getOrDefault(0)

    private companion object {
        const val TAG = "RiseMotion"
        const val MIN_INTERVAL_MS = 30L
        const val MAX_DURATION_MS = 20 * 60_000L
        const val LEGACY_MAX_DURATION_MS = 120_000L
    }
}
