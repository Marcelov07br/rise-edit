package br.com.riseedit.transcode

import java.nio.ByteBuffer

/** Source facts reported by FFmpeg (only the ones the proxy needs). Color codes are FFmpeg's (AVCOL_*). */
data class IngestInfo(
    val width: Int,
    val height: Int,
    val rotation: Int,
    val durationUs: Long,
    val fpsNum: Int,
    val fpsDen: Int,
    val primaries: Int,
    val transfer: Int,
    val matrix: Int,
    val range: Int,
    val bits: Int,
    val chroma: Int,
    val frames: Long
) {
    val fps: Double get() = if (fpsNum > 0 && fpsDen > 0) fpsNum.toDouble() / fpsDen else 30.0

    companion object {
        fun of(v: LongArray) = IngestInfo(
            v[0].toInt(), v[1].toInt(), v[2].toInt(), v[3], v[4].toInt(), v[5].toInt(),
            v[6].toInt(), v[7].toInt(), v[8].toInt(), v[9].toInt(), v[10].toInt(), v[11].toInt(), v[12]
        )
    }
}

/** JNI over the LGPL FFmpeg build (libavcodec h264/hevc, mov/matroska, swscale). */
object FfmpegIngest {
    /** False on ABIs without the FFmpeg build (x86_64 emulator) or if loading failed. */
    val available: Boolean by lazy {
        runCatching {
            listOf("avutil", "swscale", "avcodec", "avformat", "rise_ingest").forEach(System::loadLibrary)
        }.isSuccess
    }

    /** 0.21: coded size of the export intermediate (first video track), via MediaExtractor. */
    fun probeSize(context: android.content.Context, file: java.io.File): Pair<Int, Int> {
        val ex = android.media.MediaExtractor()
        try {
            ex.setDataSource(file.absolutePath)
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                if (f.getString(android.media.MediaFormat.KEY_MIME)?.startsWith("video/") == true) {
                    return f.getInteger(android.media.MediaFormat.KEY_WIDTH) to f.getInteger(android.media.MediaFormat.KEY_HEIGHT)
                }
            }
            error("intermediário sem vídeo")
        } finally { ex.release() }
    }

    val version: String? get() = if (available) runCatching { nativeVersion() }.getOrNull() else null

    @JvmStatic external fun nativeOpen(fd: Int, out: LongArray): Long

    @JvmStatic external fun nativeDecode(
        handle: Long,
        y: ByteBuffer, yStride: Int,
        u: ByteBuffer, uStride: Int, uPixel: Int,
        v: ByteBuffer, vStride: Int, vPixel: Int,
        /** [LAYOUT_8BIT] (I420/NV12/NV21 from the plane strides), [LAYOUT_P010] or [LAYOUT_P210]. */
        layout: Int
    ): Long

    const val LAYOUT_8BIT = 0
    const val LAYOUT_P010 = 1
    const val LAYOUT_P210 = 2

    @JvmStatic external fun nativeClose(handle: Long)

    @JvmStatic external fun nativeVersion(): String
}
