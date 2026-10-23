package br.com.riseedit.transcode

import android.media.MediaFormat

/** Pure decisions of the 10-bit/4:2:2 conversion (unit-tested on the host). */
object ProxyPlan {
    /** "Alta" bitrate: 0.3 bit/pixel/frame, 10–120 Mb/s; 10-bit HEVC gets the same budget. */
    fun bitrate(width: Int, height: Int, fps: Double): Int =
        (width.toLong() * height * fps.coerceIn(1.0, 120.0) * 0.3).toLong()
            .coerceIn(10_000_000L, 120_000_000L).toInt()

    /** FFmpeg AVCOL_PRI/AVCOL_SPC → MediaFormat.COLOR_STANDARD_*. */
    fun standard(primaries: Int, matrix: Int): Int = when {
        primaries == 9 || matrix == 9 || matrix == 10 -> MediaFormat.COLOR_STANDARD_BT2020
        primaries == 5 || primaries == 6 -> MediaFormat.COLOR_STANDARD_BT601_PAL
        else -> MediaFormat.COLOR_STANDARD_BT709
    }

    /** FFmpeg AVCOL_TRC → MediaFormat.COLOR_TRANSFER_*; HLG (18) and PQ (16) are preserved. */
    fun transfer(trc: Int): Int = when (trc) {
        16 -> MediaFormat.COLOR_TRANSFER_ST2084
        18 -> MediaFormat.COLOR_TRANSFER_HLG
        8 -> MediaFormat.COLOR_TRANSFER_LINEAR
        else -> MediaFormat.COLOR_TRANSFER_SDR_VIDEO
    }

    /** AVCOL_RANGE_JPEG (2) is full range; everything else is limited. */
    fun range(r: Int): Int = if (r == 2) MediaFormat.COLOR_RANGE_FULL else MediaFormat.COLOR_RANGE_LIMITED

    fun isHdr(trc: Int) = trc == 16 || trc == 18

    /** Output file name derived from the source identity (same file → same proxy). */
    fun name(uri: String, size: Long, modified: Long) =
        "conv-${(uri + size + modified).hashCode().toUInt()}.mp4"

    fun progress(ptsUs: Long, durationUs: Long): Float =
        if (durationUs <= 0) 0f else (ptsUs.toFloat() / durationUs).coerceIn(0f, 1f)
}
