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
}
