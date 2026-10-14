package br.com.riseedit.motion

import android.content.Context
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Sequential hardware decode of every frame (no seeking). Only the Y plane is read; 10-bit
 * (P010) frames use the high byte. Frames closer than the minimum interval are skipped.
 */
internal class CodecFrameSource(
    private val context: Context,
    private val uri: Uri,
    private val rotationDegrees: Int
) : GrayFrameSource {
    private var bytes = ByteArray(0)

    override suspend fun read(
        minIntervalMs: Long,
        progress: (Float) -> Unit,
        onFrame: (Long, Mat) -> Unit
    ) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val track =
                (0 until extractor.trackCount).firstOrNull {
                    extractor.getTrackFormat(
                        it
                    ).getString(MediaFormat.KEY_MIME)?.startsWith("video/") ==
                        true
                } ?: error("Sem faixa de vídeo")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val durationUs = if (format.containsKey(
                    MediaFormat.KEY_DURATION
                )
            ) {
                format.getLong(MediaFormat.KEY_DURATION)
            } else {
                0L
            }
            val rotation =
                if (format.containsKey(
                        MediaFormat.KEY_ROTATION
                    )
                ) {
                    format.getInteger(MediaFormat.KEY_ROTATION)
                } else {
                    rotationDegrees
                }
            format.setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
            )
            val decoder = MediaCodec.createDecoderByType(
                requireNotNull(format.getString(MediaFormat.KEY_MIME))
            )
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()
            decodeLoop(
                decoder,
                extractor,
                minIntervalMs * US_PER_MS,
                durationUs,
                rotation,
                progress,
                onFrame
            )
        } finally {
            runCatching { codec?.stop() }
            codec?.release()
            extractor.release()
        }
    }

    @Suppress("LongParameterList", "CyclomaticComplexMethod", "NestedBlockDepth")
    private suspend fun decodeLoop(
        codec: MediaCodec,
        extractor: MediaExtractor,
        minIntervalUs: Long,
        durationUs: Long,
        rotation: Int,
        progress: (Float) -> Unit,
        onFrame: (Long, Mat) -> Unit
    ) {
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var lastUs = Long.MIN_VALUE
        var idle = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            if (!inputDone) {
                val index = codec.dequeueInputBuffer(TIMEOUT_US)
                if (index >= 0) {
                    val size = extractor.readSampleData(
                        requireNotNull(codec.getInputBuffer(index)),
                        0
                    )
                    if (size < 0) {
                        codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val out = codec.dequeueOutputBuffer(info, TIMEOUT_US)
            if (out < 0) {
                check(++idle < MAX_IDLE_POLLS) { "Decodificador parado" }
                continue
            }
            idle = 0
            val pts = info.presentationTimeUs
            if (info.size > 0 &&
                pts > lastUs &&
                (lastUs == Long.MIN_VALUE || pts - lastUs >= minIntervalUs)
            ) {
                val image = codec.getOutputImage(out) ?: error("Formato de saída sem Image")
                val gray = image.use { toGray(it, rotation) }
                try {
                    onFrame(pts / US_PER_MS, gray)
                } finally {
                    gray.release()
                }
                lastUs = pts
                if (durationUs > 0) progress((pts.toFloat() / durationUs).coerceIn(0f, 1f))
            }
            codec.releaseOutputBuffer(out, false)
            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
        }
    }

    private fun toGray(image: Image, rotation: Int): Mat {
        val plane = image.planes[0]
        val pixelStride = plane.pixelStride
        require(pixelStride == 1 || pixelStride == 2) { "pixelStride $pixelStride" }
        val rowStride = plane.rowStride
        val rows = image.height
        val buffer = plane.buffer
        val needed = rowStride * rows
        if (bytes.size != needed) bytes = ByteArray(needed)
        val count = minOf(buffer.remaining(), needed)
        buffer.get(bytes, 0, count)
        if (count < needed) bytes.fill(0, count, needed)
        val crop = image.cropRect
        val full = Mat(
            rows,
            rowStride / pixelStride,
            if (pixelStride ==
                1
            ) {
                CvType.CV_8UC1
            } else {
                CvType.CV_8UC2
            }
        )
        val luma = Mat()
        val scaled = Mat()
        try {
            full.put(0, 0, bytes)
            if (pixelStride == 2) Core.extractChannel(full, luma, 1) else full.copyTo(luma)
            val region = luma.submat(Rect(crop.left, crop.top, crop.width(), crop.height()))
            val scale = minOf(1.0, ANALYSIS_MAX_SIDE.toDouble() / max(crop.width(), crop.height()))
            val size =
                Size(
                    (crop.width() * scale).roundToInt().toDouble(),
                    (crop.height() * scale).roundToInt().toDouble()
                )
            Imgproc.resize(region, scaled, size, 0.0, 0.0, Imgproc.INTER_AREA)
            region.release()
            return rotate(scaled, rotation)
        } finally {
            full.release()
            luma.release()
        }
    }

    private fun rotate(mat: Mat, rotation: Int): Mat {
        val code =
            when (((rotation % 360) + 360) % 360) {
                90 -> Core.ROTATE_90_CLOCKWISE
                180 -> Core.ROTATE_180
                270 -> Core.ROTATE_90_COUNTERCLOCKWISE
                else -> return mat
            }
        val rotated = Mat()
        Core.rotate(mat, rotated, code)
        mat.release()
        return rotated
    }

    private companion object {
        const val US_PER_MS = 1000L
        const val TIMEOUT_US = 10_000L
        const val MAX_IDLE_POLLS = 1000
    }
}
