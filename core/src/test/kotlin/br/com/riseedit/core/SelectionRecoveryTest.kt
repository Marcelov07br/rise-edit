package br.com.riseedit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionRecoveryTest {
    private val clip = Clip(uri = "content://v", name = "v", sourceDurationMs = 1000)
    private val audio = AudioClip(uri = "content://a", name = "a", sourceDurationMs = 1000)
    private val overlay =
        VideoOverlay(clip = Clip(uri = "content://o", name = "o", sourceDurationMs = 1000))
    private val text = TextLayer(text = "t")
    private val project =
        Project(
            clips = listOf(clip),
            audio = listOf(audio),
            overlays = listOf(overlay),
            texts = listOf(text)
        )

    @Test fun selectionResolvesByIdAndDropsDeletedTargets() {
        assertEquals(Selection.None, Selection.of(project, null))
        assertEquals(Selection.Overlay(overlay.id), Selection.of(project, overlay.id))
        assertEquals(Selection.Text(text.id), Selection.of(project, text.id))
        assertEquals(Selection.Audio(audio.id), Selection.of(project, audio.id))
        assertEquals(Selection.Clip(clip.id), Selection.of(project, clip.id))
        assertEquals(Selection.None, Selection.of(project, "gone"))
        assertEquals(
            Selection.None,
            Selection.Overlay(overlay.id).revalidated(project.copy(overlays = emptyList()))
        )
        assertEquals(Selection.Text(text.id), Selection.Text(text.id).revalidated(project))
    }

    @Test fun missingMediaAndReconnect() {
        val missing = ProjectRecovery.missing(project) { it == "content://v" }
        assertEquals(listOf("content://a", "content://o"), missing.map { it.uri })
        val fixed = ProjectRecovery.reconnect(project, "content://o", "content://o2")
        assertEquals("content://o2", fixed.overlays.single().clip.uri)
        assertTrue(fixed.overlays.single().clip.proxyUri == null)
        assertEquals(project.clips, fixed.clips)
        assertTrue(ProjectRecovery.missing(fixed) { true }.isEmpty())
        assertTrue(ProjectRecovery.restoredCopy(project).id != project.id)
    }
}
