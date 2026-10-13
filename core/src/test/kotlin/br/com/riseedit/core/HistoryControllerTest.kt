package br.com.riseedit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryControllerTest {
    @Test
    fun retainsExactlyThirtyRevisionsAndCanRedoThem() {
        val history = HistoryController()
        val versions = (0..40).map { Project(id = "project", name = "Revision $it") }
        versions.dropLast(1).forEach(history::record)
        var current = versions.last()
        repeat(30) { current = requireNotNull(history.undo(current)) }
        assertEquals(versions[10], current)
        assertNull(history.undo(current))
        repeat(30) { current = requireNotNull(history.redo(current)) }
        assertEquals(versions.last(), current)
        assertFalse(history.canRedo)
    }

    @Test
    fun failedSaveDoesNotConsumeUndoOrCreateRedo() {
        val history = HistoryController()
        val before = Project(name = "Before")
        val current = before.copy(name = "After")
        history.record(before)
        assertNull(history.undo(current) { null })
        assertEquals(before, history.undoCandidate)
        assertFalse(history.canRedo)
        assertEquals(before, history.undo(current))
        assertEquals(current, history.redoCandidate)
    }

    @Test
    fun failedRedoSaveRetainsBothStacks() {
        val history = HistoryController()
        val before = Project(name = "Before")
        val current = before.copy(name = "After")
        history.record(before)
        history.undo(current)
        assertNull(history.redo(before) { null })
        assertFalse(history.canUndo)
        assertEquals(current, history.redoCandidate)
        val saved = current.copy(modifiedAt = 42)
        assertEquals(saved, history.redo(before) { saved })
        assertEquals(before, history.undoCandidate)
    }

    @Test
    fun editingAfterUndoDiscardsRedoAndResetClearsSession() {
        val history = HistoryController()
        val original = Project(name = "Original")
        val edited = original.copy(name = "Edited")
        history.record(original)
        history.undo(edited)
        assertTrue(history.canRedo)
        history.record(original)
        assertFalse(history.canRedo)
        assertEquals(listOf(original), history.projects)
        history.clear()
        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
        assertTrue(history.projects.isEmpty())
    }

    @Test
    fun preservesSourceTrimSpeedAndProxyAcrossUndoRedo() {
        val history = HistoryController()
        val clip = Clip(
            uri = "content://original",
            name = "Original",
            sourceDurationMs = 5000,
            startMs = 500,
            endMs = 4500,
            speedPoints = listOf(SpeedPoint(500, 0.5), SpeedPoint(2000, 2.0)),
            proxyUri = "file:///proxy.mp4",
            proxyRequired = true
        )
        val project = Project(clips = listOf(clip))
        val split = project.split(0, 500)
        history.record(project)
        assertEquals(project, history.undo(split))
        assertEquals(split, history.redo(project))
        assertEquals(clip.speedPoints, history.undoCandidate?.clips?.single()?.speedPoints)
        assertEquals(clip.proxyUri, history.undoCandidate?.clips?.single()?.proxyUri)
    }
}
