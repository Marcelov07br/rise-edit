package br.com.riseedit.core

/**
 * 0.21: what this phone can export, from MediaCodecList capabilities (probed in :core-transcode)
 * plus the bundled FFmpeg. Only supported combinations are offered in the export panel.
 */
data class EncoderSupport(
    val h264: Boolean = true,
    val hevc: Boolean = false,
    /** HEVC Main10 encoder (10-bit 4:2:0). */
    val hevc10: Boolean = false,
    /** HEVC encoder that takes P210 (10-bit 4:2:2) input. No public HEVC 4:2:2 profile constant exists. */
    val hevc422: Boolean = false,
    /** AVC encoder with AVCProfileHigh422 and P210 input. */
    val avc422: Boolean = false,
    /** API 36 "video/apv" encoder with APVProfile422_10 and P210 input. */
    val apv: Boolean = false,
    /** prores_ks in the bundled FFmpeg. */
    val prores: Boolean = false
)

/** How an export is produced. */
enum class ExportRoute {
    /** Media3 Transformer writes the final MP4 (H.264/HEVC 4:2:0). */
    DIRECT,
    /** Transformer → HEVC high-bitrate intermediate → FFmpeg decode → MediaCodec 4:2:2 (APV/HEVC/AVC). */
    HARDWARE_422,
    /** Transformer → intermediate → FFmpeg prores_ks .mov. */
    PRORES
}

object ExportOptions {
    fun codecs(s: EncoderSupport): List<VideoCodec> = buildList {
        if (s.h264) add(VideoCodec.H264)
        if (s.hevc || s.hevc10) add(VideoCodec.HEVC)
        if (s.apv) add(VideoCodec.APV)
        if (s.prores) add(VideoCodec.PRORES)
    }

    fun depths(codec: VideoCodec, s: EncoderSupport): List<Int> = when (codec) {
        VideoCodec.H264 -> if (s.avc422) listOf(8, 10) else listOf(8)
        VideoCodec.HEVC -> if (s.hevc10 || s.hevc422) listOf(8, 10) else listOf(8)
        VideoCodec.APV, VideoCodec.PRORES -> listOf(10)
    }

    fun chromas(codec: VideoCodec, depth: Int, s: EncoderSupport): List<Chroma> = when (codec) {
        VideoCodec.H264 -> if (depth == 10) listOf(Chroma.YUV422) else listOf(Chroma.YUV420)
        VideoCodec.HEVC -> when {
            depth == 8 -> listOf(Chroma.YUV420)
            s.hevc10 && s.hevc422 -> listOf(Chroma.YUV420, Chroma.YUV422)
            s.hevc422 -> listOf(Chroma.YUV422)
            else -> listOf(Chroma.YUV420)
        }
        VideoCodec.APV, VideoCodec.PRORES -> listOf(Chroma.YUV422)
    }

    /** Snaps [e] to the nearest supported combination (codec, then depth, then chroma). */
    fun normalize(e: ExportSettings, s: EncoderSupport): ExportSettings {
        val codecs = codecs(s)
        val codec = if (e.codec in codecs) e.codec else codecs.firstOrNull { it == VideoCodec.HEVC } ?: VideoCodec.H264
        val depths = depths(codec, s)
        // Keep the requested chroma when possible (H.264 10-bit only exists as 4:2:2, so a 4:2:0
        // request stays 8-bit), then the requested depth.
        val fitting = depths.filter { e.chroma in chromas(codec, it, s) }
        val depth = when {
            e.bitDepth in fitting -> e.bitDepth
            fitting.isNotEmpty() -> fitting.last()
            e.bitDepth in depths -> e.bitDepth
            else -> depths.last()
        }
        val chromas = chromas(codec, depth, s)
        val chroma = if (e.chroma in chromas) e.chroma else chromas.first()
        return e.copy(codec = codec, bitDepth = depth, chroma = chroma)
    }

    /** Default for new exports: HEVC 10-bit 4:2:0 when the phone has Main10, else HEVC/H.264 8-bit. */
    fun defaults(e: ExportSettings, s: EncoderSupport): ExportSettings =
        normalize(e.copy(codec = VideoCodec.HEVC, bitDepth = 10, chroma = Chroma.YUV420), s)

    fun route(e: ExportSettings): ExportRoute = when {
        e.codec == VideoCodec.PRORES -> ExportRoute.PRORES
        e.codec == VideoCodec.APV || e.chroma == Chroma.YUV422 -> ExportRoute.HARDWARE_422
        else -> ExportRoute.DIRECT
    }

    fun extension(e: ExportSettings) = if (e.codec == VideoCodec.PRORES) "mov" else "mp4"

    fun mime(e: ExportSettings) = if (e.codec == VideoCodec.PRORES) "video/quicktime" else "video/mp4"

    /** ProRes has a fixed rate per profile; bitrate only applies to the others. */
    fun usesBitrate(e: ExportSettings) = e.codec != VideoCodec.PRORES

    /** Approximate ProRes 422 data rate (Apple white paper scale: HQ ≈ 220 Mb/s at 1080p29.97). */
    fun proresMbps(e: ExportSettings, width: Int): Int {
        val perPixelFrame = if (e.prores == ProresProfile.HQ) 220.0 else 147.0
        return (perPixelFrame * (width.toLong() * e.height) / (1920.0 * 1080) * e.fps / 29.97).toInt()
    }

    fun summary(e: ExportSettings): String {
        val container = extension(e).uppercase()
        val codec = if (e.codec == VideoCodec.PRORES) "ProRes 422 ${e.prores.label}" else e.codec.label
        val audio = if (e.codec == VideoCodec.PRORES) e.movAudio.label else "AAC"
        return "$container · $codec · ${e.bitDepth} bits ${e.chroma.label} · $audio"
    }
}
