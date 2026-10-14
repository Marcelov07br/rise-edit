package br.com.riseedit.core

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class EditingTest {
    @Test fun splittingTrimmedClipPreservesSourceCoordinatesAndDuration() {
        val clip = Clip(uri = "source", name = "Video", sourceDurationMs = 10000, startMs = 2000, endMs = 8000)
        val project = Project(listOf(clip)).split(0, 1500)
        assertEquals(6000L, project.durationMs)
        assertEquals(3500L, project.clips[0].endMs)
        assertEquals(3500L, project.clips[1].startMs)
        assertNotEquals(project.clips[0].id, project.clips[1].id)
        assertEquals("source", project.clips[1].uri)
    }
    @Test(expected = IllegalArgumentException::class) fun refusesEmptyCut() {
        Clip(uri = "a", name = "a", sourceDurationMs = 100).split(100)
    }
    @Test fun fixedTargetCorrectlyInvertsRotationAndTranslation() {
        val pose = Pose(12.0, -4.0, 0.05)
        val identity = pose.compose(pose.inverse())
        assertEquals(0.0, identity.x, 1e-9); assertEquals(0.0, identity.y, 1e-9)
        val analysis = MotionAnalysis(1000, 1000, listOf(MotionSample(0, Pose()), MotionSample(100, pose)))
        val result = MotionSmoother.corrections(analysis, Stabilization(true, MotionGoal.FIXED, 1.0, maxZoom = 1.25), 0, 100)
        val corrected = result.last().pose.compose(pose)
        assertEquals(0.0, corrected.x, 1e-8); assertEquals(0.0, corrected.angle, 1e-8)
    }
    @Test fun limitsCorrectionToZoomBudgetForAllCorners() {
        val p = MotionSmoother.bounded(Pose(90.0, -100.0, 0.2), 320, 180, 1.10)
        assertTrue(MotionSmoother.requiredZoom(p, 320, 180) <= 1.100000001)
        assertEquals(Pose(), MotionSmoother.bounded(Pose(10.0, 0.0, 0.0), 320, 180, 1.0))
    }
    @Test fun smoothingKeepsConstantMotionAndInterpolates() {
        val analysis = MotionAnalysis(320, 180, (0..10).map { MotionSample(it * 100L, Pose(it.toDouble(), 0.0, 0.0)) })
        val result = MotionSmoother.corrections(analysis, Stabilization(true, strength = 1.0, smoothSeconds = 0.2), 0, 1000)
        assertEquals(0.0, result[5].pose.x, 1e-9)
        assertEquals(5.0, MotionSmoother.interpolate(listOf(Correction(0, Pose()), Correction(100, Pose(10.0))), 50).x, 1e-9)
    }
    @Test fun neverSmoothAcrossSceneCut() {
        val analysis = MotionAnalysis(320, 180, listOf(MotionSample(0, Pose(80.0), 0), MotionSample(100, Pose(), 1)))
        val result = MotionSmoother.corrections(analysis, Stabilization(true, MotionGoal.FIXED), 0, 100)
        assertEquals(Pose(), result.last().pose)
    }
}

class AudioMuteTest {
    @org.junit.Test
    fun mutedAndOutOfRangeAudioIsExcludedFromTheMix() {
        val clip = Clip(uri = "c", name = "c", sourceDurationMs = 2_000)
        val a = AudioClip(uri = "a", name = "a", sourceDurationMs = 1_000)
        val b = a.copy(id = "b", muted = true)
        val late = a.copy(id = "late", offsetMs = 5_000)
        val project = Project(listOf(clip), audio = listOf(a, b, late))
        org.junit.Assert.assertEquals(listOf(a), project.audibleAudio())
        org.junit.Assert.assertEquals(0.7f, b.volume) // mute keeps the volume
    }
}
