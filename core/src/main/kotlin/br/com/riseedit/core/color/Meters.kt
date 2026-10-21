package br.com.riseedit.core.color

/** Exposure meters over ARGB pixels (Rec.709 luma). */
object Meters {
    fun luma(argb: Int): Int {
        val r = argb shr 16 and 0xFF
        val g = argb shr 8 and 0xFF
        val b = argb and 0xFF
        return ((2126 * r + 7152 * g + 722 * b) / 10000).coerceIn(0, 255)
    }

    /** 256-bin luma histogram. */
    fun histogram(pixels: IntArray): IntArray {
        val bins = IntArray(256)
        for (p in pixels) bins[luma(p)]++
        return bins
    }

    /** Luma waveform: [columns] × 64 levels, counts per (column, level), row 0 = black. */
    fun waveform(pixels: IntArray, width: Int, columns: Int, levels: Int = 64): Array<IntArray> {
        val out = Array(columns) { IntArray(levels) }
        if (width <= 0) return out
        for (i in pixels.indices) {
            val col = (i % width) * columns / width
            out[col][luma(pixels[i]) * levels / 256]++
        }
        return out
    }

    /** Zebra: true where luma is at or above [threshold] (0..255, default ~95 IRE). */
    fun zebra(pixels: IntArray, threshold: Int = 242): BooleanArray =
        BooleanArray(pixels.size) { luma(pixels[it]) >= threshold }

    /** Share of clipped pixels (0..1), handy for a one-line verdict. */
    fun clipped(pixels: IntArray, threshold: Int = 250): Double = if (pixels.isEmpty()) {
        0.0
    } else {
        pixels.count { luma(it) >= threshold }.toDouble() /
            pixels.size
    }

    /** RGB parade: 3 × [columns] × [levels] counts (r, g, b), row 0 = black. */
    fun parade(pixels: IntArray, width: Int, columns: Int, levels: Int = 64): Array<Array<IntArray>> {
        val out = Array(3) { Array(columns) { IntArray(levels) } }
        if (width <= 0) return out
        for (i in pixels.indices) {
            val col = (i % width) * columns / width
            val p = pixels[i]
            out[0][col][(p shr 16 and 0xFF) * levels / 256]++
            out[1][col][(p shr 8 and 0xFF) * levels / 256]++
            out[2][col][(p and 0xFF) * levels / 256]++
        }
        return out
    }

    /** BT.709 chroma (Cb, Cr), each about −0.5..0.5. */
    fun chroma(r: Float, g: Float, b: Float): Pair<Float, Float> {
        val y = 0.2126f * r + 0.7152f * g + 0.0722f * b
        return (b - y) / 1.8556f to (r - y) / 1.5748f
    }

    /** Vectorscope: [size]² bins, x = Cb, y = Cr (row 0 = +Cr, top), center = neutral. */
    fun vectorscope(pixels: IntArray, size: Int = 96): IntArray {
        val out = IntArray(size * size)
        for (p in pixels) {
            val (cb, cr) = chroma((p shr 16 and 0xFF) / 255f, (p shr 8 and 0xFF) / 255f, (p and 0xFF) / 255f)
            val x = ((cb + 0.5f) * (size - 1)).toInt().coerceIn(0, size - 1)
            val y = ((0.5f - cr) * (size - 1)).toInt().coerceIn(0, size - 1)
            out[y * size + x]++
        }
        return out
    }

    /**
     * Skin-tone ("I") line angle on the vectorscope in radians, CCW from +Cb. Derived from a
     * reference skin rgb rather than hard-coded; ≈123° like broadcast scopes.
     */
    val SKIN_LINE_RAD: Double = chroma(0.80f, 0.58f, 0.46f).let { (cb, cr) -> kotlin.math.atan2(cr.toDouble(), cb.toDouble()) }

    /** Per-channel 256-bin histograms (r, g, b) + luma. */
    fun histogramRgb(pixels: IntArray): Array<IntArray> {
        val out = Array(4) { IntArray(256) }
        for (p in pixels) {
            out[0][p shr 16 and 0xFF]++; out[1][p shr 8 and 0xFF]++; out[2][p and 0xFF]++; out[3][luma(p)]++
        }
        return out
    }
}
