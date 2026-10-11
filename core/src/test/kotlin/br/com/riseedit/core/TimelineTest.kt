package br.com.riseedit.core

import org.junit.Assert.*
import org.junit.Test

class TimelineTest {
    private fun clip(points: List<SpeedPoint>) = Clip(uri="video",name="video",sourceDurationMs=10000,startMs=2000,endMs=8000,speedPoints=points)
    @Test fun speedSplitUsesSourceCoordinatesAndPreservesDuration() {
        val c=clip(listOf(SpeedPoint(2000,2.0)))
        assertEquals(3000L,c.durationMs)
        val parts=c.split(1000)
        assertEquals(4000L,parts.first().endMs)
        assertEquals(c.durationMs,parts.sumOf {it.durationMs})
    }
    @Test fun rampTimingAndTrimRetainAbsoluteSpeedBoundaries() {
        val c=clip(listOf(SpeedPoint(2000,0.5),SpeedPoint(4000,2.0),SpeedPoint(6000,1.0)))
        assertEquals(7000L,c.durationMs)
        assertEquals(4000L,c.sourceAt(4000))
        assertEquals(5000L,c.sourceAt(4500))
        assertEquals(3000L,c.trim(4000,8000).durationMs)
        assertEquals(c.durationMs,c.split(4500).sumOf {it.durationMs})
    }
    @Test fun keyframesInterpolateAndClampAtEdges() {
        val c=clip(emptyList()).copy(keyframes=listOf(TransformKeyframe(2000,x=-1f,opacity=0f),TransformKeyframe(8000,x=1f,scale=2f,opacity=1f)))
        val middle=Timeline.keyframe(c,5000)
        assertEquals(0f,middle.x,0.0001f);assertEquals(1.5f,middle.scale,0.0001f);assertEquals(0.5f,middle.opacity,0.0001f)
        assertEquals(-1f,Timeline.keyframe(c,0).x,0.0001f)
        assertEquals(1f,Timeline.keyframe(c,10000).x,0.0001f)
    }
    @Test fun snappingOnlyMovesWithinTolerance() {
        assertEquals(1000L,Timeline.snap(1080,listOf(0,1000,2000)))
        assertEquals(1300L,Timeline.snap(1300,listOf(0,1000,2000)))
    }
    @Test fun projectDurationRemainsBoundedByMainTrack() {
        val c=clip(emptyList())
        val p=Project(listOf(c),audio=listOf(AudioClip(uri="a",name="a",sourceDurationMs=60000)),texts=listOf(TextLayer(text="caption",endMs=60000)))
        assertEquals(6000L,p.durationMs)
    }
}
