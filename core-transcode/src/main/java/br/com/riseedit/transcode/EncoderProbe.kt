package br.com.riseedit.transcode

import android.media.MediaCodecInfo
import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import br.com.riseedit.core.EncoderSupport

/**
 * 0.21: probes MediaCodecList for the export options. APV constants are API 36 (Android 16) and
 * compileSdk is 35, so they are literals here (values from developer.android.com):
 * MIMETYPE_VIDEO_APV = "video/apv", APVProfile422_10 = 1, COLOR_FormatYUVP210 = 60.
 */
object EncoderProbe {
    const val MIME_APV = "video/apv"
    const val APV_PROFILE_422_10 = 1
    const val COLOR_YUVP210 = 60
    const val COLOR_YUVP010 = 54
    const val AVC_HIGH_422 = CodecProfileLevel.AVCProfileHigh422

    enum class Kind { APV, HEVC422, AVC422 }

    private fun encoders() = runCatching {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { it.isEncoder }
    }.getOrDefault(emptyList())

    private fun hw(c: MediaCodecInfo) = Build.VERSION.SDK_INT < 29 || c.isHardwareAccelerated

    private fun find(mime: String, color: Int?, profile: Int?, hardware: Boolean, w: Int = 0, h: Int = 0) =
        encoders().firstOrNull { c ->
            c.supportedTypes.any { it.equals(mime, true) } && (!hardware || hw(c)) && runCatching {
                val caps = c.getCapabilitiesForType(mime)
                (color == null || color in caps.colorFormats) &&
                    (profile == null || caps.profileLevels.any { it.profile == profile }) &&
                    (w == 0 || caps.videoCapabilities.isSizeSupported(w, h))
            }.getOrDefault(false)
        }

    fun probe(): EncoderSupport {
        val p210 = Build.VERSION.SDK_INT >= 33
        return EncoderSupport(
            h264 = find(MediaFormat.MIMETYPE_VIDEO_AVC, null, null, false) != null,
            hevc = find(MediaFormat.MIMETYPE_VIDEO_HEVC, null, null, false) != null,
            hevc10 = Build.VERSION.SDK_INT >= 33 &&
                find(MediaFormat.MIMETYPE_VIDEO_HEVC, null, CodecProfileLevel.HEVCProfileMain10, true) != null,
            hevc422 = p210 && find(MediaFormat.MIMETYPE_VIDEO_HEVC, COLOR_YUVP210, null, true) != null,
            avc422 = p210 && find(MediaFormat.MIMETYPE_VIDEO_AVC, COLOR_YUVP210, AVC_HIGH_422, true) != null,
            apv = Build.VERSION.SDK_INT >= 36 && find(MIME_APV, COLOR_YUVP210, APV_PROFILE_422_10, true) != null,
            prores = FfmpegIngest.available
        )
    }

    /** Hardware 4:2:2 target for the finishing pass, or null if the phone lost it. */
    fun target(kind: Kind, w: Int, h: Int, bitrate: Int): ProxyConverter.Target? {
        val (mime, profile) = when (kind) {
            Kind.APV -> MIME_APV to APV_PROFILE_422_10
            Kind.HEVC422 -> MediaFormat.MIMETYPE_VIDEO_HEVC to null
            Kind.AVC422 -> MediaFormat.MIMETYPE_VIDEO_AVC to AVC_HIGH_422
        }
        val c = find(mime, COLOR_YUVP210, profile, true, w, h) ?: return null
        return ProxyConverter.Target(c.name, mime, profile, COLOR_YUVP210, FfmpegIngest.LAYOUT_P210, bitrate)
    }
}
