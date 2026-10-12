package br.com.riseedit.core.timeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class TrackTest {
    private val clip = Segment("clip", SegmentSource.Media("v", 10000), ConstantSpeedMap(TimeRange(2000, 6000), 2.0))

    @Test
    fun lookupUsesGlobalOffsetHalfOpenBoundariesAndExplicitGaps() {
        val track = Track("track", "Main", TrackKind.VIDEO, 1000, listOf(clip, Segment.gap("gap", 500), clip.copy(id = "next")))
        assertEquals(4500L, track.durationUs)
        assertEquals(5500L, track.endUs)
        assertEquals(3500L, track.segmentStartUs(2))
        assertNull(track.segmentAt(999))
        assertEquals(Track.Located(0, 0), track.segmentAt(1000))
        assertEquals(Track.Located(0, 1999), track.segmentAt(2999))
        assertEquals(Track.Located(1, 0), track.segmentAt(3000))
        assertEquals(Track.Located(2, 0), track.segmentAt(3500))
        assertNull(track.segmentAt(5500))
        assertEquals(4000L, track.timelineToSource(2000))
        assertEquals(2000L, track.sourceToTimeline("clip", 4000))
        assertEquals(3000L, track.sourceToTimeline("clip", Long.MAX_VALUE))
        assertNull(track.timelineToSource(3000))
        assertNull(track.sourceToTimeline("gap", 0))
        assertNull(track.sourceToTimeline("missing", 0))
    }

    @Test
    fun emptyTracksAndSnapshotsStaySafe() {
        val input = mutableListOf(clip)
        val track = Track("track", "Main", TrackKind.VIDEO, 1000, input)
        input.clear()
        assertEquals(2000L, track.durationUs)
        assertEquals(track, track.copy())
        val empty = Track("empty", "Empty", TrackKind.AUDIO, 1000, emptyList())
        assertEquals(0L, empty.durationUs)
        assertEquals(1000L, empty.endUs)
        assertNull(empty.segmentAt(1000))
        assertThrows(IllegalArgumentException::class.java) { empty.segmentStartUs(0) }
    }

    @Test
    fun rejectsInvalidStartsDuplicateIdsAndDurationOverflow() {
        assertThrows(IllegalArgumentException::class.java) { Track("t", "T", TrackKind.VIDEO, -1, listOf(clip)) }
        assertThrows(IllegalArgumentException::class.java) { Track("t", "T", TrackKind.VIDEO, 0, listOf(clip, clip)) }
        assertThrows(ArithmeticException::class.java) { Track("t", "T", TrackKind.VIDEO, Long.MAX_VALUE, listOf(clip)) }
        assertThrows(ArithmeticException::class.java) {
            Track("t", "T", TrackKind.VIDEO, 0, listOf(Segment.gap("a", Long.MAX_VALUE), Segment.gap("b", 1)))
        }
    }
}
