package br.com.riseedit.core.timeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SegmentSourceTest {
    @Test
    fun sourceKindsHaveValueIdentityWithoutInventingStillOrGapMediaDuration() {
        assertEquals(SegmentSource.Media("video", 1000), SegmentSource.Media("video", 1000))
        assertNotEquals(SegmentSource.Media("video", 1000), SegmentSource.Media("video", 2000))
        assertEquals(SegmentSource.Still("photo"), SegmentSource.Still("photo"))
        assertNotEquals(SegmentSource.Still("photo"), SegmentSource.Gap)
    }

    @Test
    fun rejectsMissingMediaIdentityAndInvalidOriginalDuration() {
        assertThrows(IllegalArgumentException::class.java) { SegmentSource.Media("", 1000) }
        assertThrows(IllegalArgumentException::class.java) { SegmentSource.Media("video", 0) }
        assertThrows(IllegalArgumentException::class.java) { SegmentSource.Still(" ") }
    }
}
