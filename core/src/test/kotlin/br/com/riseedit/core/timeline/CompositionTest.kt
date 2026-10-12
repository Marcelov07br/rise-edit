package br.com.riseedit.core.timeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CompositionTest {
    private fun video(id: String = "main", name: String = "Main") = Track(
        id, name, TrackKind.VIDEO, 0,
        listOf(Segment("segment:$id", SegmentSource.Media("v", 1000), ConstantSpeedMap(TimeRange(0, 1000), 1.0))),
    )

    @Test
    fun validCompositionUsesMainContentDurationAndSnapshotsTracks() {
        val main = video()
        val audio = Track("audio", "Main", TrackKind.AUDIO, 2000, listOf(Segment.gap("space", 5000)))
        val input = mutableListOf(main, audio)
        val composition = Composition(input, main.id)
        input.clear()
        composition.validate()
        assertEquals(1000L, composition.durationUs)
        assertEquals(2, composition.tracks.size)
        assertEquals(composition, composition.copy())
    }

    @Test
    fun validationRejectsDuplicateIdsNamesMissingMainAndAudioMain() {
        val main = video()
        val bad = listOf(
            Composition(listOf(main, main), "main"),
            Composition(listOf(main, video("other")), "main"),
            Composition(listOf(main, video("other", "Other").copy(segments = main.segments)), "main"),
            Composition(listOf(main), "missing"),
            Composition(listOf(main.copy(kind = TrackKind.AUDIO)), "main"),
            Composition(listOf(main.copy(id = "")), ""),
            Composition(listOf(main.copy(id = "segment:main")), "segment:main"),
        )
        bad.forEach { assertThrows(IllegalArgumentException::class.java) { it.validate() } }
        // Invalid ranges/starts are rejected before they can enter a composition.
        assertThrows(IllegalArgumentException::class.java) { main.copy(startUs = -1) }
        assertThrows(IllegalArgumentException::class.java) { Segment.gap("empty", 0) }
    }

    @Test
    fun fingerprintIgnoresOnlyNamesAndIdsAndRetainsRenderingStructure() {
        val original = Composition(listOf(video()), "main")
        val renamed = original.tracks.map { track ->
            track.copy(id = "new:${track.id}", name = "Renamed", segments = FrozenList(track.segments.map { it.copy(id = "new:${it.id}") }))
        }
        assertEquals(original.renderFingerprint(), Composition(renamed, "new:main").renderFingerprint())
        val moved = Composition(listOf(video().copy(startUs = 1)), "main")
        assertNotEquals(original.renderFingerprint(), moved.renderFingerprint())
        val changed = video().segments.single().copy(source = SegmentSource.Media("other", 1000))
        assertNotEquals(original.renderFingerprint(), Composition(listOf(video().copy(segments = FrozenList(listOf(changed)))), "main").renderFingerprint())
        val retimed = video().segments.single().copy(timeMap = ConstantSpeedMap(TimeRange(0, 1000), 2.0))
        assertNotEquals(original.renderFingerprint(), Composition(listOf(video().copy(segments = FrozenList(listOf(retimed)))), "main").renderFingerprint())
        val second = video("second", "Second")
        assertNotEquals(
            Composition(listOf(video(), second), "main").renderFingerprint(),
            Composition(listOf(video(), second), "second").renderFingerprint(),
        )
    }
}
