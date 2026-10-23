package br.com.riseedit.core.editing

import br.com.riseedit.core.ChromaFormat
import br.com.riseedit.core.Clip
import br.com.riseedit.core.MediaFault
import br.com.riseedit.core.Stabilization
import org.junit.Assert.*
import org.junit.Test

class CutToolsTest {
    private val clip = Clip(uri = "a", name = "a", sourceDurationMs = 10_000, startMs = 2_000, endMs = 6_000)

    @Test fun constantPresetBakesToNothing() {
        assertTrue(SpeedCurve.bake(clip, SpeedCurve.Preset.CONSTANT.knots).isEmpty())
    }

    @Test fun bulletIsSlowInTheMiddleAndFastAtEdges() {
        val c = SpeedCurve.apply(clip, SpeedCurve.Preset.BULLET.knots)
        assertEquals(2.0, c.speedAt(2_000), 0.05)
        assertEquals(0.2, c.speedAt(4_000), 0.05)
        assertTrue(c.speedPoints.zipWithNext().all { (a, b) -> a.sourceMs < b.sourceMs })
        assertTrue(c.speedPoints.all { it.sourceMs in 2_000 until 6_000 })
    }

    @Test fun knotsRoundTrip() {
        val c = SpeedCurve.apply(clip, SpeedCurve.Preset.RAMP_UP.knots)
        val k = SpeedCurve.knotsOf(c)
        assertEquals(0.5, k.first().speed, 0.1)
        assertEquals(3.0, k.last().speed, 0.1)
    }

    @Test fun replaceKeepsLengthAndInPoint() {
        val old = clip.copy(stabilization = Stabilization(enabled = true), volume = 0.5f)
        val fresh = Clip(uri = "b", name = "b", sourceDurationMs = 20_000)
        val r = MediaReplace.replace(old, fresh)
        assertEquals(old.id, r.id); assertEquals("b", r.uri)
        assertEquals(2_000L, r.startMs); assertEquals(6_000L, r.endMs); assertEquals(0.5f, r.volume, 0f)
        assertFalse(r.stabilization.enabled)
    }

    @Test fun replaceWithShorterMediaFits() {
        val r = MediaReplace.replace(clip, Clip(uri = "b", name = "b", sourceDurationMs = 3_000))
        assertEquals(0L, r.startMs); assertEquals(3_000L, r.endMs)
        val r2 = MediaReplace.replace(clip, Clip(uri = "b", name = "b", sourceDurationMs = 5_000))
        assertEquals(1_000L, r2.startMs); assertEquals(5_000L, r2.endMs)
    }

    @Test fun chromaWarningNamesTheFormat() {
        val f = MediaFault.UnsupportedChroma(ChromaFormat.YUV422, 10, "video/avc")
        assertTrue(f.userMessage, f.userMessage.contains("H.264 4:2:2 · 10 bits"))
    }
}
