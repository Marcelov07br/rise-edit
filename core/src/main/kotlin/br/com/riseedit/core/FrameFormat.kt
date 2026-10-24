package br.com.riseedit.core

/** 0.24.0: what fills the bars when the video is fitted (letterbox) into the project ratio. */
enum class BackgroundKind(val label: String) {
    BLACK("Preto"),
    COLOR("Cor"),
    BLUR("Desfoque do vídeo")
}

data class FrameBackground(
    val kind: BackgroundKind = BackgroundKind.BLACK,
    /** ARGB, used by [BackgroundKind.COLOR]. */
    val color: Int = 0xFF000000.toInt(),
    /** 0..1 blur radius (fraction of the max), used by [BackgroundKind.BLUR]. */
    val blur: Float = 0.6f,
    /** 0..1 darkening of the blurred copy so the foreground reads. */
    val dim: Float = 0.25f
) {
    val isBlack get() = kind == BackgroundKind.BLACK
}

/** Ratio cards of the Formato zone (0.24.0). ratio 0 = original; [FREE] = any custom value. */
enum class FormatRatio(val label: String, val ratio: Float) {
    ORIGINAL("Original", 0f),
    VERTICAL("9:16", 9f / 16f),
    SQUARE("1:1", 1f),
    PORTRAIT("4:5", 4f / 5f),
    WIDE("16:9", 16f / 9f),
    CINEMA("21:9", 21f / 9f),
    FREE("Livre", -1f);

    companion object {
        val FREE_RANGE = 0.4f..2.5f
        fun of(ratio: Float): FormatRatio =
            entries.firstOrNull { it.ratio >= 0f && kotlin.math.abs(it.ratio - ratio) < 0.01f } ?: FREE
    }
}

object FrameFormat {
    /**
     * Output canvas that contains an [inW]×[inH] frame at [ratio] (letterbox/pillarbox):
     * one side kept, the other grown. Even dimensions (encoders).
     */
    fun fitCanvas(inW: Int, inH: Int, ratio: Float): Pair<Int, Int> {
        if (ratio <= 0f || inW <= 0 || inH <= 0) return inW to inH
        val source = inW.toFloat() / inH
        val (w, h) = if (source > ratio) inW to (inW / ratio).toInt() else (inH * ratio).toInt() to inH
        return (w + 1) / 2 * 2 to (h + 1) / 2 * 2
    }

    /**
     * Content rect of the fitted frame inside the canvas, in 0..1 uv: (left, bottom, width, height).
     */
    fun contentRect(inW: Int, inH: Int, outW: Int, outH: Int): FloatArray {
        val w = inW.toFloat() / outW
        val h = inH.toFloat() / outH
        return floatArrayOf((1 - w) / 2, (1 - h) / 2, w, h)
    }

    /**
     * NDC x translation (ClipTransform convention: 1 = half the source width) that centers the
     * source position [centerX] (0..1) in a FILL crop of a [sourceRatio] frame to [outRatio].
     * Clamped so the crop never leaves the frame (no black bars).
     */
    fun reframeX(sourceRatio: Float, outRatio: Float, centerX: Float): Float {
        if (sourceRatio <= outRatio || outRatio <= 0f) return 0f
        val visible = outRatio / sourceRatio // visible fraction of the source width
        val c = centerX.coerceIn(visible / 2, 1 - visible / 2)
        return (0.5f - c) * 2f
    }
}
