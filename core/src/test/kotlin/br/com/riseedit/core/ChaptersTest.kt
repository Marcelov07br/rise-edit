package br.com.riseedit.core

import br.com.riseedit.core.editing.MarkerEdits
import br.com.riseedit.core.editing.YouTubeChapters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChaptersTest {
    private fun m(t: Long, l: String) = Marker(timeMs = t, label = l)

    @Test fun validChapters() {
        val r = YouTubeChapters.build(listOf(m(0, "Intro"), m(65_400, "Setup"), m(190_000, "Final")), 300_000)
        assertTrue(r.ok)
        assertEquals("00:00 Intro\n01:05 Setup\n03:10 Final", r.text)
    }

    @Test fun firstChapterIsForcedToZero() {
        val r = YouTubeChapters.build(listOf(m(30_000, "A"), m(60_000, "B"), m(90_000, "C")), 200_000)
        assertEquals("00:00 Introdução", r.text.lines().first())
        assertEquals(1, r.notes.size)
        assertTrue(r.ok)
    }

    @Test fun warnsTooFewAndTooShort() {
        val r = YouTubeChapters.build(listOf(m(0, "A"), m(5_000, "")), 60_000)
        assertFalse(r.ok)
        assertEquals(2, r.problems.size) // < 3 chapters + "A" has 5 s
        assertEquals("00:05 Capítulo 2", r.text.lines()[1])
        val last = YouTubeChapters.build(listOf(m(0, "A"), m(20_000, "B"), m(55_000, "C")), 60_000)
        assertEquals(listOf("\"C\" (00:55) tem 5 s; o mínimo é 10 s."), last.problems)
    }

    @Test fun longVideosUseHours() {
        val r = YouTubeChapters.build(listOf(m(0, "A"), m(1_800_000, "B"), m(3_723_000, "C")), 4_000_000)
        assertEquals("0:00:00 A\n0:30:00 B\n1:02:03 C", r.text)
    }

    @Test fun addReusesNearbyAndEdits() {
        val c = Clip(uri = "file:///a", name = "a", sourceDurationMs = 60_000)
        val p = Project(clips = listOf(c))
        val (p1, a) = MarkerEdits.add(p, 10_000)
        val (p2, same) = MarkerEdits.add(p1, 10_100)
        assertEquals(a.id, same.id)
        assertEquals(1, p2.markers.size)
        val p3 = MarkerEdits.update(p2, a.copy(label = "Gancho", color = MarkerEdits.COLORS[4]))
        assertEquals("Gancho", p3.markers.single().label)
        assertEquals(0, MarkerEdits.remove(p3, a.id).markers.size)
    }
}
