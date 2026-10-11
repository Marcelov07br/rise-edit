package br.com.riseedit.core

import kotlin.math.*

/** Rigid pose around the image center. Translation is expressed in analysis pixels. */
data class Pose(val x: Double = 0.0, val y: Double = 0.0, val angle: Double = 0.0) {
    fun compose(other: Pose): Pose = Pose(
        x + cos(angle) * other.x - sin(angle) * other.y,
        y + sin(angle) * other.x + cos(angle) * other.y,
        angle + other.angle
    )
    fun inverse() = Pose(-cos(angle) * x - sin(angle) * y, sin(angle) * x - cos(angle) * y, -angle)
}
data class MotionSample(val timeMs: Long, val pose: Pose, val segment: Int = 0)
data class MotionAnalysis(val width: Int, val height: Int, val samples: List<MotionSample>, val unreliableFrames: Int = 0) {
    init {
        require(width > 0 && height > 0 && samples.isNotEmpty())
        require(samples.zipWithNext().all { (a,b) -> a.timeMs < b.timeMs })
    }
}
data class Correction(val timeMs: Long, val pose: Pose, val segment: Int = 0)

object MotionSmoother {
    /** Trajectory analysis is immutable. All user settings are applied afterwards. */
    fun corrections(analysis: MotionAnalysis, settings: Stabilization, startMs: Long, endMs: Long): List<Correction> {
        val samples = analysis.samples.filter { it.timeMs in startMs..endMs }.ifEmpty {
            listOf(analysis.samples.minBy { abs(it.timeMs - startMs) })
        }
        return samples.map { sample ->
            val neighborhood = samples.filter {
                it.segment == sample.segment && abs(it.timeMs - sample.timeMs) <= settings.smoothSeconds * 1000.0
            }
            val target = if (settings.goal == MotionGoal.FIXED) {
                samples.first { it.segment == sample.segment }.pose
            } else Pose(neighborhood.map { it.pose.x }.average(), neighborhood.map { it.pose.y }.average(), neighborhood.map { it.pose.angle }.average())
            val raw = target.compose(sample.pose.inverse())
            val desired = Pose(raw.x * settings.strength, raw.y * settings.strength, raw.angle * settings.strength)
            Correction(sample.timeMs, bounded(desired, analysis.width, analysis.height, settings.maxZoom), sample.segment)
        }
    }

    /** Reduce correction instead of exceeding the chosen zoom or introducing black borders. */
    fun bounded(pose: Pose, width: Int, height: Int, zoom: Double): Pose {
        fun fits(factor: Double): Boolean {
            val p = Pose(pose.x * factor, pose.y * factor, pose.angle * factor)
            return requiredZoom(p, width, height) <= zoom
        }
        if (fits(1.0)) return pose
        var lo = 0.0; var hi = 1.0
        repeat(30) { val mid = (lo + hi) / 2; if (fits(mid)) lo = mid else hi = mid }
        return Pose(pose.x * lo, pose.y * lo, pose.angle * lo)
    }

    fun requiredZoom(pose: Pose, width: Int, height: Int): Double {
        val c = cos(pose.angle); val s = sin(pose.angle)
        val x = c * pose.x + s * pose.y; val y = -s * pose.x + c * pose.y
        return max(1.0, max((abs(c) * width + abs(s) * height + 2 * abs(x)) / width,
            (abs(c) * height + abs(s) * width + 2 * abs(y)) / height))
    }

    fun interpolate(corrections: List<Correction>, timeMs: Long): Pose {
        require(corrections.isNotEmpty())
        if (timeMs <= corrections.first().timeMs) return corrections.first().pose
        if (timeMs >= corrections.last().timeMs) return corrections.last().pose
        val index = corrections.binarySearchBy(timeMs) { it.timeMs }
        if (index >= 0) return corrections[index].pose
        val upper = -index - 1
        val a = corrections[upper - 1]; val b = corrections[upper]
        if (a.segment != b.segment) return a.pose
        val t = (timeMs - a.timeMs).toDouble() / (b.timeMs - a.timeMs)
        return Pose(a.pose.x + (b.pose.x - a.pose.x) * t, a.pose.y + (b.pose.y - a.pose.y) * t, a.pose.angle + (b.pose.angle - a.pose.angle) * t)
    }
}
