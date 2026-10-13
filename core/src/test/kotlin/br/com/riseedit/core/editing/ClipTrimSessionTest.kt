package br.com.riseedit.core.editing

import br.com.riseedit.core.Clip
import br.com.riseedit.core.SpeedPoint
import br.com.riseedit.core.TransformKeyframe
import org.junit.Assert.assertEquals
import org.junit.Test

class ClipTrimSessionTest {
    private fun clip() = Clip(uri = "file:///source.mp4", name = "Source", sourceDurationMs = 4000)

    @Test
    fun bothEdgesTrimTheSourceWithoutChangingTheOriginal() {
        val original = clip()
        val left = ClipTrimSession(original, TrimEdge.START).dragged(1_000_000.0)
        val right = ClipTrimSession(original, TrimEdge.END).dragged(-1_000_000.0)
        assertEquals(1000L, left.startMs)
        assertEquals(3000L, right.endMs)
        assertEquals(0L, original.startMs)
        assertEquals(4000L, original.endMs)
        assertEquals(original.id, left.id)
        assertEquals(original.uri, right.uri)
    }

    @Test
    fun edgesCanRestorePreviouslyTrimmedFootage() {
        val original = clip().trim(1000, 3000)
        assertEquals(0L, ClipTrimSession(original, TrimEdge.START).dragged(-2_000_000.0).startMs)
        assertEquals(4000L, ClipTrimSession(original, TrimEdge.END).dragged(2_000_000.0).endMs)
    }

    @Test
    fun crossingEdgesIsClampedToAMeaningfulMinimum() {
        assertEquals(
            40L,
            ClipTrimSession(clip(), TrimEdge.START).dragged(20_000_000.0).sourceLengthMs
        )
        assertEquals(
            40L,
            ClipTrimSession(clip(), TrimEdge.END).dragged(-20_000_000.0).sourceLengthMs
        )
    }

    @Test
    fun trimUsesOutputTimeAcrossSpeedBoundariesAndRetainsEffects() {
        val original = Clip(
            uri = "file:///ramp.mp4",
            name = "Ramp",
            sourceDurationMs = 12000,
            startMs = 3000,
            speedPoints = listOf(SpeedPoint(4000, 2.0)),
            keyframes = listOf(TransformKeyframe(5000, scale = 1.2f)),
            volume = 0.5f
        )
        val result = ClipTrimSession(original, TrimEdge.START).dragged(1_500_000.0)
        assertEquals(5000L, result.startMs)
        assertEquals(original.speedPoints, result.speedPoints)
        assertEquals(original.keyframes, result.keyframes)
        assertEquals(original.volume, result.volume)
        assertEquals(11000L, ClipTrimSession(original, TrimEdge.END).dragged(-500_000.0).endMs)
    }
}
