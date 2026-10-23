package br.com.riseedit.core.editing

import br.com.riseedit.core.Clip
import br.com.riseedit.core.Marker
import br.com.riseedit.core.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RippleTrimTest {
    private fun clip(id: String, ms: Long) = Clip(id = id, uri = "u$id", name = id, startMs = 0, endMs = ms, sourceDurationMs = ms)

    @Test fun trimStartToPlayheadClosesGap() {
        val p = Project(clips = listOf(clip("a", 2000), clip("b", 3000)), markers = listOf(Marker(timeMs = 4000)))
        val r = RippleTrim.atPlayhead(p, 2500, toStart = true)!!
        assertEquals(500, r.project.clips[1].startMs)
        assertEquals(4500, r.project.durationMs)
        assertEquals(2000, r.playheadMs)
        assertEquals(3500, r.project.markers.single().timeMs)
    }

    @Test fun trimEndFromPlayhead() {
        val p = Project(clips = listOf(clip("a", 2000), clip("b", 3000)), markers = listOf(Marker(timeMs = 4500)))
        val r = RippleTrim.atPlayhead(p, 1000, toStart = false)!!
        assertEquals(1000, r.project.clips[0].endMs)
        assertEquals(4000, r.project.durationMs)
        assertEquals(1000, r.playheadMs)
        assertEquals(3500, r.project.markers.single().timeMs)
    }

    @Test fun outsideTimelineIsNoop() {
        val p = Project(clips = listOf(clip("a", 2000)))
        assertNull(RippleTrim.atPlayhead(p, 5000, toStart = true))
        assertNull(RippleTrim.atPlayhead(p, 0, toStart = true))
    }

    @Test fun pasteInsertsAfterClipUnderPlayhead() {
        val p = Project(clips = listOf(clip("a", 2000), clip("b", 3000)))
        val (q, at) = ClipClipboard.paste(p, p.clips[1], 500)
        assertEquals(1, at)
        assertEquals(3, q.clips.size)
        assertEquals("ub", q.clips[1].uri)
    }
}
