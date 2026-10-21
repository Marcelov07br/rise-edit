package br.com.riseedit.core.color

import br.com.riseedit.core.Clip
import br.com.riseedit.core.ColorAdjust
import br.com.riseedit.core.ColorCurves
import br.com.riseedit.core.CurvePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorKeyframesTest {
    private val g = ColorGrade()
    private val k = ColorKeyframes()
        .upsert(ColorKeyGroup.BASIC, 0, ColorAdjust(exposure = 0f), 1f)
        .upsert(ColorKeyGroup.BASIC, 1000, ColorAdjust(exposure = 1f), 1f)

    @Test fun endsHoldAndMidpointIsHalf() {
        assertEquals(0f, k.at(-50, ColorAdjust(), g).first.exposure, 1e-6f)
        assertEquals(1f, k.at(5000, ColorAdjust(), g).first.exposure, 1e-6f)
        assertEquals(0.5f, k.at(500, ColorAdjust(), g).first.exposure, 1e-6f)
        // smoothstep: slower than linear near the keys
        assertTrue(k.at(100, ColorAdjust(), g).first.exposure < 0.1f)
    }

    @Test fun unkeyedGroupsKeepBase() {
        val base = ColorAdjust(curves = ColorCurves(master = listOf(CurvePoint(0f, 0.1f), CurvePoint(1f, 1f))))
        assertEquals(base.curves, k.at(500, base, g).first.curves)
    }

    @Test fun curvesLerpWhenSameCountElseStep() {
        val a = ColorAdjust(curves = ColorCurves(master = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f))))
        val b = ColorAdjust(curves = ColorCurves(master = listOf(CurvePoint(0f, 0.2f), CurvePoint(1f, 1f))))
        val c = ColorAdjust(curves = ColorCurves(master = listOf(CurvePoint(0f, 0.2f), CurvePoint(0.5f, 0.6f), CurvePoint(1f, 1f))))
        val same = ColorKeyframes().upsert(ColorKeyGroup.CURVES, 0, a, 1f).upsert(ColorKeyGroup.CURVES, 1000, b, 1f)
        assertEquals(0.1f, same.at(500, ColorAdjust(), g).first.curves.master[0].y, 1e-6f)
        val diff = ColorKeyframes().upsert(ColorKeyGroup.CURVES, 0, a, 1f).upsert(ColorKeyGroup.CURVES, 1000, c, 1f)
        assertEquals(a.curves.master, diff.at(999, ColorAdjust(), g).first.curves.master)
        assertEquals(c.curves.master, diff.at(1000, ColorAdjust(), g).first.curves.master)
    }

    @Test fun lutIntensityQuantized() {
        val l = ColorKeyframes().upsert(ColorKeyGroup.LUT, 0, ColorAdjust(), 0f).upsert(ColorKeyGroup.LUT, 1000, ColorAdjust(), 1f)
        val v = l.at(500, ColorAdjust(), g).second.lutIntensity
        assertEquals(0.5f, v, 1e-4f)
        assertEquals(0f, (l.at(321, ColorAdjust(), g).second.lutIntensity / ColorKeyframes.LUT_STEP) % 1f, 1e-3f)
    }

    @Test fun upsertSameFrameReplacesAndRemove() {
        val r = k.upsert(ColorKeyGroup.BASIC, 1010, ColorAdjust(exposure = 2f), 1f)
        assertEquals(2, r.keys(ColorKeyGroup.BASIC).size)
        assertEquals(2f, r.at(2000, ColorAdjust(), g).first.exposure)
        assertTrue(r.remove(ColorKeyGroup.BASIC, 0).keys(ColorKeyGroup.BASIC).size == 1)
    }

    @Test fun jsonRoundTripAndMigration() {
        val full = k.upsert(ColorKeyGroup.LUT, 250, ColorAdjust(), 0.4f)
        assertEquals(full, ColorKeyframes.fromMap(jsonish(ColorKeyframes.toMap(full))))
        assertTrue(ColorKeyframes.fromMap(null).isEmpty) // 0.13.x projects
        assertTrue(ColorKeyframes.fromMap(mapOf("v" to 1, "tracks" to mapOf("future" to listOf<Any>()))).isEmpty)
    }

    @Test fun autoKeyAndToggle() {
        val clip = Clip(uri = "x", name = "x", sourceDurationMs = 2000, colorKeys = k)
        val edited = ColorKeyEdits.editAdjust(clip, 500, clip.colorAt(500).first.copy(exposure = 0.9f))
        assertEquals(3, edited.colorKeys.keys(ColorKeyGroup.BASIC).size)
        assertEquals(0.9f, edited.colorAt(500).first.exposure, 1e-6f)
        val t = ColorKeyEdits.toggle(clip, ColorKeyGroup.HSL, 300)
        assertTrue(t.colorKeys.hasKeyAt(ColorKeyGroup.HSL, 300))
        val off = ColorKeyEdits.toggle(ColorKeyEdits.toggle(clip, ColorKeyGroup.BASIC, 0), ColorKeyGroup.BASIC, 1000)
        assertTrue(off.colorKeys.keys(ColorKeyGroup.BASIC).isEmpty())
        assertEquals(1f, off.adjustments.exposure, 1e-6f) // last value baked into base
    }

    /** Numbers as Double/Long like org.json gives back. */
    @Suppress("UNCHECKED_CAST")
    private fun jsonish(v: Any?): Map<String, Any?> = conv(v) as Map<String, Any?>
    private fun conv(v: Any?): Any? = when (v) {
        is Map<*, *> -> v.entries.associate { it.key as String to conv(it.value) }
        is Iterable<*> -> v.map(::conv)
        is Float -> v.toDouble()
        else -> v
    }
}
