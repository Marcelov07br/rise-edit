package br.com.riseedit.core

enum class ChromaFormat { YUV420, YUV422, YUV444, UNKNOWN }
enum class ColorDepth(val bits: Int) { EIGHT(8), TEN(10), TWELVE(12), UNKNOWN(0) }

/** Unknown extractor metadata stays explicit; numeric zero never implies an 8-bit source. */
data class SourceInfo(
    val mime: String,
    val width: Int,
    val height: Int,
    val profile: Int = 0,
    val level: Int = 0,
    val bitDepth: ColorDepth = ColorDepth.UNKNOWN,
    val chroma: ChromaFormat = ChromaFormat.UNKNOWN,
    val isHdr: Boolean = false,
    val frameRate: Float = 0f,
    val audioMime: String? = null,
    val audioChannels: Int = 0,
    val audioSampleRate: Int = 0,
    val rotationDegrees: Int = 0
)
