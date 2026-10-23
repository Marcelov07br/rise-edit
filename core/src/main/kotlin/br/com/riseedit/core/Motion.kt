package br.com.riseedit.core

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Rigid pose around the image center. Translation is expressed in analysis pixels. */
data class Pose(val x: Double = 0.0, val y: Double = 0.0, val angle: Double = 0.0) {
    fun compose(other: Pose): Pose = Pose(
        x + cos(angle) * other.x - sin(angle) * other.y,
        y + sin(angle) * other.x + cos(angle) * other.y,
        angle + other.angle
    )

    fun inverse() = Pose(-cos(angle) * x - sin(angle) * y, sin(angle) * x - cos(angle) * y, -angle)

    fun scaled(factor: Double) = Pose(x * factor, y * factor, angle * factor)
}

data class MotionSample(val timeMs: Long, val pose: Pose, val segment: Int = 0)

data class MotionAnalysis(
    val width: Int,
    val height: Int,
    val samples: List<MotionSample>,
    val unreliableFrames: Int = 0
) {
    init {
        require(width > 0 && height > 0 && samples.isNotEmpty())
        require(samples.zipWithNext().all { (a, b) -> a.timeMs < b.timeMs })
    }
}

/**
 * Correction pose plus the crop zoom needed at this instant (eased, never above maxZoom).
 * [velocity] is the raw trajectory slope at this sample (analysis pixels per sample): the
 * frame-to-frame motion the sensor captures progressively, i.e. the rolling-shutter/wobble
 * source that the per-line WARP correction undoes. Zero at segment borders.
 */
data class Correction(
    val timeMs: Long,
    val pose: Pose,
    val segment: Int = 0,
    val zoom: Double = 1.0,
    val velocity: Pose = Pose()
)

/** Frame-to-frame motion measured by an analyzer; [delta] is null when tracking failed. */
data class FrameDelta(val timeMs: Long, val delta: Pose?)

/** Progress events of a motion analysis, independent of any UI toolkit. */
sealed interface MotionAnalysisEvent {
    data class Progress(val fraction: Float) : MotionAnalysisEvent

    data class Finished(val analysis: MotionAnalysis, val fromCache: Boolean) : MotionAnalysisEvent
}

object MotionSmoother {
    /** Trajectory analysis is immutable. All user settings are applied afterwards. O(n). */
    fun corrections(
        analysis: MotionAnalysis,
        settings: Stabilization,
        startMs: Long,
        endMs: Long
    ): List<Correction> {
        val samples =
            analysis.samples.filter { it.timeMs in startMs..endMs }.ifEmpty {
                listOf(analysis.samples.minBy { abs(it.timeMs - startMs) })
            }
        val radiusMs = settings.smoothSeconds * 1000.0
        val velocities =
            if (settings.warpAmount > 0.0) {
                warpVelocities(samples, analysis.width, analysis.height)
            } else {
                List(samples.size) { Pose() }
            }
        val poses = ArrayList<Correction>(samples.size)
        segmentRanges(samples).forEach { range ->
            val part = samples.subList(range.first, range.last + 1)
            val targets =
                if (settings.goal == MotionGoal.FIXED) {
                    List(part.size) { part.first().pose }
                } else {
                    smoothPoses(part, radiusMs)
                }
            part.forEachIndexed { i, sample ->
                val index = range.first + i
                val desired = targets[i].compose(sample.pose.inverse()).scaled(settings.strength)
                val pose = bounded(desired, analysis.width, analysis.height, settings.maxZoom)
                poses += Correction(sample.timeMs, pose, sample.segment, 1.0, velocities[index])
            }
        }
        val zooms =
            easedZoom(
                poses,
                analysis.width,
                analysis.height,
                settings.maxZoom,
                max(ZOOM_EASE_MS, radiusMs),
                settings.warpAmount
            )
        return poses.mapIndexed { i, c -> c.copy(zoom = zooms[i]) }
    }

    /**
     * Crop zoom over time: a running maximum of the required zoom over ±[radiusMs], then a box
     * average over the same radius. The average of maxima that each cover the instant is never
     * below the instant's requirement, so borders stay hidden while transitions ease in and out.
     * [warpAmount] widens the requirement by the per-line warp reach so WARP never shows borders.
     */
    fun easedZoom(
        corrections: List<Correction>,
        width: Int,
        height: Int,
        maxZoom: Double,
        radiusMs: Double,
        warpAmount: Double = 0.0
    ): DoubleArray {
        val times = LongArray(corrections.size) { corrections[it].timeMs }
        val required =
            DoubleArray(corrections.size) {
                warpExtras(corrections[it].velocity, width, height, warpAmount).let { e ->
                    requiredZoom(corrections[it].pose, width, height, e.first, e.second)
                }
            }
        val peaks = SlidingWindow.max(times, required, radiusMs)
        val eased = SlidingWindow.mean(times, peaks, radiusMs)
        return DoubleArray(eased.size) { eased[it].coerceIn(1.0, max(1.0, maxZoom)) }
    }

