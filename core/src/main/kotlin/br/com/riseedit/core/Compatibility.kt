package br.com.riseedit.core

import kotlin.math.max
import kotlin.math.min

data class DeviceProfile(
    val canDecode: Set<String>,
    val maxDecoders: Int = 1,
    val supports10Bit: Boolean = false,
    val supports422: Boolean = false
)

data class ProxySpec(
    val width: Int,
    val height: Int,
    val bitrate: Int,
    val fps: Int,
    /** 0.24.0: "video/avc" or "video/hevc" (8-bit Main). */
    val mime: String = "video/avc"
)

sealed class SourcePlan {
    object Direct : SourcePlan()
    data class Proxy(val reason: String, val target: ProxySpec) : SourcePlan()
}

/**
 * P0 edits known 4:2:0 SDR sources directly when their actual format has a decoder.
 * APV, 4:2:2/4:4:4, unsupported depth, missing decoders and sources above UHD 4K
 * request a proxy. Unknown depth/chroma/dimensions also request a proxy instead of
 * silently being treated as ordinary 8-bit media. Profile/level zero alone is harmless
 * once the Android adapter has tested the complete format against a decoder.
 *
 * A proxy plan describes the editing requirement, not a promise that this device can
 * transcode the input. The Android adapter must reject a missing source decoder before
 * starting proxy generation. [decoded] is the result for this source's actual format;
 * [DeviceProfile.canDecode] is only the device's broad MIME inventory.
 */
object Compatibility {
    fun plan(info: SourceInfo, decoded: Boolean, device: DeviceProfile): SourcePlan {
        val mime = info.mime.trim().lowercase()
        val reason = when {
            mime == "video/apv" || mime == "video/x-apv" -> "APV exige uma cópia compatível para edição."
            info.chroma == ChromaFormat.YUV422 -> "Vídeo 4:2:2 exige uma cópia 4:2:0 para edição."
            info.chroma == ChromaFormat.YUV444 -> "Vídeo 4:4:4 exige uma cópia 4:2:0 para edição."
            info.bitDepth == ColorDepth.TWELVE -> "Vídeo de 12 bits exige uma cópia de 8 bits para edição."
            info.bitDepth == ColorDepth.TEN && !device.supports10Bit ->
                "Este aparelho precisa de uma cópia de 8 bits para editar vídeo de 10 bits."
            !decoded || device.maxDecoders <= 0 ->
                "Não há decodificador compatível com este vídeo no aparelho."
            above4K(info) -> "Vídeo acima de 4K exige uma cópia menor para edição."
            info.isHdr -> "Vídeo HDR exige uma cópia SDR para a edição neste projeto."
            mime.isBlank() ||
                info.width <= 0 ||
                info.height <= 0 ||
                info.bitDepth == ColorDepth.UNKNOWN ||
                info.chroma == ChromaFormat.UNKNOWN ->
                "Os dados do vídeo estão incompletos; é necessária uma cópia compatível para edição."
            else -> null
        }
        return if (reason == null) SourcePlan.Direct else SourcePlan.Proxy(reason, proxySpec(info))
    }

    private fun above4K(info: SourceInfo): Boolean =
        max(info.width, info.height) > 3840 || min(info.width, info.height) > 2160

    /** Fit display orientation inside 1920x1080 with even encoder dimensions. */
    private fun proxySpec(info: SourceInfo): ProxySpec {
        if (info.width <= 0 || info.height <= 0) return ProxySpec(1920, 1080, 6_000_000, 30)
        val rotation = ((info.rotationDegrees % 360) + 360) % 360
        val swapDimensions = rotation == 90 || rotation == 270
        val displayWidth = if (swapDimensions) info.height else info.width
        val displayHeight = if (swapDimensions) info.width else info.height
        val longSide = max(displayWidth, displayHeight).toDouble()
        val shortSide = min(displayWidth, displayHeight).toDouble()
        val scale = min(1.0, min(1920.0 / longSide, 1080.0 / shortSide))
        fun even(value: Int) = max(2, (value.toDouble() * scale).toInt() / 2 * 2)
        return ProxySpec(even(displayWidth), even(displayHeight), 6_000_000, 30)
    }

    /**
     * Lightweight preview proxy for Direct sources that are still heavy to decode in the
     * editor (above 1080p or > 4 Mbps). Returns null when the source is already light enough.
     * Callers store it with proxyRequired = false so export keeps the original.
     */
    fun previewProxy(info: SourceInfo): ProxySpec? {
        if (info.width <= 0 || info.height <= 0) return null
        val longSide = max(info.width, info.height)
        val shortSide = min(info.width, info.height)
        if (longSide <= 1920 && shortSide <= 1080) return null
        val rotation = ((info.rotationDegrees % 360) + 360) % 360
        val swap = rotation == 90 || rotation == 270
        val dw = if (swap) info.height else info.width
        val dh = if (swap) info.width else info.height
        val scale = min(
            1.0,
            min(1280.0 / max(dw, dh), 720.0 / min(dw, dh).toDouble().coerceAtLeast(1.0))
        )
        fun even(v: Int) = max(2, (v * scale).toInt() / 2 * 2)
        return ProxySpec(even(dw), even(dh), 2_500_000, 30)
    }
}
