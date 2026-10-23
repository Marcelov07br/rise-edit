package br.com.riseedit.transcode

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecInfo.CodecCapabilities
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import java.io.File
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * 0.21 "Converter para edição": FFmpeg (software) decodes the 10-bit/4:2:2 source, swscale writes
 * each frame straight into the encoder's input Image, MediaCodec encodes HEVC Main10 (P010) when
 * the phone offers it, otherwise H.264 8-bit. Per-frame pts are kept (VFR), plus rotation and
 * color tags; audio is copied (AAC/AMR) or re-encoded to AAC. Video+audio muxed with MediaMuxer.
 */
class ProxyConverter(private val context: Context) {
    data class Result(val file: File, val tenBit: Boolean, val codec: String, val audio: String)

    /**
     * 0.21: an explicit encoder (export finishing: APV / HEVC 4:2:2 / AVC High 4:2:2). [layout] is
     * [FfmpegIngest.LAYOUT_P210] for 4:2:2 10-bit input.
     */
    class Target(val name: String, val mime: String, val profile: Int?, val colorFormat: Int, val layout: Int, val bitrate: Int)

    private class Encoder(val name: String, val mime: String, val tenBit: Boolean,
                          val color: Int, val layout: Int, val profile: Int?, val bitrate: Int?)

    private fun pickEncoder(info: IngestInfo): Encoder {
        val list = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { it.isEncoder }
        fun has(mime: String, color: Int, profile: Int? = null): MediaCodecInfo? = list.firstOrNull { c ->
            c.supportedTypes.any { it.equals(mime, true) } && runCatching {
                val caps = c.getCapabilitiesForType(mime)
                color in caps.colorFormats &&
                    (profile == null || caps.profileLevels.any { it.profile == profile }) &&
                    caps.videoCapabilities.isSizeSupported(info.width, info.height)
            }.getOrDefault(false)
        }
        if (info.bits >= 10 && Build.VERSION.SDK_INT >= 33) {
            has(MediaFormat.MIMETYPE_VIDEO_HEVC, CodecCapabilities.COLOR_FormatYUVP010,
                MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10)
                ?.let { return Encoder(it.name, MediaFormat.MIMETYPE_VIDEO_HEVC, true, CodecCapabilities.COLOR_FormatYUVP010,
                    FfmpegIngest.LAYOUT_P010, MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10, null) }
        }
        val flex = CodecCapabilities.COLOR_FormatYUV420Flexible
        has(MediaFormat.MIMETYPE_VIDEO_AVC, flex)?.let { return Encoder(it.name, MediaFormat.MIMETYPE_VIDEO_AVC, false, flex, FfmpegIngest.LAYOUT_8BIT, null, null) }
        has(MediaFormat.MIMETYPE_VIDEO_HEVC, flex)?.let { return Encoder(it.name, MediaFormat.MIMETYPE_VIDEO_HEVC, false, flex, FfmpegIngest.LAYOUT_8BIT, null, null) }
        throw IllegalStateException("Nenhum codificador de vídeo aceita ${info.width}x${info.height}.")
    }