    /**
     * Raw trajectory slope per sample (central difference, analysis pixels/sample), zeroed at
     * segment borders where the tracker reset makes the difference meaningless. Magnitudes are
     * capped at [VELOCITY_CAP] of the frame per sample so a single bad estimate cannot tear the
     * warp; RANSAC already rejects most outliers, this is the second line of defense.
     */
    internal fun warpVelocities(
        samples: List<MotionSample>,
        width: Int,
        height: Int
    ): List<Pose> = List(samples.size) { i ->
        val s = samples[i]
        val prev = samples.getOrNull(i - 1)
        val next = samples.getOrNull(i + 1)
        if (prev == null ||
            next == null ||
            prev.segment != s.segment ||
            next.segment != s.segment
        ) {
            Pose()
        } else {
            Pose(
                ((next.pose.x - prev.pose.x) / 2).coerceIn(-VELOCITY_CAP * width, VELOCITY_CAP * width),
                ((next.pose.y - prev.pose.y) / 2).coerceIn(-VELOCITY_CAP * height, VELOCITY_CAP * height),
                ((next.pose.angle - prev.pose.angle) / 2).coerceIn(-VELOCITY_CAP, VELOCITY_CAP)
            )
        }
    }

    /**
     * Max per-line warp displacement in pixels: the RS shear moves the top/bottom edge by
     * velocity·amount (shear is linear over the ±0.5 line span), the angular term by
     * angle·amount·height/2 (small-angle rotation ≈ horizontal shear).
     */
    internal fun warpExtras(
        velocity: Pose,
        width: Int,
        height: Int,
        amount: Double
    ): Pair<Double, Double> =
        (
            abs(velocity.x) * amount + abs(velocity.angle) * amount * height / 2
            ) to (abs(velocity.y) * amount)

    /** Triangular (two-pass box) low-pass of the trajectory, linear in the number of samples. */
    private fun smoothPoses(part: List<MotionSample>, radiusMs: Double): List<Pose> {
        val times = LongArray(part.size) { part[it].timeMs }
        fun channel(value: (Pose) -> Double): DoubleArray {
            val raw = DoubleArray(part.size) { value(part[it].pose) }
            return SlidingWindow.mean(
                times,
                SlidingWindow.mean(times, raw, radiusMs / 2),
                radiusMs / 2
            )
        }
        val x = channel { it.x }
        val y = channel { it.y }
        val angle = channel { it.angle }
        return List(part.size) { Pose(x[it], y[it], angle[it]) }
    }

    private fun segmentRanges(samples: List<MotionSample>): List<IntRange> {
        val ranges = mutableListOf<IntRange>()
        var start = 0
        for (i in 1..samples.size) {
            if (i == samples.size || samples[i].segment != samples[start].segment) {
                ranges += start until i
                start = i
            }
        }
        return ranges
    }

    /** Reduce correction instead of exceeding the chosen zoom or introducing black borders. */
    fun bounded(pose: Pose, width: Int, height: Int, zoom: Double): Pose {
        fun fits(factor: Double) = requiredZoom(pose.scaled(factor), width, height) <= zoom
        if (fits(1.0)) return pose
        var lo = 0.0
        var hi = 1.0
        repeat(BISECTION_STEPS) {
            val mid = (lo + hi) / 2
            if (fits(mid)) lo = mid else hi = mid
        }
        return pose.scaled(lo)
    }

    fun requiredZoom(
        pose: Pose,
        width: Int,
        height: Int,
        extraX: Double = 0.0,
        extraY: Double = 0.0
    ): Double {
        val c = cos(pose.angle)
        val s = sin(pose.angle)
        val x = c * pose.x + s * pose.y
        val y = -s * pose.x + c * pose.y
        return max(
            1.0,
            max(
                (abs(c) * width + abs(s) * height + 2 * abs(x) + 2 * extraX) / width,
                (abs(c) * height + abs(s) * width + 2 * abs(y) + 2 * extraY) / height
            )
        )
    }

    fun interpolate(corrections: List<Correction>, timeMs: Long): Pose =
        at(corrections, timeMs).pose

    fun zoomAt(corrections: List<Correction>, timeMs: Long): Double = at(corrections, timeMs).zoom

