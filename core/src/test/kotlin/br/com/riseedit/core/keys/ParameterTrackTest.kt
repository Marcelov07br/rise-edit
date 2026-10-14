package br.com.riseedit.core.keys

import br.com.riseedit.core.TransformKeyframe
import br.com.riseedit.core.TransformPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParameterTrackTest {
    private val legacy = listOf(
        TransformKeyframe(0, 0f, 0f, 1f, 170f, 1f),
        TransformKeyframe(800, 0.5f, -0.3f, 1.6f, -150f, 0.4f),
        TransformKeyframe(1500, -0.2f, 0.4f, 0.8f, 30f, 1f),
        TransformKeyframe(2600, 0.1f, 0.1f, 1.2f, 0f, 0f)
    )

    private class G(
        val t: Long,
        val x: Float,
        val y: Float,
        val s: Float,
        val r: Float,
        val o: Float
    )
    private fun g(t: Long, x: Float, y: Float, s: Float, r: Float, o: Float) = G(t, x, y, s, r, o)

    /** Recorded from the pre-0.9 Hermite TransformPath (0.8.0); the migration must reproduce them. */
    private val golden = listOf(
        g(-100, 0.0f, 0.0f, 1.0f, 170.0f, 1.0f),
        g(0, 0.0f, 0.0f, 1.0f, 170.0f, 1.0f),
        g(133, 0.039321635f, -0.027034229f, 1.0442363f, 172.94908f, 0.9557637f),
        g(400, 0.26333332f, -0.17666668f, 1.3f, 190.0f, 0.7f),
        g(799, 0.50013065f, -0.3002646f, 1.5999972f, 209.99982f, 0.40000278f),
        g(800, 0.5f, -0.3f, 1.6f, -150.0f, 0.4f),
        g(1001, 0.35552225f, -0.14190342f, 1.4399979f, -113.99953f, 0.5200016f),
        g(1250, 0.019387754f, 0.18826531f, 1.0332361f, -22.478142f, 0.8250729f),
        g(1500, -0.2f, 0.4f, 0.8f, 30.0f, 1.0f),
        g(1777, -0.18696721f, 0.3869672f, 0.8633202f, 25.250988f, 0.8416996f),
        g(2100, -0.057150014f, 0.25715002f, 1.0271976f, 12.96018f, 0.432006f),
        g(2599, 0.099999055f, 0.10000095f, 1.1999991f, 7.43866E-5f, 2.4778362E-6f),
        g(3000, 0.1f, 0.1f, 1.2f, 0.0f, 0.0f)
    )

    @Test fun migratedHermiteMatchesGoldenSamples() {
        for (domain in TimeDomain.entries) {
            val tracks = TransformTracks.fromLegacy(legacy, domain)
            golden.forEach { e ->
                val k = tracks.at(e.t)
                assertEquals("x@${e.t}", e.x, k.x, 1e-5f)
                assertEquals("y@${e.t}", e.y, k.y, 1e-5f)
                assertEquals("scale@${e.t}", e.s, k.scale, 1e-5f)
                assertEquals("rot@${e.t}", e.r, k.rotation, 1e-3f)
                assertEquals("opacity@${e.t}", e.o, k.opacity, 1e-5f)
                assertEquals(k, TransformPath.at(legacy, e.t, domain = domain))
            }
            assertEquals(domain, tracks.x.domain)
        }
    }

    private fun track(vararg keys: Key<Float>) =
        ParameterTrack(TimeDomain.LOCAL_OUTPUT, keys.toList(), TrackValue.Scalar)

    @Test fun easings() {
        assertEquals(0f, track(Key(0, 0f, Easing.Hold), Key(1000, 1f)).at(999, 5f))
        assertEquals(0.25f, track(Key(0, 0f, Easing.Linear), Key(1000, 1f)).at(250, 5f), 1e-6f)
        val ease = track(Key(0, 0f, Easing.CubicBezier(0.42, 0.0, 0.58, 1.0)), Key(1000, 1f))
        assertTrue(ease.at(100, 0f) < 0.1f)
        assertEquals(0.5f, ease.at(500, 0f), 1e-4f)
        assertEquals(TransformPath.ease(0.3), Easing.CubicBezier.SMOOTHSTEP.progress(0.3), 1e-9)
        assertEquals(7f, track().at(10, 7f))
    }

    @Test fun frameQuantizationReplacesTolerance() {
        val grid = FrameGrid(30)
        assertEquals(1000L, grid.quantize(1010))
        assertEquals(1033L, grid.quantize(1020))
        var t = track(Key(1000, 0f))
        t = t.upsert(1010, 3f, grid)
        assertEquals(1, t.keys.size)
        assertEquals(1000L, t.keys.single().timeMs)
        t = t.upsert(1040, 4f, grid)
        assertEquals(listOf(1000L, 1033L), t.keys.map { it.timeMs })
        assertTrue(t.hasKeyAt(1030, grid))
        assertFalse(t.remove(1035, grid).hasKeyAt(1033, grid))
        // At 60 fps keys 20 ms apart are distinct frames; the old 40 ms tolerance merged them.
        assertEquals(2, track(Key(1000, 0f)).upsert(1020, 1f, FrameGrid(60)).keys.size)
    }
}
