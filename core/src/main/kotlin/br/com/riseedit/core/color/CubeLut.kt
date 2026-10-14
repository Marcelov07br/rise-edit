package br.com.riseedit.core.color

/** Adobe/Resolve .cube 3D LUT (r fastest), sampled trilinearly. */
class CubeLut(val size: Int, val data: FloatArray, val title: String = "") {
    init {
        require(size in 2..256 && data.size == size * size * size * 3)
    }

    fun sample(r: Double, g: Double, b: Double): DoubleArray {
        val max = size - 1
        val fr = r.coerceIn(0.0, 1.0) * max
        val fg = g.coerceIn(0.0, 1.0) * max
        val fb = b.coerceIn(0.0, 1.0) * max
        val r0 = fr.toInt().coerceAtMost(max - 1)
        val g0 = fg.toInt().coerceAtMost(max - 1)
        val b0 = fb.toInt().coerceAtMost(max - 1)
        val dr = fr - r0
        val dg = fg - g0
        val db = fb - b0
        return DoubleArray(3) { c ->
            fun at(ri: Int, gi: Int, bi: Int) =
                data[((bi * size + gi) * size + ri) * 3 + c].toDouble()
            val c00 = at(r0, g0, b0) * (1 - dr) + at(r0 + 1, g0, b0) * dr
            val c10 = at(r0, g0 + 1, b0) * (1 - dr) + at(r0 + 1, g0 + 1, b0) * dr
            val c01 = at(r0, g0, b0 + 1) * (1 - dr) + at(r0 + 1, g0, b0 + 1) * dr
            val c11 = at(r0, g0 + 1, b0 + 1) * (1 - dr) + at(r0 + 1, g0 + 1, b0 + 1) * dr
            val c0 = c00 * (1 - dg) + c10 * dg
            val c1 = c01 * (1 - dg) + c11 * dg
            c0 * (1 - db) + c1 * db
        }
    }

    companion object {
        @Suppress("CyclomaticComplexMethod", "NestedBlockDepth")
        fun parse(text: String): CubeLut {
            var size = 0
            var title = ""
            var min = doubleArrayOf(0.0, 0.0, 0.0)
            var max = doubleArrayOf(1.0, 1.0, 1.0)
            val values = ArrayList<Float>()
            for (raw in text.lineSequence()) {
                val line = raw.substringBefore('#').trim()
                if (line.isEmpty()) continue
                val parts = line.split(Regex("\\s+"))
                when (parts[0].uppercase()) {
                    "TITLE" -> title = line.substringAfter("TITLE").trim().trim('"')
                    "LUT_3D_SIZE" -> size = parts[1].toInt()
                    "LUT_1D_SIZE" -> throw IllegalArgumentException("LUT 1D não suportado")
                    "DOMAIN_MIN" -> min = DoubleArray(3) { parts[it + 1].toDouble() }
                    "DOMAIN_MAX" -> max = DoubleArray(3) { parts[it + 1].toDouble() }
                    else -> if (parts.size >= 3 &&
                        parts[0].first().let { it.isDigit() || it == '-' || it == '.' }
                    ) {
                        for (c in 0 until 3) {
                            val v = parts[c].toDouble()
                            values += ((v - min[c]) / (max[c] - min[c])).toFloat()
                        }
                    }
                }
            }
            require(size > 0) { "LUT sem LUT_3D_SIZE" }
            require(values.size == size * size * size * 3) { "LUT incompleto" }
            return CubeLut(size, values.toFloatArray(), title)
        }
    }
}