    suspend fun convert(source: Uri, out: File, target: Target? = null, progress: (Float) -> Unit): Result = withContext(Dispatchers.IO) {
        check(FfmpegIngest.available) { "Conversor indisponível neste aparelho." }
        val partial = File(out.parentFile, out.name + ".partial").apply { delete() }
        val audioTemp = File(out.parentFile, out.name + ".audio.m4a").apply { delete() }
        val pfd = context.contentResolver.openFileDescriptor(source, "r")
            ?: throw IllegalStateException("Arquivo inacessível.")
        val raw = LongArray(13)
        val handle = FfmpegIngest.nativeOpen(pfd.fd, raw)
        check(handle != 0L) { "O FFmpeg não abriu este arquivo." }
        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        try {
            val info = IngestInfo.of(raw)
            val audio = AudioPlan.prepare(context, source, audioTemp)
            val enc = target?.let { Encoder(it.name, it.mime, it.layout != FfmpegIngest.LAYOUT_8BIT, it.colorFormat, it.layout, it.profile, it.bitrate) }
                ?: pickEncoder(info)
            val format = MediaFormat.createVideoFormat(enc.mime, info.width, info.height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, enc.color)
                setInteger(MediaFormat.KEY_BIT_RATE, enc.bitrate ?: ProxyPlan.bitrate(info.width, info.height, info.fps))
                setFloat(MediaFormat.KEY_FRAME_RATE, info.fps.toFloat())
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                setInteger(MediaFormat.KEY_COLOR_STANDARD, ProxyPlan.standard(info.primaries, info.matrix))
                setInteger(MediaFormat.KEY_COLOR_RANGE, ProxyPlan.range(info.range))
                // 8-bit output keeps SDR tagging only when the source is SDR; HDR→8-bit is tagged
                // as-is (no tone mapping) — see NOTES.
                setInteger(MediaFormat.KEY_COLOR_TRANSFER, ProxyPlan.transfer(info.transfer))
                enc.profile?.let { setInteger(MediaFormat.KEY_PROFILE, it) }
            }
            codec = MediaCodec.createByCodecName(enc.name).apply {
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE); start()
            }
            muxer = MediaMuxer(partial.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer.setOrientationHint(info.rotation)
            val frameBytes = when (enc.layout) {
                FfmpegIngest.LAYOUT_P210 -> info.width * info.height * 4
                FfmpegIngest.LAYOUT_P010 -> info.width * info.height * 3
                else -> info.width * info.height * 3 / 2
            }
            var videoTrack = -1
            var audioExtractor: MediaExtractor? = null
            var audioTrack = -1
            var inputDone = false
            var outputDone = false
            val bi = MediaCodec.BufferInfo()
            while (!outputDone) {
                coroutineContext.ensureActive()
                if (!inputDone) {
                    val idx = codec.dequeueInputBuffer(10_000)
                    if (idx >= 0) {
                        val img = codec.getInputImage(idx) ?: error("Codificador sem Image de entrada.")
                        val p = img.planes
                        val pts = FfmpegIngest.nativeDecode(
                            handle, p[0].buffer, p[0].rowStride,
                            p[1].buffer, p[1].rowStride, p[1].pixelStride,
                            p[2].buffer, p[2].rowStride, p[2].pixelStride, enc.layout
                        )
                        when {
                            pts >= 0 -> {
                                codec.queueInputBuffer(idx, 0, frameBytes, pts, 0)
                                progress(ProxyPlan.progress(pts, info.durationUs))
                            }
                            pts == -1L -> {
                                codec.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            }
                            else -> error("Falha ao decodificar um quadro.")
                        }
                    }
                }
                val o = codec.dequeueOutputBuffer(bi, 10_000)
                when {
                    o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        videoTrack = muxer.addTrack(codec.outputFormat)
                        audio?.file?.let { file ->
                            audioExtractor = MediaExtractor().apply { setDataSource(file.absolutePath) }
                            audioExtractor!!.selectTrack(0)
                            audioTrack = muxer.addTrack(audioExtractor!!.getTrackFormat(0))
                        }
                        muxer.start()
                    }
                    o >= 0 -> {
                        val buf = codec.getOutputBuffer(o)!!
                        if (bi.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) bi.size = 0
                        if (bi.size > 0 && videoTrack >= 0) muxer.writeSampleData(videoTrack, buf, bi)
                        codec.releaseOutputBuffer(o, false)
                        if (bi.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
            audioExtractor?.let { ex -> copySamples(ex, muxer, audioTrack); ex.release() }
            muxer.stop()
            muxer.release(); muxer = null
            check(partial.length() > 0 && partial.renameTo(out)) { "Conversão incompleta." }
            Result(out, enc.tenBit, enc.mime.removePrefix("video/"), audio?.label ?: "sem áudio")
        } finally {
            runCatching { codec?.stop() }; runCatching { codec?.release() }
            runCatching { muxer?.release() }
            FfmpegIngest.nativeClose(handle)
            pfd.close()
            partial.delete(); audioTemp.delete()
        }
    }

    companion object {
        /** True when some HEVC encoder takes P010 input with the Main10 profile (API 33+). */
        fun supportsTenBit(): Boolean = Build.VERSION.SDK_INT >= 33 && runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { c ->
                c.isEncoder && c.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, true) } &&
                    c.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_HEVC).let { caps ->
                        CodecCapabilities.COLOR_FormatYUVP010 in caps.colorFormats &&
                            caps.profileLevels.any { it.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10 }
                    }
            }
        }.getOrDefault(false)
    }

    private fun copySamples(ex: MediaExtractor, muxer: MediaMuxer, track: Int) {
        val buf = ByteBuffer.allocateDirect(1 shl 20)
        val bi = MediaCodec.BufferInfo()
        while (true) {
            val n = ex.readSampleData(buf, 0)
            if (n < 0) break
            bi.set(0, n, ex.sampleTime, if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
            muxer.writeSampleData(track, buf, bi)
            ex.advance()
        }
    }
}
