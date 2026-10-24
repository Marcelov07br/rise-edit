package br.com.riseedit.core

import kotlin.math.max
import kotlin.math.min

/** 0.24.0: preview proxy resolution (Configurações › Proxies). */
enum class ProxyResolution(val label: String, val longSide: Int, val shortSide: Int, val bitrate: Int) {
    P720("720p", 1280, 720, 4_000_000),
    P1080("1080p", 1920, 1080, 8_000_000)
}

enum class ProxyCodec(val label: String, val mime: String) {
    H264("H.264", "video/avc"),
    HEVC("HEVC 8 bits", "video/hevc")
}

/**
 * Which Direct (editable) sources get an optional preview proxy. Export never uses these
 * (Clip.proxyRequired = false); required compatibility copies stay in [Compatibility].
 */
object ProxyPolicy {
    /** Above this the decoder is the bottleneck on tablets (box estimate, not measured on the device). */
    const val HIGH_BITRATE = 60_000_000

    fun reasons(info: SourceInfo, bitrate: Long = 0): List<String> = buildList {
        val long = max(info.width, info.height)
        val short = min(info.width, info.height)
        if (long > 1920 || short > 1080) add(if (long >= 3840) "4K" else "acima de 1080p")
        if (info.bitDepth == ColorDepth.TEN || info.bitDepth == ColorDepth.TWELVE) add("${info.bitDepth.bits} bits")
        if (info.chroma == ChromaFormat.YUV422) add("4:2:2")
        if (info.chroma == ChromaFormat.YUV444) add("4:4:4")
        if (bitrate > HIGH_BITRATE) add("${bitrate / 1_000_000} Mbps")
    }

    fun heavy(info: SourceInfo, bitrate: Long = 0) = reasons(info, bitrate).isNotEmpty()

    /** Display-oriented target inside [resolution] (never upscales), even dimensions. */
    fun spec(info: SourceInfo, resolution: ProxyResolution, codec: ProxyCodec): ProxySpec {
        val rotation = ((info.rotationDegrees % 360) + 360) % 360
        val swap = rotation == 90 || rotation == 270
        val dw = (if (swap) info.height else info.width).coerceAtLeast(2)
        val dh = (if (swap) info.width else info.height).coerceAtLeast(2)
        val scale = min(
            1.0,
            min(resolution.longSide.toDouble() / max(dw, dh), resolution.shortSide.toDouble() / min(dw, dh))
        )
        fun even(v: Int) = max(2, (v * scale).toInt() / 2 * 2)
        val fps = 30
        return ProxySpec(even(dw), even(dh), resolution.bitrate, fps, codec.mime)
    }
}
