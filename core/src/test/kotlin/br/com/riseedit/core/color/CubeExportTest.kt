package br.com.riseedit.core.color

import br.com.riseedit.core.ColorAdjust
import br.com.riseedit.core.ColorAdjustMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CubeExportTest {
    @Test fun headerSizeAndRedFastest() {
        val text = CubeExport.write("Meu \"look\"", 3) { it }
        val lines = text.lines().filter { it.isNotBlank() && !it.startsWith("#") }
        assertEquals("TITLE \"Meu 'look'\"", lines[0])
        assertEquals("LUT_3D_SIZE 3", lines[1])
        val data = lines.drop(4)
        assertEquals(27, data.size)
        assertEquals("0.000000 0.000000 0.000000", data[0])
        assertEquals("0.500000 0.000000 0.000000", data[1]) // red changes first
        assertEquals("0.000000 0.500000 0.000000", data[3])
        assertEquals("0.000000 0.000000 0.500000", data[9])
        assertEquals("1.000000 1.000000 1.000000", data[26])
    }

    @Test fun identityRoundTripsThroughParser() {
        val text = CubeExport.write("id", 33) { it }
        val lut = CubeLut.parse(text)
        val o = lut.sample(0.3, 0.6, 0.9)
        assertEquals(0.3, o[0], 1e-4)
        assertEquals(0.6, o[1], 1e-4)
        assertEquals(0.9, o[2], 1e-4)
    }

    @Test fun gradeMatchesCpuMirror() {
        val a = ColorAdjust(exposure = 0.5f, saturation = 0.3f)
        val lut = CubeLut.parse(CubeExport.write("g", 33) { ColorAdjustMath.apply(a, it) })
        val step = 1f / 32
        val expected = ColorAdjustMath.apply(a, floatArrayOf(4 * step, 10 * step, 20 * step))
        val got = lut.sample(4.0 * step, 10.0 * step, 20.0 * step)
        for (c in 0..2) assertEquals(expected[c].toDouble(), got[c], 1e-5)
        assertTrue(CubeExport.fileName("A/b:c").endsWith(".cube"))
    }
}
