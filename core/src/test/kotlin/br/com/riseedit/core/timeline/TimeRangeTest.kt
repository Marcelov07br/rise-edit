package br.com.riseedit.core.timeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeRangeTest {
    @Test
    fun rejectsNegativeEmptyReversedAndOverflowingRanges() {
        listOf(-1L to 5L, 5L to 5L, 5L to 4L).forEach { (start, end) ->
            assertThrows(IllegalArgumentException::class.java) { TimeRange(start, end) }
        }
        assertThrows(ArithmeticException::class.java) { TimeRange.fromMs(0, Long.MAX_VALUE) }
        assertThrows(ArithmeticException::class.java) { TimeRange(1, Long.MAX_VALUE).shifted(1) }
        assertThrows(IllegalArgumentException::class.java) { TimeRange(1, 4).shifted(-2) }
    }

    @Test
    fun touchingRangesDoNotOverlapAndFinalBoundaryIsExcluded() {
        val range = TimeRange(10, 20)
        assertTrue(10L in range)
        assertFalse(20L in range)
        assertFalse(range.overlaps(TimeRange(20, 30)))
        assertNull(range.intersect(TimeRange(0, 10)))
        assertTrue(range.overlaps(TimeRange(19, 30)))
        assertEquals(TimeRange(19, 20), range.intersect(TimeRange(19, 30)))
    }

    @Test
    fun convertsShiftsAndSortsWithoutLosingPrecision() {
        val range = TimeRange.fromMs(2, 7)
        assertEquals(5000L, range.durationUs)
        assertEquals(TimeRange(1000, 6000), range.shifted(-1000))
        assertEquals(
            listOf(TimeRange(0, 5), TimeRange(0, 8), TimeRange(1, 4)),
            listOf(TimeRange(1, 4), TimeRange(0, 8), TimeRange(0, 5)).sorted()
        )
        val large = TimeRange(Long.MAX_VALUE - 3, Long.MAX_VALUE)
        assertEquals(large, large.intersect(large))
    }
}
