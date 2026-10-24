package br.com.riseedit.media

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import br.com.riseedit.core.media.ReverseMedia
import java.io.File
import java.nio.ByteOrder

/** 0.23.0: decodes [window] of the first audio track to 16-bit PCM, reverses it, writes a WAV. */
internal object ReverseAudio {
    /** Null when the source has no audio track. */
    fun write(context: Context, uri: String, window: LongRange, out: File): File? {
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            ex.setDataSource(context, Uri.parse(uri), null)
            val track = (0 until ex.trackCount).firstOrNull {
                ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            ex.selectTrack(track)
            val format = ex.getTrackFormat(track)
            ex.seekTo(window.first * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(format, null, null, 0)
            codec.start()
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            val pcm = ShortBuilder()
            var firstUs = -1L
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            while (!outputDone) {
                if (!inputDone) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val n = ex.readSampleData(codec.getInputBuffer(i)!!, 0)
                        if (n < 0 || ex.sampleTime > window.last * 1000) {
                            codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(i, 0, n, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> codec.outputFormat.let { f ->
                        rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        if (f.containsKey(MediaFormat.KEY_PCM_ENCODING)) encoding = f.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    }
                    o >= 0 -> {
                        val b = codec.getOutputBuffer(o)!!.order(ByteOrder.nativeOrder())
                        b.position(info.offset); b.limit(info.offset + info.size)
                        if (info.size > 0) {
                            if (firstUs < 0) firstUs = info.presentationTimeUs
                            if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
                                while (b.remaining() >= 4) pcm.add((b.float.coerceIn(-1f, 1f) * 32767).toInt().toShort())
                            } else {
                                while (b.remaining() >= 2) pcm.add(b.short)
                            }
                        }
                        codec.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
            if (pcm.size == 0) return null
            val all = pcm.toArray()
            val slice = ReverseMedia.slice(all, channels, rate, firstUs.coerceAtLeast(0), window.first, window.last)
            out.writeBytes(ReverseMedia.wav(ReverseMedia.reverseFrames(slice, channels), rate, channels))
            return out
        } finally {
            runCatching { codec?.stop() }
            codec?.release()
            ex.release()
        }
    }

    private class ShortBuilder {
        private var a = ShortArray(1 shl 16)
        var size = 0; private set
        fun add(v: Short) { if (size == a.size) a = a.copyOf(a.size * 2); a[size++] = v }
        fun toArray() = a.copyOf(size)
    }
}
