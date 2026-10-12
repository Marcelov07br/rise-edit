package br.com.riseedit.core.timeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class TimeMapTest {
    @Test
    fun constantSpeedDurationClampAndRoundTrip() {
        val map = ConstantSpeedMap(TimeRange.fromMs(2000, 6000), 2.0)
        assertEquals(2_000_000L, map.targetRange.durationUs)
        assertEquals(0L, map.sourceToTarget(-1))
        assertEquals(2_000_000L, map.sourceToTarget(Long.MAX_VALUE))
        assertEquals(2_000_000L, map.targetToSource(-1))
        assertEquals(6_000_000L, map.targetToSource(Long.MAX_VALUE))
        for (source in 2_000_000L..2_020_000L) {
            assertTrue(abs(source - map.targetToSource(map.sourceToTarget(source))) <= 1)
        }
        assertEquals(1L, map.sourceToTarget(2_000_001))
        assertEquals(2.0, map.speedAt(-1), 0.0)
    }

    @Test
    fun handlesQuantizationLargeTimesAndInvalidDurationsExplicitly() {
        val fast = ConstantSpeedMap(TimeRange(0, 4000), 4.0)
        assertEquals(1L, fast.sourceToTarget(2))
        assertEquals(4L, fast.targetToSource(1))
        val large = ConstantSpeedMap(TimeRange(0, Long.MAX_VALUE), 1.0)
        val time = Long.MAX_VALUE - 7
        assertEquals(time, large.targetToSource(large.sourceToTarget(time)))
        listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach {
            assertThrows(IllegalArgumentException::class.java) { ConstantSpeedMap(TimeRange(0, 100), it) }
        }
        assertThrows(IllegalArgumentException::class.java) { ConstantSpeedMap(TimeRange(0, 1), 4.0) }
        assertThrows(ArithmeticException::class.java) { ConstantSpeedMap(TimeRange(0, Long.MAX_VALUE), 0.5) }
    }

    @Test
    fun piecewiseRampRetainsAbsoluteSourceBoundariesAndChoosesRightHandSpeed() {
        val map = PiecewiseMap(listOf(
            ConstantSpeedMap(TimeRange.fromMs(2000, 4000), 0.5),
            ConstantSpeedMap(TimeRange.fromMs(4000, 6000), 2.0),
            ConstantSpeedMap(TimeRange.fromMs(6000, 8000), 1.0),
        ))
        assertEquals(7_000_000L, map.targetRange.durationUs)
        assertEquals(4_000_000L, map.targetToSource(4_000_000))
        assertEquals(5_000_000L, map.targetToSource(4_500_000))
        assertEquals(2.0, map.speedAt(4_000_000), 0.0)
        assertEquals(2_000_000L, map.targetToSource(-1))
        assertEquals(8_000_000L, map.targetToSource(Long.MAX_VALUE))
        for (source in 2_000_000L..8_000_000L step 1999) {
            assertTrue(abs(source - map.targetToSource(map.sourceToTarget(source))) <= 1)
        }
    }

    @Test
    fun rejectsBadPiecesAndSnapshotsMutableInputs() {
        val piece = ConstantSpeedMap(TimeRange(0, 10), 1.0)
        assertThrows(IllegalArgumentException::class.java) { PiecewiseMap(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) {
            PiecewiseMap(listOf(piece, ConstantSpeedMap(TimeRange(11, 20), 1.0)))
        }
        val input = mutableListOf<TimeMap>(piece)
        val map = PiecewiseMap(input)
        input.clear()
        assertEquals(10L, map.targetRange.durationUs)
        assertEquals(listOf(piece), map.pieces)
        assertEquals(map, map.copy())
    }
}
