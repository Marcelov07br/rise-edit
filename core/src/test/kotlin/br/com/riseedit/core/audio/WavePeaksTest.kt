package br.com.riseedit.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WavePeaksTest {
    @Test fun maxPerBucketOverTrim() {
        val s = FloatArray(100) { it / 100f }
        val p = WavePeaks.peaks(s, 10_000, 5_000, 10_000, 5)
        assertEquals(5, p.size)
        assertEquals(0.59f, p[0], 1e-4f)
        assertEquals(0.99f, p[4], 1e-4f)
        assertTrue(p.toList().zipWithNext().all { (a, b) -> b >= a })
    }
    @Test fun sparseSamplesStayContinuous() {
        val p = WavePeaks.peaks(FloatArray(4) { 0.5f }, 4_000, 0, 4_000, 64)
        assertTrue(p.all { it == 0.5f })
    }
    @Test fun levelIsPowerOfTwoAndCapped() {
        assertEquals(64, WavePeaks.level(180f))
        assertEquals(4096, WavePeaks.level(1e7f))
        assertEquals(1, WavePeaks.level(1f))
    }
}
