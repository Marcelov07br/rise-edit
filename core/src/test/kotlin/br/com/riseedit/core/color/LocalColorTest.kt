package br.com.riseedit.core.color

import br.com.riseedit.core.Clip
import br.com.riseedit.core.ColorAdjust
import br.com.riseedit.core.ColorAdjustMath
import br.com.riseedit.core.editing.AttributeGroup
import br.com.riseedit.core.editing.ClipAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalColorTest {
    private val red = floatArrayOf(0.8f, 0.15f, 0.1f)
    private val blue = floatArrayOf(0.1f, 0.2f, 0.8f)

    @Test fun qualifierOnlyTouchesPickedColor() {
        val q = Qualifier.pick(Qualifier(), red).copy(saturation = -1f)
        assertTrue(LocalColorMath.mask(q, red) > 0.95f)
        assertEquals(0f, LocalColorMath.mask(q, blue), 1e-4f)
        val a = ColorAdjust(qualifier = q)
        val outRed = ColorAdjustMath.apply(a, red)
        assertEquals(outRed[0], outRed[1], 0.02f) // desaturated
        val outBlue = ColorAdjustMath.apply(a, blue)
        for (i in 0..2) assertEquals(blue[i], outBlue[i], 1e-4f)
    }

    @Test fun pickedButUnadjustedIsNeutral() {
        val a = ColorAdjust(qualifier = Qualifier.pick(Qualifier(), red))
        assertTrue(a.isNeutral)
        assertTrue(!ColorAdjust(vignette = Vignette(amount = -0.5f)).isNeutral)
    }

    @Test fun vignetteDarkensCornersNotCenter() {
        val v = Vignette(amount = -1f, size = 0.3f, softness = 0.4f)
        val c = floatArrayOf(0.5f, 0.5f, 0.5f)
        assertEquals(0.5f, LocalColorMath.vignette(v, c, 0.5f, 0.5f, 16f / 9f)[0], 1e-4f)
        assertTrue(LocalColorMath.vignette(v, c, 0f, 0f, 16f / 9f)[0] < 0.1f)
        // moved center follows
        val moved = v.copy(cx = 0.1f, cy = 0.1f)
        assertEquals(0.5f, LocalColorMath.vignette(moved, c, 0.1f, 0.1f, 1f)[0], 1e-4f)
    }

    @Test fun jsonV4RoundTripAndV3Migration() {
        val a = ColorAdjust(exposure = 0.3f, qualifier = Qualifier.pick(Qualifier(), red).copy(hueShift = 0.2f), vignette = Vignette(-0.4f, cx = 0.3f))
        assertEquals(a.qualifier, ColorAdjust.fromMap(ColorAdjust.toMap(a)).qualifier)
        assertEquals(a.vignette, ColorAdjust.fromMap(ColorAdjust.toMap(a)).vignette)
        val v3 = ColorAdjust.toMap(a).filterKeys { it != "qualifier" && it != "vignette" } + ("v" to 3)
        val old = ColorAdjust.fromMap(v3)
        assertEquals(Qualifier(), old.qualifier)
        assertEquals(Vignette(), old.vignette)
        assertEquals(0.3f, old.exposure)
    }

    @Test fun keyframesAnimateVignetteAndQualifier() {
        val k = ColorKeyframes()
            .upsert(ColorKeyGroup.VIGNETTE, 0, ColorAdjust(vignette = Vignette(0f)), 1f)
            .upsert(ColorKeyGroup.VIGNETTE, 1000, ColorAdjust(vignette = Vignette(-1f)), 1f)
            .upsert(ColorKeyGroup.QUALIFIER, 0, ColorAdjust(qualifier = Qualifier(true, hue = 350f)), 1f)
            .upsert(ColorKeyGroup.QUALIFIER, 1000, ColorAdjust(qualifier = Qualifier(true, hue = 10f)), 1f)
        val (mid, _) = k.at(500, ColorAdjust.NEUTRAL, ColorGrade())
        assertEquals(-0.5f, mid.vignette.amount, 1e-4f)
        assertEquals(0f, mid.qualifier.hue % 360f, 0.01f) // short way around
        assertEquals(k, ColorKeyframes.fromMap(ColorKeyframes.toMap(k)))
    }

    @Test fun copyGroupsAreIndependent() {
        val src = Clip(uri = "a", name = "a", sourceDurationMs = 1000, adjustments = ColorAdjust(exposure = 1f, vignette = Vignette(-0.5f), qualifier = Qualifier(true, hue = 120f, saturation = 0.5f)))
        val dst = Clip(uri = "b", name = "b", sourceDurationMs = 1000, adjustments = ColorAdjust(contrast = 0.2f))
        val attrs = ClipAttributes.of(src)
        assertTrue(AttributeGroup.COLOR_VIGNETTE in attrs.available && AttributeGroup.COLOR_QUALIFIER in attrs.available)
        val out = attrs.applyTo(dst, setOf(AttributeGroup.COLOR_VIGNETTE))
        assertEquals(-0.5f, out.adjustments.vignette.amount)
        assertEquals(0.2f, out.adjustments.contrast)
        assertEquals(Qualifier(), out.adjustments.qualifier)
        // pasting basic keeps the target's vignette
        val basic = ClipAttributes.of(dst.copy(adjustments = ColorAdjust(exposure = 0.5f))).applyTo(out, setOf(AttributeGroup.COLOR_BASIC))
        assertEquals(-0.5f, basic.adjustments.vignette.amount)
    }
}
