package br.com.riseedit.core.timeline

import br.com.riseedit.core.AudioClip
import br.com.riseedit.core.Clip
import br.com.riseedit.core.Project
import br.com.riseedit.core.SpeedPoint
import br.com.riseedit.core.TextLayer
import br.com.riseedit.core.VideoOverlay
import kotlin.math.abs
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectAdapterTest {
    private fun assertParity(clip: Clip) {
        val segment = Project(listOf(clip)).toComposition().tracks.first().segments.single()
        assertTrue("Duration drift", abs(segment.durationUs - clip.durationMs * 1000) <= 1000)
        val samples = (0L..clip.durationMs step maxOf(1, clip.durationMs / 80)) + clip.durationMs
        samples.forEach { time ->
            val actual = segment.timeMap.targetToSource(time * 1000)
            assertTrue("Source drift at $time", abs(actual - clip.sourceAt(time) * 1000) <= 1000)
        }
    }

    @Test
    fun generatedTrimmedClipsAgreeWithLegacyTimingWithAndWithoutSpeedPoints() {
        val random = Random(42)
        repeat(180) {
            val start = random.nextLong(0, 3000)
            val end = random.nextLong(start + 1, 10001)
            val points = if (it % 2 == 0) {
                emptyList()
            } else {
                (0 until 20).map { random.nextLong(0, 10000) }.distinct().sorted().map { time ->
                    SpeedPoint(
                        time,
                        listOf(0.25, 0.5, 0.75, 1.0, 1.5, 2.0, 3.0, 4.0).random(random)
                    )
                }
            }
            assertParity(
                Clip(
                    uri = "v",
                    name = "Generated",
                    sourceDurationMs = 10000,
                    startMs = start,
                    endMs = end,
                    speedPoints = points
                )
            )
        }
    }

    @Test
    fun originalRampAndTrimRetainAbsoluteBoundaries() {
        val clip =
            Clip(
                uri = "v",
                name = "Ramp",
                sourceDurationMs = 10000,
                startMs = 2000,
                endMs = 8000,
                speedPoints = listOf(
                    SpeedPoint(2000, 0.5),
                    SpeedPoint(4000, 2.0),
                    SpeedPoint(6000, 1.0)
                )
            )
        val map = Project(listOf(clip)).toComposition().tracks.first().segments.single().timeMap
        assertEquals(7_000_000L, map.targetRange.durationUs)
        assertEquals(4_000_000L, map.targetToSource(4_000_000))
        assertEquals(5_000_000L, map.targetToSource(4_500_000))
        assertParity(clip.trim(4000, 8000))
    }

    @Test
    fun thousandsOfFractionalPiecesDoNotAccumulateIndependentRoundingDrift() {
        val clip = Clip(
            uri = "v",
            name = "Dense",
            sourceDurationMs = 4000,
            speedPoints = (0 until 4000).map { SpeedPoint(it.toLong(), 1.5) }
        )
        assertParity(clip)
        val map = Project(listOf(clip)).toComposition().tracks.first().segments.single().timeMap
        assertEquals(2_666_667L, map.targetRange.durationUs)
        assertEquals(4_000_000L, map.targetToSource(Long.MAX_VALUE))
    }

    @Test
    fun preservesOverlappingAudioOffsetsAndSharedClipIdsWithoutExtendingMainDuration() {
        val clip = Clip(uri = "v", name = "Main", sourceDurationMs = 2000)
        val sound =
            AudioClip(uri = "a", name = "Sound", sourceDurationMs = 5000, offsetMs = 100, track = 1)
        val project = Project(
            listOf(clip, clip),
            audio = listOf(sound, sound.copy(offsetMs = 500)),
            overlays = listOf(VideoOverlay(clip = clip, offsetMs = 250)),
            texts = listOf(TextLayer(text = "Ignored"))
        )
        val composition = project.toComposition()
        composition.validate()
        assertEquals(4_000_000L, composition.durationUs)
        assertEquals(
            listOf(100_000L, 500_000L),
            composition.tracks.filter {
                it.kind ==
                    TrackKind.AUDIO
            }.map { it.startUs }
        )
        assertEquals(250_000L, composition.tracks[1].startUs)
        assertEquals(4, composition.tracks.size)
        assertEquals(
            project.toComposition().renderFingerprint(),
            project.copy(id = "new", name = "Renamed").toComposition().renderFingerprint()
        )
        assertEquals(0L, Project().toComposition().durationUs)
    }
}
