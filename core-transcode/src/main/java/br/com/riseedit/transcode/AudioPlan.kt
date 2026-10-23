package br.com.riseedit.transcode

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import java.nio.ByteBuffer

/**
 * Audio of the converted proxy, written to a one-track temp m4a: AAC/AMR are copied (remux);
 * anything else (e.g. LPCM in camera .mov) is decoded by MediaCodec and re-encoded to AAC 256k.
 */
internal object AudioPlan {
    class Out(val file: File, val label: String)

    private val copyable = setOf(MediaFormat.MIMETYPE_AUDIO_AAC, MediaFormat.MIMETYPE_AUDIO_AMR_NB, MediaFormat.MIMETYPE_AUDIO_AMR_WB)

    fun canCopy(mime: String?) = mime in copyable

    fun prepare(context: Context, source: Uri, temp: File): Out? {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(context, source, null)
            val track = (0 until ex.trackCount).firstOrNull {
                ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            ex.selectTrack(track)
            val fmt = ex.getTrackFormat(track)
            val mime = fmt.getString(MediaFormat.KEY_MIME)
            return runCatching {
                if (canCopy(mime)) { remux(ex, fmt, temp); Out(temp, "copiado") }
                else { reencode(ex, fmt, temp); Out(temp, "AAC") }
            }.onFailure { android.util.Log.w("RiseIngest", "audio skipped ($mime)", it) }.getOrNull()
        } finally {
            ex.release()
        }
    }

    private fun remux(ex: MediaExtractor, fmt: MediaFormat, temp: File) {
        val m = MediaMuxer(temp.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val t = m.addTrack(fmt)
        m.start()
        val buf = ByteBuffer.allocateDirect(1 shl 20)
        val bi = MediaCodec.BufferInfo()
        while (true) {
            val n = ex.readSampleData(buf, 0)
            if (n < 0) break
            bi.set(0, n, ex.sampleTime, MediaCodec.BUFFER_FLAG_KEY_FRAME)
            m.writeSampleData(t, buf, bi)
            ex.advance()
        }
        m.stop(); m.release()
    }

    private fun reencode(ex: MediaExtractor, fmt: MediaFormat, temp: File) {
        val rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val ch = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceIn(1, 2)
        val dec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
        dec.configure(fmt, null, null, 0); dec.start()
        val aac = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, ch).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 256_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1 shl 16)
        }
        val enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        enc.configure(aac, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE); enc.start()
        val m = MediaMuxer(temp.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1
        val srcChannels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val bi = MediaCodec.BufferInfo()
        val ebi = MediaCodec.BufferInfo()
        var extractorDone = false
        var decoderDone = false
        var done = false
        var eosSent = false
        val pending = java.util.ArrayDeque<Pair<ByteArray, Long>>()
        try {
            while (!done) {
                if (!extractorDone) {
                    val i = dec.dequeueInputBuffer(5_000)
                    if (i >= 0) {
                        val n = ex.readSampleData(dec.getInputBuffer(i)!!, 0)
                        if (n < 0) { dec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); extractorDone = true }
                        else { dec.queueInputBuffer(i, 0, n, ex.sampleTime, 0); ex.advance() }
                    }
                }
                if (!decoderDone) {
                    val o = dec.dequeueOutputBuffer(bi, 5_000)
                    if (o >= 0) {
                        val b = dec.getOutputBuffer(o)!!
                        val pcm = ByteArray(bi.size).also { b.position(bi.offset); b.get(it) }
                        pending += downmix(pcm, srcChannels, ch) to bi.presentationTimeUs
                        dec.releaseOutputBuffer(o, false)
                        if (bi.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) decoderDone = true
                    }
                }
                val i = if (pending.isNotEmpty() || (decoderDone && !eosSent)) enc.dequeueInputBuffer(5_000) else -1
                if (i >= 0) {
                    val next = pending.pollFirst()
                    if (next != null) {
                        val ib = enc.getInputBuffer(i)!!
                        val n = minOf(ib.capacity(), next.first.size)
                        ib.put(next.first, 0, n)
                        enc.queueInputBuffer(i, 0, n, next.second, 0)
                        if (n < next.first.size) {
                            val restUs = next.second + n.toLong() / (2 * ch) * 1_000_000 / rate
                            pending.addFirst(next.first.copyOfRange(n, next.first.size) to restUs)
                        }
                    } else if (decoderDone) {
                        enc.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        eosSent = true
                    }
                }
                val o = enc.dequeueOutputBuffer(ebi, 5_000)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) { track = m.addTrack(enc.outputFormat); m.start() }
                else if (o >= 0) {
                    if (ebi.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) ebi.size = 0
                    if (ebi.size > 0 && track >= 0) m.writeSampleData(track, enc.getOutputBuffer(o)!!, ebi)
                    enc.releaseOutputBuffer(o, false)
                    if (ebi.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) done = true
                }
            }
            m.stop()
        } finally {
            runCatching { dec.stop() }; dec.release()
            runCatching { enc.stop() }; enc.release()
            runCatching { m.release() }
        }
    }

    /** 16-bit PCM: keeps the first [out] channels (multichannel camera audio → front L/R). */
    fun downmix(pcm: ByteArray, inCh: Int, out: Int): ByteArray {
        if (inCh <= out) return pcm
        val frames = pcm.size / (2 * inCh)
        val r = ByteArray(frames * 2 * out)
        for (f in 0 until frames) for (c in 0 until out) {
            r[(f * out + c) * 2] = pcm[(f * inCh + c) * 2]
            r[(f * out + c) * 2 + 1] = pcm[(f * inCh + c) * 2 + 1]
        }
        return r
    }
}
