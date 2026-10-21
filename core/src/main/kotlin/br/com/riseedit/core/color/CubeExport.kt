package br.com.riseedit.core.color

import java.util.Locale

/**
 * Writes the current grade as a standard Adobe/Resolve .cube: TITLE, LUT_3D_SIZE, then size³
 * "r g b" lines with RED changing fastest, then green, then blue. [transform] maps an input rgb
 * (0..1) to the graded output; the app passes the CPU mirror of the shader (adjust + grade).
 */
object CubeExport {
    val SIZES = listOf(33, 65)

    fun write(title: String, size: Int, transform: (FloatArray) -> FloatArray): String {
        require(size in 2..256)
        val sb = StringBuilder(size * size * size * 26 + 128)
        sb.append("# Rise Edit\n")
        sb.append("TITLE \"").append(title.replace("\"", "'")).append("\"\n")
        sb.append("LUT_3D_SIZE ").append(size).append('\n')
        sb.append("DOMAIN_MIN 0.0 0.0 0.0\nDOMAIN_MAX 1.0 1.0 1.0\n")
        val step = 1f / (size - 1)
        val rgb = FloatArray(3)
        for (b in 0 until size) {
            for (g in 0 until size) {
                for (r in 0 until size) {
                    rgb[0] = r * step
                    rgb[1] = g * step
                    rgb[2] = b * step
                    val o = transform(rgb.copyOf())
                    sb.append(
                        String.format(
                            Locale.US,
                            "%.6f %.6f %.6f\n",
                            o[0].coerceIn(0f, 1f),
                            o[1].coerceIn(0f, 1f),
                            o[2].coerceIn(0f, 1f)
                        )
                    )
                }
            }
        }
        return sb.toString()
    }

    /** Safe file name for SAF's CREATE_DOCUMENT suggestion. */
    fun fileName(title: String) =
        title.trim().ifBlank { "grade" }.replace(Regex("[^A-Za-z0-9._ -]"), "_") + ".cube"
}
