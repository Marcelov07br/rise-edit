package br.com.riseedit.core.color

import br.com.riseedit.core.ColorAdjust
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorMatchTest {
    /** Synthetic "scene": gradient + colored patches. */
    private fun scene(seed: Int = 1): FloatArray {
        val rnd = Random(seed)
        val n = 1600
        return FloatArray(n * 3) { i ->
            val p = i / 3
            val c = i % 3
            val base = (p % 40) / 40f * 0.8f + 0.1f
            (
                base + (
                    if (c ==
                        p / 400 % 3
                    ) {
                        0.12f
                    } else {
                        -0.04f
                    }
                    ) + rnd.nextFloat() * 0.03f
                ).coerceIn(0f, 1f)
        }
    }

    @Test fun matchesAKnownGrade() {
        val raw = scene()
        val look = ColorAdjust(exposure = 0.5f, warmth = 0.4f, contrast = 0.15f, saturation = 0.2f)
        val ref = ColorMatch.statsAfter(look, raw)
        val before = ColorMatch.distance(ColorMatch.statsAfter(ColorAdjust.NEUTRAL, raw), ref)
        val solved = ColorMatch.solve(raw, ColorAdjust.NEUTRAL, ref)
        val after = ColorMatch.distance(ColorMatch.statsAfter(solved, raw), ref)
        assertTrue("after=$after before=$before", after < before * 0.15f)
        assertTrue(solved.exposure > 0.2f && solved.warmth > 0.15f)
    }

    @Test fun keepsNonBasicGroupsAndIntensityBlends() {
        val raw = scene(2)
        val base = ColorAdjust(vignette = Vignette(-0.3f), curves = ColorAdjust().curves)
        val ref = ColorMatch.statsAfter(ColorAdjust(exposure = -0.6f), raw)
        val solved = ColorMatch.solve(raw, base, ref)
        assertEquals(base.vignette, solved.vignette)
        val half = ColorMatch.blend(base, solved, 0.5f)
        assertEquals(solved.exposure / 2f, half.exposure, 1e-4f)
        assertEquals(base, ColorMatch.blend(base, solved, 0f))
    }

    @Test fun statsOfGrayHaveNoChroma() {
        val s = FrameStats.of(FloatArray(300) { 0.5f })
        assertEquals(0.5f, s.meanY, 1e-4f)
        assertEquals(0f, s.chroma, 1e-4f)
        assertEquals(0f, s.stdY, 1e-4f)
    }
}
