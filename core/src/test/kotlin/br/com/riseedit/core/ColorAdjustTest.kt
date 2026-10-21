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
