package br.com.riseedit.core.perf

import br.com.riseedit.core.HistoryController
import br.com.riseedit.core.Selection
import br.com.riseedit.core.editing.RippleDelete
import br.com.riseedit.core.editing.RippleTrim
import br.com.riseedit.core.editing.SeparateAudio
import br.com.riseedit.core.editing.SplitAtPlayhead
import br.com.riseedit.core.editing.TimelineIndex
import br.com.riseedit.core.timeline.AudioSchedule
import br.com.riseedit.core.timeline.toComposition
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Run with -Dperf=true (./gradlew :core:test -Dperf=true --tests '*TimelinePerfTest*'). */
class TimelinePerfTest {
    private val p = PerfFixture.marce()
    private val mid = p.durationMs / 2

    private val linked = (0 until 200).fold(p) { acc, i -> SeparateAudio.apply(acc, i * 10) }

    @Test fun syncAllMatchesPerClipSync() {
        val trimmed = linked.replace(5, linked.clips[5].trim(linked.clips[5].startMs + 300, linked.clips[5].endMs))
        val old = trimmed.clips.fold(trimmed) { acc, c -> SeparateAudio.sync(acc, c.id) }
        assertEquals(old, SeparateAudio.syncAll(trimmed))
    }

    @Test fun fixtureShape() {
        assertEquals(PerfFixture.CLIPS, p.clips.size)
        assertTrue(p.durationMs in 40 * 60_000L..50 * 60_000L)
        assertEquals(4, p.clips.map { it.uri }.distinct().size)
    }

    @Test fun visibleIndexMatchesNaive() {
        val idx = TimelineIndex.of(p.clips.map { it.durationMs })
        for (w in listOf(0L to 10_000L, mid to mid + 30_000, p.durationMs - 5_000 to p.durationMs + 1000, 0L to p.durationMs)) {
            val naive = naiveVisible(w.first, w.second)
            assertEquals(naive, idx.visible(w.first, w.second).toList())
        }
    }

    private fun naiveVisible(a: Long, b: Long): List<Int> {
        var off = 0L
        val out = ArrayList<Int>()
        p.clips.forEachIndexed { i, c -> val d = c.durationMs; if (off < b && off + d > a) out += i; off += d }
        return out
    }

    @Test fun bench() {
        assumeTrue(System.getProperty("perf") == "true")
        println("PERF clips=${p.clips.size} durationMin=${p.durationMs / 60_000.0} audio=${p.audio.size} overlays=${p.overlays.size}")
        PerfBench.run("Project.durationMs (cópia nova)") { p.copy(name = "x").durationMs }
        PerfBench.run("ripple trim Z c/ 200 áudios vinculados") { RippleTrim.atPlayhead(linked, mid + 200, toStart = true) }
        PerfBench.run("split @ meio (SplitAtPlayhead)") { SplitAtPlayhead.apply(p, Selection.None, mid, 30)!! }
        PerfBench.run("ripple trim Z (início→agulha)") { RippleTrim.atPlayhead(p, mid + 200, toStart = true) }
        PerfBench.run("ripple trim D (agulha→fim)") { RippleTrim.atPlayhead(p, mid + 200, toStart = false) }
        PerfBench.run("ripple delete clipe 1000") { RippleDelete.clip(p, 1000) }
        PerfBench.run("100 edições + 100 undo + 100 redo", warmup = 2, runs = 7) {
            val h = HistoryController(capacity = 100)
            var cur = p
            repeat(100) { i -> h.record(cur); cur = cur.copy(name = "e$i") }
            repeat(100) { cur = h.undo(cur)!! }
            repeat(100) { cur = h.redo(cur)!! }
            cur
        }
        run {
            // Waveform: old draw walked/filtered every sample per frame; new draw reuses cached peaks.
            val a = p.audio.maxBy { it.durationMs }
            val wave = FloatArray(4096) { ((it * 7919) % 1000) / 1000f }
            val width = 20_000f
            fun oldDraw(): Float { var acc = 0f; for (i in wave.indices) { val src = a.sourceDurationMs * i.toFloat() / wave.size; if (src >= a.startMs && src <= a.endMs) acc += (src - a.startMs) / a.durationMs * width + wave[i] }; return acc }
            val level = br.com.riseedit.core.audio.WavePeaks.level(width)
            val cached = br.com.riseedit.core.audio.WavePeaks.peaks(wave, a.sourceDurationMs, a.startMs, a.endMs, level)
            PerfBench.run("waveform draw (todas amostras, 4096)", runs = 51) { oldDraw() }
            PerfBench.run("waveform picos: build por zoom", runs = 51) { br.com.riseedit.core.audio.WavePeaks.peaks(wave, a.sourceDurationMs, a.startMs, a.endMs, level) }
            PerfBench.run("waveform draw (picos cacheados)", runs = 51) { var acc = 0f; for (i in cached.indices) acc += cached[i]; acc }
        }
        PerfBench.run("toComposition()") { p.toComposition() }
        PerfBench.run("AudioSchedule 1 quadro") {
            val d = p.durationMs
            AudioSchedule.layers(p).mapNotNull { AudioSchedule.at(it, d, mid) }
        }
        PerfBench.run("layout: offsets + visíveis (ingênuo)") { naiveVisible(mid, mid + 30_000) }
        PerfBench.run("layout: TimelineIndex.of (por edição)") { TimelineIndex.of(p.clips.map { it.durationMs }) }
        val idx = TimelineIndex.of(p.clips.map { it.durationMs })
        PerfBench.run("layout: visíveis (índice, por quadro)", runs = 51) { idx.visible(mid, mid + 30_000).toList() }
        File(System.getProperty("perf.out") ?: "build/perf-core.txt").writeText(PerfBench.results.joinToString("\n"))
    }
}
