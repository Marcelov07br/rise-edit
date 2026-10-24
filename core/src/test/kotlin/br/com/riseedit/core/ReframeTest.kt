package br.com.riseedit.core

import br.com.riseedit.core.motion.Reframe
import br.com.riseedit.core.motion.SubjectSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

class ReframeTest {
    private val src = 16f / 9f
    private val out = 9f / 16f

    @Test fun gapsHoldAndInterpolate() {
        val f = Reframe.fillGaps(
            listOf(SubjectSample(0, null), SubjectSample(100, 0.2f), SubjectSample(200, null), SubjectSample(300, 0.6f), SubjectSample(400, null))
        )
        listOf(0.2f, 0.2f, 0.4f, 0.6f, 0.6f).zip(f.map { it.second }).forEach { (e, a) -> assertEquals(e, a, 1e-5f) }
        assertEquals(0.5f, Reframe.fillGaps(listOf(SubjectSample(0, null))).single().second)
    }

    @Test fun jitterDoesNotShake() {
        // subject at 0.7 with ±0.03 detector noise: path must be flat (dead zone + smoothing)
        val samples = (0 until 100).map { SubjectSample(it * 200L, 0.7f + 0.03f * sin(it * 2.1f)) }
        val path = Reframe.path(samples, src, out)
        val spread = path.maxOf { it.second } - path.minOf { it.second }
        assertTrue("spread $spread", spread < 0.01f)
        val keys = Reframe.keyframes(samples, src, out)
        assertTrue("keys ${keys.size}", keys.size <= 3)
    }

    @Test fun followsAMoveAndStaysInside() {
        val samples = (0 until 100).map { SubjectSample(it * 200L, if (it < 50) 0.1f else 0.95f) }
        val path = Reframe.path(samples, src, out)
        val visible = out / src
        assertTrue(path.all { it.second >= visible / 2 - 1e-4f && it.second <= 1 - visible / 2 + 1e-4f })
        assertTrue(abs(path.first().second - visible / 2) < 0.02f)
        assertTrue(abs(path.last().second - (1 - visible / 2)) < 0.02f)
        val keys = Reframe.keyframes(samples, src, out)
        assertTrue(keys.size in 2..12)
        assertEquals(0L, keys.first().sourceMs)
        // left subject → positive NDC shift (frame moves right)
        assertTrue(keys.first().x > 0f && keys.last().x < 0f)
    }

    @Test fun sampleTimesCapped() {
        assertEquals(5, Reframe.sampleTimes(0, 1000).size)
        assertTrue(Reframe.sampleTimes(0, 3_600_000).size <= 600)
    }
}
