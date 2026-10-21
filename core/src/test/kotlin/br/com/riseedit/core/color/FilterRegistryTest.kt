package br.com.riseedit.core.color

import br.com.riseedit.core.ColorAdjust
import org.junit.Assert.assertEquals
import org.junit.Test

class FilterRegistryTest {
    @Test fun noneFirstAndMatchesIntensity() {
        assertEquals("none", FilterRegistry.all.first().id)
        val cinema = FilterRegistry.byId("cinema")!!
        val clip = ColorAdjust(exposure = 0.5f).withFilter(cinema.at(0.5f))
        val m = FilterRegistry.match(clip)!!
        assertEquals("cinema", m.preset.id)
        assertEquals(0.5f, m.intensity, 1e-3f)
        assertEquals(0.5f, clip.exposure, 0f)
    }
}
