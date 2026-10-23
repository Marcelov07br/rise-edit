package br.com.riseedit.core

import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionSmoothingTest {
    private fun shaky(count: Int) = MotionAnalysis(
        640,
        360,
        (0 until count).map {
            MotionSample(
                it * 33L,
                Pose(
                    it * 0.5 + 6 * sin(it * 1.7),
                    4 * sin(it * 2.3),
                    0.01 * sin(it * 1.1)
                )
            )
        }
    )

    @Test fun slidingMeanMatchesBruteForce() {
        val times = LongArray(200) { it * 33L + (it % 3) }
        val values = DoubleArray(200) { sin(it * 0.7) * it }
        val fast = SlidingWindow.mean(times, values, 250.0)
        values.indices.forEach { i ->
            val brute = values.indices.filter {
                abs(times[it] - times[i]) <= 250
            }.map { values[it] }.average()
            assertEquals(brute, fast[i], 1e-9)
        }
    }

    @Test fun slidingMaxMatchesBruteForce() {
        val times = LongArray(200) { it * 40L }
        val values = DoubleArray(200) { sin(it * 0.37) + it % 7 }
        val fast = SlidingWindow.max(times, values, 300.0)
        values.indices.forEach { i ->
            assertEquals(
                values.indices.filter {
                    abs(times[it] - times[i]) <= 300
                }.maxOf { values[it] },
                fast[i],
                0.0
            )
        }
    }

    @Test fun smoothingReducesJitter() {
        val analysis = shaky(300)
        val result = MotionSmoother.corrections(
            analysis,
            Stabilization(true, strength = 1.0, smoothSeconds = 0.6, maxZoom = 1.25),
            0,
            10_000
        )
        val stabilized = result.zip(analysis.samples).map { (c, s) -> c.pose.compose(s.pose) }
        fun jitter(xs: List<Double>) = xs.zipWithNext { a, b -> abs(b - a) }.average()
        assertTrue(jitter(stabilized.map { it.y }) < jitter(analysis.samples.map { it.pose.y }) / 3)
    }

    @Test fun zoomCoversEveryRequirementAndStaysWithinLimit() {
        val analysis = shaky(300)
        val settings = Stabilization(true, strength = 1.0, smoothSeconds = 0.6, maxZoom = 1.15)
        val result = MotionSmoother.corrections(analysis, settings, 0, 10_000)
        result.forEach {
            assertTrue(it.zoom + 1e-9 >= MotionSmoother.requiredZoom(it.pose, 640, 360))
            assertTrue(it.zoom in 1.0..1.15)
        }
    }

    @Test fun zoomIsTimeVaryingAndEased() {
        val samples =
            (0..300).map { i ->
                val shake = if (i in 140..160) 20 * sin(i * 2.0) else 0.0
                MotionSample(i * 33L, Pose(shake, 0.0))
            }
        val result = MotionSmoother.corrections(
            MotionAnalysis(640, 360, samples),
            Stabilization(true, strength = 1.0, maxZoom = 1.2),
            0,
            10_000
        )
        assertEquals(1.0, result.first().zoom, 1e-9)
        assertEquals(1.0, result.last().zoom, 1e-9)
        assertTrue(result[150].zoom > 1.01)
        val maxStep = result.zipWithNext { a, b -> abs(b.zoom - a.zoom) }.max()
        assertTrue("zoom must ease, step=$maxStep", maxStep < (result[150].zoom - 1.0) / 4)
    }

    @Test fun shortTrackingLossIsBridgedWithoutReset() {
        val deltas = (1..30).map {
            FrameDelta(
                it * 33L,
                if (it in
                    10..14
                ) {
                    null
                } else {
                    Pose(2.0, 0.0)
                }
            )
        }
        val samples = MotionTracking.trajectory(0, deltas)
        assertTrue(samples.all { it.segment == 0 })
        assertEquals(60.0, samples.last().pose.x, 1e-9)
    }

    @Test fun bridgeInterpolatesVelocity() {
        val deltas =
            listOf(
                FrameDelta(33, Pose(1.0)),
                FrameDelta(66, null),
                FrameDelta(99, null),
                FrameDelta(132, Pose(4.0))
            )
        val filled = MotionTracking.bridge(0, deltas)
        assertEquals(2.0, filled[1]!!.x, 1e-9)
        assertEquals(3.0, filled[2]!!.x, 1e-9)
    }

    @Test fun longTrackingLossStartsNewSegment() {
        val deltas = (1..100).map { FrameDelta(it * 33L, if (it in 20..70) null else Pose(1.0)) }
        val samples = MotionTracking.trajectory(0, deltas)
        assertEquals(0, samples[19].segment)
        assertTrue(samples.last().segment > 0)
    }

    @Test fun warpVelocitiesFollowTrajectorySlope() {
        val samples = (0..50).map { MotionSample(it * 33L, Pose(it * 2.0, -it * 0.5, 0.001 * it)) }
        val velocities = MotionSmoother.warpVelocities(samples, 640, 360)
        // Central difference of a linear trajectory: one step per sample.
        (1 until samples.size - 1).forEach {
            assertEquals(2.0, velocities[it].x, 1e-9)
            assertEquals(-0.5, velocities[it].y, 1e-9)
            assertEquals(0.001, velocities[it].angle, 1e-9)
        }
        // Trajectory ends have no neighbor on one side.
        assertEquals(0.0, velocities.first().x, 1e-9)
        assertEquals(0.0, velocities.last().x, 1e-9)
    }

    @Test fun warpVelocitiesZeroAtSegmentBordersAndCapped() {
        val shaky = shaky(40).let { a ->
            // Force a tracker reset in the middle.
            MotionAnalysis(
                a.width,
                a.height,
                a.samples.mapIndexed { i, s ->
                    if (i >= 20) s.copy(segment = 1) else s
                }
            )
        }
        val velocities = MotionSmoother.warpVelocities(shaky.samples, shaky.width, shaky.height)
        assertEquals(0.0, velocities[19].x, 1e-9)
        assertEquals(0.0, velocities[20].x, 1e-9)
        // A tracking spike 100× the frame is capped at 5% of it (shows up in the neighbors).
        val spike = (0..4).map {
            MotionSample(it * 33L, Pose(if (it == 2) 1e5 else it * 1.0, 0.0))
        }
        val capped = MotionSmoother.warpVelocities(spike, 640, 360)
        assertTrue(kotlin.math.abs(capped[1].x) <= 0.05 * 640 + 1e-9)
        assertTrue(kotlin.math.abs(capped[3].x) <= 0.05 * 640 + 1e-9)
    }

    @Test fun warpRaisesRequiredZoomButStaysWithinLimit() {
        val analysis = shaky(300)
        val rigid = MotionSmoother.corrections(
            analysis,
            Stabilization(true, strength = 1.0, maxZoom = 1.25),
            0,
            10_000
        )
        val warp = MotionSmoother.corrections(
            analysis,
            Stabilization(
                true,
                strength = 1.0,
                maxZoom = 1.25,
                method = StabilizeMethod.WARP,
                rollingShutter = 1.0
            ),
            0,
            10_000
        )
        warp.forEachIndexed { i, c ->
            assertTrue(c.zoom + 1e-9 >= MotionSmoother.requiredZoom(c.pose, 640, 360))
            assertTrue(c.zoom in 1.0..1.25)
            assertTrue(c.velocity == Pose() || c.zoom >= rigid[i].zoom - 1e-9)
        }
    }

    @Test fun rigidMethodKeepsVelocitiesZero() {
        val analysis = shaky(100)
        val result = MotionSmoother.corrections(
            analysis,
            Stabilization(true, strength = 1.0),
            0,
            10_000
        )
        assertTrue(result.all { it.velocity == Pose() })
    }

    @Test fun velocityInterpolatesBetweenSamples() {
        val samples = (0..10).map { MotionSample(it * 33L, Pose(it * 3.0, 0.0)) }
        val corrections = MotionSmoother.corrections(
            MotionAnalysis(640, 360, samples),
            Stabilization(true, method = StabilizeMethod.WARP),
            0,
            10_000
        )
        val midway = MotionSmoother.at(corrections, 5 * 33L + 16)
        assertEquals(3.0, midway.velocity.x, 1e-9)
    }
}
