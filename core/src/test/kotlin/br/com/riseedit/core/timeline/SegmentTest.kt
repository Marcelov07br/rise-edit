package br.com.riseedit.core.timeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SegmentTest {
    @Test
    fun mediaAndStillDurationsComeFromTheTimeMap() {
        val map = ConstantSpeedMap(TimeRange(2000, 6000), 2.0)
        assertEquals(2000L, Segment("video", SegmentSource.Media("v", 10000), map).durationUs)
        assertEquals(2000L, Segment("photo", SegmentSource.Still("p"), map).durationUs)
        assertEquals(3000L, Segment.gap("space", 3000).durationUs)
    }

    @Test
    fun rejectsInvalidTrimEmptySegmentAndRetimedGap() {
        val map = ConstantSpeedMap(TimeRange(0, 6000), 2.0)
        assertThrows(IllegalArgumentException::class.java) {
            Segment("video", SegmentSource.Media("v", 5000), map)
        }
        assertThrows(IllegalArgumentException::class.java) { Segment("", SegmentSource.Still("p"), map) }
        assertThrows(IllegalArgumentException::class.java) { Segment("gap", SegmentSource.Gap, map) }
        assertThrows(IllegalArgumentException::class.java) { Segment.gap("gap", 0) }
    }
}
