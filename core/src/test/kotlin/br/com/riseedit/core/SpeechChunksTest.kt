package br.com.riseedit.core

import kotlin.math.PI
import kotlin.math.sin
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class SpeechChunksTest {
    private val rate = 16_000

    /** [spec] = list of (ms, voiced). */
    private fun audio(vararg spec: Pair<Int, Boolean>): FloatArray {
        val out = ArrayList<Float>()
        for ((ms, voiced) in spec) repeat(ms * rate / 1000) { i ->
            out += if (voiced) (0.3 * sin(2 * PI * 220 * i / rate)).toFloat() else 0.001f * ((i % 7) - 3)
        }
        return out.toFloatArray()
    }

    @Test fun silenceIsSkippedAndSpeechKept() {
        val pcm = audio(3000 to false, 2000 to true, 9000 to false, 1500 to true, 2000 to false)
        val v = SpeechChunks.voiced(pcm, rate)
        assertEquals(2, v.size)
        assertTrue(v[0].first in 2_700L * 16..3_000L * 16)
        assertTrue(v[1].last <= (3000 + 2000 + 9000 + 1500 + 300L) * 16)
        val chunks = SpeechChunks.plan(v, rate)
        assertEquals(1, chunks.size) // 4 s of speech + glue fits one whisper window
        assertTrue(chunks[0].samples(rate) < 5 * rate)
    }

    @Test fun longSpeechSplitsIntoWindows() {
        val v = listOf(0L until 70_000L * 16)
        val chunks = SpeechChunks.plan(v, rate)
        assertEquals(3, chunks.size)
        assertTrue(chunks.all { it.samples(rate) <= SpeechChunks.MAX_CHUNK_MS * 16 })
    }

    @Test fun gluedTimesMapBackToSource() {
        val chunk = SpeechChunk(listOf(16_000L * 3 until 16_000L * 5, 16_000L * 14 until 16_000L * 16))
        assertEquals(3000L, chunk.toSourceMs(0, rate))
        assertEquals(4500L, chunk.toSourceMs(1500, rate))
        // After 2 s of piece 1 + 200 ms glue → start of piece 2 (14 s).
        assertEquals(14_000L, chunk.toSourceMs(2200, rate))
        assertEquals(15_000L, chunk.toSourceMs(3200, rate))
        val seg = SpeechChunks.remap(listOf(SpeechSegment(2300, 3000, "oi", listOf(SpeechWord(2300, 2600, "oi")))), chunk, rate)
        assertEquals(14_100L, seg[0].startMs)
        assertEquals(14_100L, seg[0].words[0].startMs)
    }

    @Test fun bigCoresSkipLittleCluster() {
        // 8 Gen 3: 1×3.3 + 5×3.15/2.96 + 2×2.27 → 6 big. 8 Elite: 2×4.32 + 6×3.53 → 8.
        assertEquals(6, CpuTopology.bigCores(listOf(2_270_000, 2_270_000, 2_960_000, 2_960_000, 3_150_000, 3_150_000, 3_150_000, 3_300_000), 8))
        assertEquals(8, CpuTopology.bigCores(List(6) { 3_532_800L } + List(2) { 4_320_000L }, 8))
        assertEquals(4, CpuTopology.bigCores(List(4) { 1_800_000L } + List(4) { 2_800_000L }, 8))
        assertEquals(4, CpuTopology.bigCores(emptyList(), 4))
    }
}
