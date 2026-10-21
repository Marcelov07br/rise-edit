package br.com.riseedit.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorAdjustTest {
    @Test fun legacyArrayMigratesToSameValues() {
        val a = ColorAdjust.fromLegacy(listOf(0.1, 0.2, 0.3, -0.2))
        assertEquals(ColorAdjust(0.1f, 0.2f, 0.3f, -0.2f), a)
        assertEquals(0f, a.exposure); assertEquals(0f, a.vibrance)
    }

    @Test fun missingLegacyIsNeutral() {
        assertTrue(ColorAdjust.fromLegacy(null).isNeutral)
        assertTrue(ColorAdjust.fromLegacy(listOf(0.1)).isNeutral)
    }

    @Test fun versionedRoundTrip() {
        val a = ColorAdjust(0.1f, -0.2f, 0.3f, 0.4f, 1.5f, -0.3f, 0.2f, -0.6f, 0.7f)
        val m = ColorAdjust.toMap(a)
        assertEquals(ColorAdjust.VERSION, m["v"])
        val back = ColorAdjust.fromMap(m + ("futureKey" to 9.0))
        assertEquals(a.copy(), back.copy())
    }

    @Test fun neutralIsIdentity() {
        val px = floatArrayOf(0.2f, 0.5f, 0.9f)
        assertArrayEquals(px, ColorAdjustMath.apply(ColorAdjust(), px), 1e-3f)
    }

    @Test fun exposureDoublesPerStop() {
        val o = ColorAdjustMath.apply(ColorAdjust(exposure = 1f), floatArrayOf(0.2f, 0.2f, 0.2f))
        assertEquals(0.4f, o[0], 1e-3f)
    }
}

class ColorAdjustV3Test {
    @Test fun filterKeepsAdvancedFields() {
        val a = ColorAdjust(exposure = 1f, shadows = 0.4f, hsl = HslBands().with(0, 0, 0.5f))
        val out = a.withFilter(ColorAdjust(0.1f, 0.2f, 0.3f, 0.4f))
        assertEquals(1f, out.exposure); assertEquals(0.4f, out.shadows); assertEquals(0.5f, out.hsl.hue(0))
        assertEquals(0.2f, out.contrast)
        assertTrue(out.matchesFilter(ColorAdjust(0.1f, 0.2f, 0.3f, 0.4f)))
    }

    @Test fun v3RoundTripAndV2Migration() {
        val a = ColorAdjust(
            exposure = 0.5f,
            curves = ColorCurves(red = listOf(CurvePoint(0f, 0.1f), CurvePoint(0.5f, 0.4f), CurvePoint(1f, 1f))),
            hsl = HslBands().with(5, 2, -0.3f),
            wheels = ColorWheels(lift = Wheel(0.2f, -0.1f, 0.05f))
        )
        assertEquals(a, ColorAdjust.fromMap(ColorAdjust.toMap(a)))
        val v2 = mapOf<String, Any?>("v" to 2, "exposure" to 0.5, "contrast" to 0.1)
        val m = ColorAdjust.fromMap(v2)
        assertTrue(m.curves.isIdentity && m.hsl.isNeutral && m.wheels.isNeutral)
        assertEquals(0.1f, m.contrast)
    }

    @Test fun curveMonotoneAndEndpoints() {
        val c = ColorCurves(master = listOf(CurvePoint(0f, 0f), CurvePoint(0.3f, 0.5f), CurvePoint(1f, 1f)))
        var last = -1f
        for (i in 0..100) { val v = c.eval(0, i / 100f); assertTrue(v >= last - 1e-5f); last = v }
        assertEquals(0.5f, c.eval(1, 0.3f), 1e-3f)
        assertEquals(256 * 3, c.table().size)
    }

    @Test fun neutralAdvancedIsIdentity() {
        val px = floatArrayOf(0.8f, 0.3f, 0.1f)
        val a = ColorAdjust(hsl = HslBands(), wheels = ColorWheels())
        assertArrayEquals(px, ColorAdjustMath.apply(a, px), 1e-4f)
        val hsv = ColorAdjustMath.rgbToHsv(px)
        assertArrayEquals(px, ColorAdjustMath.hsvToRgb(hsv[0], hsv[1], hsv[2]), 1e-4f)
    }

    @Test fun hslBandOnlyTouchesItsHue() {
        val a = ColorAdjust(hsl = HslBands().with(0, 1, -1f)) // desaturate reds
        val red = ColorAdjustMath.apply(a, floatArrayOf(0.9f, 0.1f, 0.1f))
        val blue = ColorAdjustMath.apply(a, floatArrayOf(0.1f, 0.1f, 0.9f))
        assertEquals(red[0], red[1], 1e-3f)
        assertArrayEquals(floatArrayOf(0.1f, 0.1f, 0.9f), blue, 1e-4f)
    }
}
