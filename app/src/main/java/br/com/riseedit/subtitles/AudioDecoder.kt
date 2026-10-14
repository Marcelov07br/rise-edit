package br.com.riseedit.subtitles

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder

/** Decodes [startMs, endMs) of a file's first audio track to 16 kHz mono float (Whisper input). */
internal object AudioDecoder {
    private const val TARGET_RATE = 16_000

    @Suppress("CyclomaticComplexMethod", "LongMethod", "NestedBlockDepth")
    fun decode(context: Context, uri: String, startMs: Long, endMs: Long): FloatArray {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, Uri.parse(uri), null)
        val track = (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") ==
                true
        } ?: run {
            extractor.release()
            return FloatArray(0)
        }
        extractor.selectTrack(track)
        val format = extractor.getTrackFormat(track)
        extractor.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(format, null, null, 0)
        codec.start()
        var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var pcmFloat = false
        val mono = ArrayList<Float>(((endMs - startMs) * 48).toInt().coerceAtLeast(0))
        var firstUs = -1L
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        try {
            while (!outputDone) {
                if (!inputDone) {
                    val index = codec.dequeueInputBuffer(10_000)
                    if (index >= 0) {
                        val buffer = codec.getInputBuffer(index)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0 || extractor.sampleTime > endMs * 1000) {
                            codec.queueInputBuffer(
                                index,
                                0,
                                0,
                                0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val out = codec.dequeueOutputBuffer(info, 10_000)
                if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val f = codec.outputFormat
                    rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    pcmFloat = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        f.getInteger(MediaFormat.KEY_PCM_ENCODING) ==
                        android.media.AudioFormat.ENCODING_PCM_FLOAT
                } else if (out >= 0) {
                    val buffer = codec.getOutputBuffer(out)!!.order(ByteOrder.nativeOrder())
                    buffer.position(info.offset)
                    buffer.limit(info.offset + info.size)
                    if (info.presentationTimeUs >= startMs * 1000 &&
                        info.presentationTimeUs < endMs * 1000
                    ) {
                        if (firstUs < 0) firstUs = info.presentationTimeUs
                        if (pcmFloat) {
                            val fb = buffer.asFloatBuffer()
                            while (fb.remaining() >= channels) {
                                var sum = 0f
                                repeat(channels) { sum += fb.get() }
                                mono += sum / channels
                            }
                        } else {
                            val sb = buffer.asShortBuffer()
                            while (sb.remaining() >= channels) {
                                var sum = 0f
                                repeat(channels) { sum += sb.get() / 32768f }
                                mono += sum / channels
                            }
                        }
                    }
                    codec.releaseOutputBuffer(out, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
        } finally {
            codec.stop()
            codec.release()
            extractor.release()
        }
        // Pad the start if decoding began late so timestamps stay aligned with startMs.
        val padSamples = if (firstUs >
            startMs * 1000
        ) {
            ((firstUs / 1000 - startMs) * rate / 1000).toInt()
        } else {
            0
        }
        val source = FloatArray(padSamples + mono.size)
        for (i in mono.indices) source[padSamples + i] = mono[i]
        return resample(source, rate)
    }

    private fun resample(input: FloatArray, rate: Int): FloatArray {
        if (rate == TARGET_RATE || input.isEmpty()) return input
        val ratio = rate.toDouble() / TARGET_RATE
        val out = FloatArray((input.size / ratio).toInt())
        for (i in out.indices) {
            // Box average over the source window: cheap anti-aliasing for downsampling.
            val a = (i * ratio).toInt()
            val b = ((i + 1) * ratio).toInt().coerceIn(a + 1, input.size)
            var sum = 0f
            for (j in a until b) sum += input[j]
            out[i] = sum / (b - a)
        }
        return out
    }
}
