package br.com.riseedit.core.editing

import br.com.riseedit.core.AudioClip
import br.com.riseedit.core.Clip
import br.com.riseedit.core.HistoryController
import br.com.riseedit.core.Project
import br.com.riseedit.core.Selection
import br.com.riseedit.core.TextLayer
import br.com.riseedit.core.TransformKeyframe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SplitAtPlayheadTest {
    private fun clip(n: String) = Clip(uri = "file:///$n.mp4", name = n, sourceDurationMs = 2000)
    private val music =
        AudioClip(
            id = "m",
            uri = "file:///m.mp3",
            name = "M",
            sourceDurationMs = 5000,
            offsetMs = 500
        )
    private val title = TextLayer(
        id = "t",
        text = "Oi",
        startMs = 1000,
        endMs = 3000,
        keyframes = listOf(TransformKeyframe(0, x = 0f), TransformKeyframe(2000, x = 1f))
    )
    private val project =
        Project(clips = listOf(clip("A"), clip("B")), audio = listOf(music), texts = listOf(title))

    @Test
    fun withoutSelectionSplitsMainClipUnderPlayheadAndSelectsRight() {
        val r = requireNotNull(SplitAtPlayhead.apply(project, Selection.None, 2500, 30))
        assertEquals(3, r.project.clips.size)
        assertEquals(500L, r.project.clips[1].endMs)
        assertEquals(r.project.clips[2].id, r.rightId)
        assertEquals(Selection.Clip(r.rightId), r.selection)
        assertEquals(project.durationMs, r.project.durationMs)
        assertEquals(project.audio, r.project.audio)
        val h = HistoryController()
        h.record(project)
        assertEquals(project, h.undo(r.project))
    }

    @Test
    fun selectedClipStillUsesThePlayhead() {
        val a = project.clips[0].id
        val r = requireNotNull(SplitAtPlayhead.apply(project, Selection.Clip(a), 3000, 30))
        assertEquals(1000L, r.project.clips[1].endMs)
        assertEquals("B", r.project.clips[2].name)
    }

    @Test
    fun selectedLayerSplitsOnlyThatLayer() {
        val r = requireNotNull(SplitAtPlayhead.apply(project, Selection.Audio("m"), 1500, 30))
        assertEquals(project.clips, r.project.clips)
        val (left, right) = r.project.audio
        assertEquals(1000L, left.endMs)
        assertEquals(1000L, right.startMs)
        assertEquals(1500L, right.offsetMs)
        assertNotEquals("m", right.id)
        assertEquals(Selection.Audio(right.id), r.selection)
    }

    @Test
    fun textKeysAreShiftedSoTheRightHalfLooksTheSame() {
        val r = requireNotNull(SplitAtPlayhead.apply(project, Selection.Text("t"), 2000, 30))
        val (left, right) = r.project.texts
        assertEquals(2000L, left.endMs)
        assertEquals(2000L, right.startMs)
        assertEquals(title.transformAt(1000).x, right.transformAt(0).x, 1e-4f)
    }

    @Test
    fun disabledWithinOneFrameOfAnEdgeOrOutside() {
        assertNull(SplitAtPlayhead.target(project, Selection.None, 2010, 30))
        assertNull(SplitAtPlayhead.target(project, Selection.None, 1990, 30))
        assertEquals(
            Selection.Clip(project.clips[1].id),
            SplitAtPlayhead.target(project, Selection.None, 2040, 30)
        )
        assertNull(SplitAtPlayhead.target(project, Selection.Text("t"), 500, 30))
        assertNull(SplitAtPlayhead.target(project, Selection.None, 4000, 30))
    }
}
