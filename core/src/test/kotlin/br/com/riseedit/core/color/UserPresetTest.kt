package br.com.riseedit.core.color

import br.com.riseedit.core.ColorAdjust
import br.com.riseedit.core.HslBands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserPresetTest {
    @Test fun appliesOnlySelectedGroupsAndRoundTrips() {
        val look = ColorAdjust(exposure = 0.4f, hsl = HslBands().with(2, 1, 0.5f))
        val p = UserPreset("1", "Pôr do sol", look, setOf(ColorKeyGroup.HSL))
        val applied = p.applyTo(ColorAdjust(exposure = -1f))
        assertEquals(-1f, applied.exposure)
        assertEquals(0.5f, applied.hsl.sat(2))
        val back = UserPreset.fromMap(UserPreset.toMap(p))!!
        assertEquals(setOf(ColorKeyGroup.HSL), back.groups)
        assertEquals(0f, back.adjust.exposure) // only owned groups are stored
        assertNull(UserPreset.fromMap(mapOf("id" to "x")))
    }
}
