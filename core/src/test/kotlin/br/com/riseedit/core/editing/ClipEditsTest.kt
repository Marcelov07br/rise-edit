package br.com.riseedit.core.editing

import br.com.riseedit.core.Clip
import br.com.riseedit.core.HistoryController
import br.com.riseedit.core.Project
import br.com.riseedit.core.SpeedPoint
import br.com.riseedit.core.mask.Mask
import br.com.riseedit.core.mask.MaskGeom
import br.com.riseedit.core.mask.MaskKey
import br.com.riseedit.core.mask.MaskSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipEditsTest {
    private fun clip(name: String = "A", ms: Long = 4000) =
        Clip(uri = "file:///$name.mp4", name = name, sourceDurationMs = ms)

    private fun undoRestores(before: Project, after: Project) {
        val history = HistoryController()
        history.record(before)
        assertEquals(before, history.undo(after))
        assertEquals(after, history.redo(before))
    }

    @Test
    fun duplicateInsertsCopyWithFreshIds() {
        val masked = clip().copy(
            masks = MaskSet(listOf(Mask(id = "m", keys = listOf(MaskKey(0, MaskGeom())))))
        )
        val p = SeparateAudio.apply(Project(clips = listOf(masked, clip("B"))), 0)
        val d = ClipEdits.duplicate(p, 0)
        assertEquals(3, d.clips.size)
        val copy = d.clips[1]
        assertNotEquals(masked.id, copy.id)
        assertNotEquals("m", copy.masks.masks.single().id)
        assertEquals(1, copy.masks.masks.single().keys.size)
        assertEquals(masked.endMs, copy.endMs)
        val linked = d.audio.filter { it.linkedClipId == copy.id }.single()
        assertNotEquals(p.audio.single().id, linked.id)
        assertEquals(masked.durationMs, linked.offsetMs)
        assertEquals("B", d.clips[2].name)
        undoRestores(p, d)
    }

    @Test
    fun cutToCursorKeepsTheOtherSide() {
        val p = Project(clips = listOf(clip().trim(500, 3500)))
        val left = requireNotNull(ClipEdits.cutToCursor(p, 0, 1000, left = true)).clips[0]
        assertEquals(1500L, left.startMs)
        assertEquals(3500L, left.endMs)
        val right = requireNotNull(ClipEdits.cutToCursor(p, 0, 1000, left = false))
        assertEquals(1500L, right.clips[0].endMs)
        assertNull(ClipEdits.cutToCursor(p, 0, 0, left = true))
        assertNull(ClipEdits.cutToCursor(p, 0, 3000, left = false))
        undoRestores(p, right)
    }

    @Test
    fun cutToCursorUsesSpeedMapping() {
        val fast = clip().copy(speedPoints = listOf(SpeedPoint(0, 2.0)))
        val p = Project(clips = listOf(fast))
        assertEquals(2000L, ClipEdits.cutToCursor(p, 0, 1000, left = true)!!.clips[0].startMs)
    }

    @Test
    fun nudgeMovesOneFrameAndClamps() {
        val c = clip().trim(1000, 3000)
        assertEquals(1033L, ClipEdits.nudge(c, TrimEdge.START, 1).startMs)
        assertEquals(2967L, ClipEdits.nudge(c, TrimEdge.END, -1).endMs)
        assertEquals(0L, ClipEdits.nudge(c, TrimEdge.START, -1000).startMs)
        assertEquals(4000L, ClipEdits.nudge(c, TrimEdge.END, 1000).endMs)
        val fast = c.copy(speedPoints = listOf(SpeedPoint(0, 2.0)))
        assertEquals(1067L, ClipEdits.nudge(fast, TrimEdge.START, 1).startMs)
        assertTrue(ClipEdits.nudge(c, TrimEdge.START, 1000).sourceLengthMs >= ClipEdits.MIN_MS)
    }

    @Test
    fun speedIsContinuousClampedAndKeepsPitchFlag() {
        val c = clip()
        assertEquals(4000L * 10 / 3, ClipEdits.speed(c, 0.3).durationMs)
        assertEquals(0.1, ClipEdits.speed(c, 0.01).speedAt(0), 0.0)
        assertEquals(10.0, ClipEdits.speed(c, 50.0).speedAt(0), 0.0)
        assertTrue(ClipEdits.speed(c, 1.0).speedPoints.isEmpty())
        assertFalse(ClipEdits.speed(c, 2.0, keepPitch = false).keepPitch)
        assertTrue(c.keepPitch)
        undoRestores(Project(clips = listOf(c)), Project(clips = listOf(ClipEdits.speed(c, 3.5))))
    }
}