    /** Correction at [timeMs], linearly interpolated inside a segment. */
    fun at(corrections: List<Correction>, timeMs: Long): Correction {
        require(corrections.isNotEmpty())
        if (timeMs <= corrections.first().timeMs) return corrections.first()
        if (timeMs >= corrections.last().timeMs) return corrections.last()
        val index = corrections.binarySearchBy(timeMs) { it.timeMs }
        if (index >= 0) return corrections[index]
        val upper = -index - 1
        val a = corrections[upper - 1]
        val b = corrections[upper]
        if (a.segment != b.segment) return a
        val t = (timeMs - a.timeMs).toDouble() / (b.timeMs - a.timeMs)
        fun mix(p: Double, q: Double) = p + (q - p) * t
        val pose =
            Pose(mix(a.pose.x, b.pose.x), mix(a.pose.y, b.pose.y), mix(a.pose.angle, b.pose.angle))
        val velocity = Pose(
            mix(a.velocity.x, b.velocity.x),
            mix(a.velocity.y, b.velocity.y),
            mix(a.velocity.angle, b.velocity.angle)
        )
        return Correction(timeMs, pose, a.segment, mix(a.zoom, b.zoom), velocity)
    }

    private const val BISECTION_STEPS = 30
    private const val ZOOM_EASE_MS = 500.0

    /** Per-sample trajectory slope cap, as a fraction of the frame per sample. */
    internal const val VELOCITY_CAP = 0.05
}

/** Builds a continuous trajectory from frame deltas, bridging short tracking losses. */
object MotionTracking {
    /** Gaps longer than this are treated as cuts and start a new segment. */
    const val MAX_BRIDGE_MS = 1000L

    fun trajectory(
        firstTimeMs: Long,
        deltas: List<FrameDelta>,
        maxBridgeMs: Long = MAX_BRIDGE_MS
    ): List<MotionSample> {
        val filled = bridge(firstTimeMs, deltas, maxBridgeMs)
        val samples = ArrayList<MotionSample>(deltas.size + 1)
        samples += MotionSample(firstTimeMs, Pose(), 0)
        var pose = Pose()
        var segment = 0
        filled.forEachIndexed { i, delta ->
            if (delta == null) {
                segment++
                pose = Pose()
            } else {
                pose = delta.compose(pose)
            }
            samples += MotionSample(deltas[i].timeMs, pose, segment)
        }
        return samples
    }

    /** Missing deltas inside short gaps are interpolated between the surrounding velocities. */
    fun bridge(
        firstTimeMs: Long,
        deltas: List<FrameDelta>,
        maxBridgeMs: Long = MAX_BRIDGE_MS
    ): List<Pose?> {
        val out = deltas.map { it.delta }.toMutableList()
        var i = 0
        while (i < deltas.size) {
            if (deltas[i].delta != null) {
                i++
                continue
            }
            val start = i
            while (i < deltas.size && deltas[i].delta == null) i++
            val gapStart = if (start == 0) firstTimeMs else deltas[start - 1].timeMs
            val gapEnd = if (i < deltas.size) deltas[i].timeMs else deltas.last().timeMs
            if (gapEnd - gapStart > maxBridgeMs) continue
            val before = deltas.getOrNull(start - 1)?.delta ?: deltas.getOrNull(i)?.delta ?: Pose()
            val after = deltas.getOrNull(i)?.delta ?: before
            val count = i - start + 1
            for (k in start until i) {
                val t = (k - start + 1).toDouble() / count
                out[k] =
                    Pose(
                        before.x + (after.x - before.x) * t,
                        before.y + (after.y - before.y) * t,
                        before.angle + (after.angle - before.angle) * t
                    )
            }
        }
        return out
    }
}

/** O(n) sliding-window statistics over time-sorted samples with a ±radius window. */
internal object SlidingWindow {
    fun mean(times: LongArray, values: DoubleArray, radiusMs: Double): DoubleArray {
        val out = DoubleArray(values.size)
        var lo = 0
        var hi = 0
        var sum = 0.0
        for (i in values.indices) {
            while (hi < values.size && times[hi] - times[i] <= radiusMs) sum += values[hi++]
            while (times[i] - times[lo] > radiusMs) sum -= values[lo++]
            out[i] = sum / (hi - lo)
        }
        return out
    }

    fun max(times: LongArray, values: DoubleArray, radiusMs: Double): DoubleArray {
        val out = DoubleArray(values.size)
        val deque = ArrayDeque<Int>()
        var hi = 0
        for (i in values.indices) {
            while (hi < values.size && times[hi] - times[i] <= radiusMs) {
                while (deque.isNotEmpty() && values[deque.last()] <= values[hi]) deque.removeLast()
                deque.addLast(hi++)
            }
            while (times[i] - times[deque.first()] > radiusMs) deque.removeFirst()
            out[i] = values[deque.first()]
        }
        return out
    }
}
