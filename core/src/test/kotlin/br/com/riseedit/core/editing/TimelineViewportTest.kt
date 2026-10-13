package br.com.riseedit.core.editing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineViewportTest {
    private fun viewport() = TimelineViewport(200.0, 500_000.0, 800.0, 20.0, 4000.0, 24.0)

    @Test
    fun pinchPreservesTimeUnderTheFingers() {
        val before = viewport()
        val anchor = before.timeAtPx(320.0)
        val after = before.zoomed(2.0, 320.0, 20_000_000)
        assertEquals(400.0, after.pixelsPerSecond, 0.001)
        assertEquals(anchor, after.timeAtPx(320.0), 0.001)
        assertEquals(320.0, after.pxAtTime(anchor), 0.001)
    }

    @Test
    fun panClampsAtBothEndsAndLeavesRoomForHandles() {
        val left = viewport().panned(10_000.0, 20_000_000)
        assertEquals(24.0, left.pxAtTime(0.0), 0.001)
        val right = viewport().panned(-10_000.0, 20_000_000)
        assertEquals(776.0, right.pxAtTime(20_000_000.0), 0.001)
    }

    @Test
    fun shortProjectRemainsReachableAfterResizeAndZoomOut() {
        val after = viewport().resized(1200.0, 1_000_000).zoomed(0.1, 600.0, 1_000_000)
        assertTrue(after.pxAtTime(0.0) >= 0)
        assertTrue(after.pxAtTime(1_000_000.0) < after.widthPx)
    }
}
