package br.com.riseedit.core.timeline

import br.com.riseedit.core.Clip
import br.com.riseedit.core.SpeedPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilmStripTest {
    private val clip = Clip(uri = "file:///a.mp4", name = "A", sourceDurationMs = 10_000)

    @Test
    fun tilesAreFixedSourceIntervalsWidthFromZoom() {
        // 100 px/s, 60 px target → 1 s per tile, 100 px each.
        val t = FilmStrip.tiles(clip.trim(0, 4000), 100.0, 60.0)
        assertEquals(listOf(0L, 1000L, 2000L, 3000L), t.map { it.sourceMs })
        t.forEach { assertEquals(100.0, it.widthPx, 1e-6) }
        assertEquals(listOf(0.0, 100.0, 200.0, 300.0), t.map { it.xPx })
    }

    @Test
    fun trimmingTheStartRevealsOrHidesTilesWithoutReindexing() {
        val trimmed = FilmStrip.tiles(clip.trim(1500, 4000), 100.0, 60.0)
        // Same source frames (1 s grid); first tile is cut by the edge (starts at −50 px).
        assertEquals(listOf(1000L, 2000L, 3000L), trimmed.map { it.sourceMs })
        assertEquals(-50.0, trimmed.first().xPx, 1e-6)
        assertEquals(100.0, trimmed.first().widthPx, 1e-6)
    }

    @Test
    fun speedShrinksTilesInOutputTime() {
        val fast = clip.trim(0, 4000).copy(speedPoints = listOf(SpeedPoint(0, 2.0)))
        val t = FilmStrip.tiles(fast, 100.0, 60.0)
        // 2× → 2 s of source per tile at 100 px each.
        assertEquals(listOf(0L, 2000L), t.map { it.sourceMs })
        assertEquals(100.0, t[1].xPx, 1e-6)
        assertEquals(2000.0, FilmStrip.outputAt(fast, 4000), 1e-6)
    }

    @Test
    fun onlyVisibleTilesAreReturned() {
        val t = FilmStrip.tiles(clip, 100.0, 60.0, visibleFromPx = 250.0, visibleToPx = 420.0)
        assertEquals(listOf(2000L, 3000L, 4000L), t.map { it.sourceMs })
        assertTrue(FilmStrip.stepMs(clip, 10.0, 60.0) >= 6000)
    }
}
