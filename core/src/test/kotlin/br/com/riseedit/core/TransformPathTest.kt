package br.com.riseedit.core

import br.com.riseedit.core.editing.FrameSnap
import br.com.riseedit.core.editing.Snapping
import br.com.riseedit.core.editing.TimelineViewport
import br.com.riseedit.core.editing.TimelineZoom
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransformPathTest {
    private val path =
        listOf(
            TransformKeyframe(0, x = 0f, y = 0f),
            TransformKeyframe(1000, x = 1f, y = 1f),
            TransformKeyframe(2000, x = 2f, y = 0f)
        )

    @Test fun passesThroughKeyframesAndClamps() {
        path.forEach { assertEquals(it.x, TransformPath.at(path, it.sourceMs).x, 1e-5f) }
        assertEquals(0f, TransformPath.at(path, -50).x, 0f)
        assertEquals(2f, TransformPath.at(path, 5000).x, 0f)
    }

    @Test fun velocityIsContinuousThroughInteriorKey() {
        fun vx(t: Long) = (TransformPath.at(path, t + 1).x - TransformPath.at(path, t - 1).x) / 2
        assertEquals(vx(999), vx(1001), 1e-4f)
        assertTrue("moves through the key, not stopping", vx(1000) > 0.0005f)
    }

    @Test fun easesInAndOutAtPathEnds() {
        val two = listOf(TransformKeyframe(0, x = 0f), TransformKeyframe(1000, x = 1f))
        assertTrue(TransformPath.at(two, 100).x < 0.1f) // slower than linear at start
        assertEquals(0.5f, TransformPath.at(two, 500).x, 1e-5f)
        assertTrue(TransformPath.at(two, 900).x > 0.9f)
    }

    @Test fun curvedPathIsNotPiecewiseLinear() {
        val y = TransformPath.at(path, 1250).y
        assertTrue("catmull-rom rounds the peak above the chord (0.75), y=$y", y > 0.8f)
    }

    @Test fun rotationTakesShortestPath() {
        val keys =
            listOf(TransformKeyframe(0, rotation = 170f), TransformKeyframe(1000, rotation = -170f))
        val mid = TransformPath.at(keys, 500).rotation
        assertEquals(180f, mid, 1e-3f)
        assertEquals(20f, TransformPath.shortestDelta(170f, -170f), 1e-4f)
        assertEquals(-20f, TransformPath.shortestDelta(-170f, 170f), 1e-4f)
    }

    @Test fun scaleIsEased() {
        val keys = listOf(TransformKeyframe(0, scale = 1f), TransformKeyframe(1000, scale = 2f))
        assertTrue(TransformPath.at(keys, 100).scale < 1.1f)
        assertEquals(1.5f, TransformPath.at(keys, 500).scale, 1e-5f)
    }

    @Test fun upsertToggleAndOverlayBase() {
        val keys = TransformPath.upsert(path, TransformKeyframe(1020, x = 9f))
        assertEquals(3, keys.size)
        assertEquals(9f, keys[1].x)
        assertEquals(1000L, keys[1].sourceMs)
        val removed = TransformEdits.toggle(keys, 1000, TransformKeyframe(0))
        assertEquals(2, removed.size)
        val added = TransformEdits.toggle(removed, 1500, TransformKeyframe(0, x = 4f))
        assertEquals(3, added.size)
        val overlay =
            VideoOverlay(clip = Clip(uri = "a", name = "a", sourceDurationMs = 1000), x = 0.2f)
        assertEquals(0.2f, overlay.transformAt(300).x)
        val moved = TransformEdits.overlay(
            overlay,
            300,
            TransformKeyframe(0, x = -0.5f, scale = 0.6f, rotation = 15f)
        )
        assertEquals(-0.5f, moved.x)
        assertEquals(15f, moved.rotation)
        assertTrue(moved.keyframes.isEmpty())
    }

    @Test fun clipEditWithoutKeysStaysStatic() {
        val clip = Clip(uri = "a", name = "a", sourceDurationMs = 5000)
        val edited = TransformEdits.clip(clip, 2000, TransformKeyframe(0, x = 0.3f))
        assertEquals(0.3f, Timeline.keyframe(edited, 0).x)
        assertEquals(0.3f, Timeline.keyframe(edited, 4000).x)
    }

    @Test fun snappingPicksNearestWithinThreshold() {
        assertEquals(1000.0, Snapping.snap(1040.0, listOf(0.0, 1000.0), 50.0).value, 0.0)
        assertNull(Snapping.snap(1100.0, listOf(0.0, 1000.0), 50.0).target)
        val span = Snapping.snapSpan(470.0, 500.0, listOf(1000.0), 50.0)
        assertEquals(500.0, span.value, 0.0)
        assertEquals(FrameSnap.Guide.CENTER, FrameSnap.axis(0.02, 0.3).guide)
        assertEquals(0.7, FrameSnap.axis(0.69, 0.3).center, 1e-9)
        assertEquals(90f to true, FrameSnap.angle(87f))
        assertEquals(false, FrameSnap.angle(80f).second)
    }

    @Test fun zoomOutFitsShortProjectsAndCapsAtTenMinutes() {
        val viewport = TimelineViewport(55.0, 0.0, 1000.0, 20.0, 4000.0, 24.0)
        val fit = viewport.zoomed(0.0001, 0.0, 20_000_000)
        val visibleUs = fit.timeAtPx(fit.widthPx - 2 * fit.edgeInsetPx) - fit.timeAtPx(0.0)
        assertEquals(20_000_000.0, visibleUs, 1.0)
        val long = viewport.zoomed(0.0001, 0.0, 3_600_000_000)
        val longVisible = long.timeAtPx(long.widthPx - 2 * long.edgeInsetPx) - long.timeAtPx(0.0)
        assertEquals(TimelineZoom.MAX_VISIBLE_US.toDouble(), longVisible, 1.0)
        val anchored = viewport.zoomed(2.0, 500.0, 60_000_000)
        assertEquals(viewport.timeAtPx(500.0), anchored.timeAtPx(500.0), 1.0)
    }

    @Test fun rulerStepsAndLabelsAdapt() {
        assertEquals(1_000_000L, TimelineZoom.tickStepUs(100.0, 60.0))
        assertTrue(TimelineZoom.tickStepUs(0.2, 60.0) >= 300_000_000L)
        assertEquals("1:30", TimelineZoom.label(90_000_000.0, 30_000_000))
        assertEquals("2.5s", TimelineZoom.label(2_500_000.0, 500_000))
        assertTrue(abs(0) == 0)
    }
}
