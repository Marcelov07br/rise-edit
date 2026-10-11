package br.com.riseedit.core

import java.util.UUID
import kotlin.math.roundToLong

enum class MotionGoal { SMOOTH, FIXED }

data class Stabilization(
    val enabled: Boolean = false,
    val goal: MotionGoal = MotionGoal.SMOOTH,
    val strength: Double = 0.75,
    val smoothSeconds: Double = 0.6,
    val maxZoom: Double = 1.10
) {
    init {
        require(strength in 0.0..1.0)
        require(smoothSeconds in 0.1..2.0)
        require(maxZoom in 1.0..1.25)
    }
}

data class Clip(
    val id: String = UUID.randomUUID().toString(),
    val uri: String,
    val name: String,
    val sourceDurationMs: Long,
    val startMs: Long = 0,
    val endMs: Long = sourceDurationMs,
    val stabilization: Stabilization = Stabilization(),
    val speedPoints: List<SpeedPoint> = emptyList(),
    val volume: Float = 1f,
    val fadeMs: Long = 0,
    val adjustments: Adjustments = Adjustments(),
    val keyframes: List<TransformKeyframe> = emptyList()
) {
    init {
        require(sourceDurationMs > 0 && startMs >= 0 && endMs <= sourceDurationMs && endMs > startMs)
        require(volume in 0f..2f && fadeMs >= 0)
        require(speedPoints.zipWithNext().all { (a,b) -> a.sourceMs < b.sourceMs })
    }
    val sourceLengthMs get() = endMs - startMs
    fun speedAt(sourceMs: Long) = speedPoints.lastOrNull { it.sourceMs <= sourceMs }?.speed ?: 1.0
    private fun intervals() = (listOf(startMs) + speedPoints.map { it.sourceMs }.filter { it in (startMs + 1) until endMs } + endMs).distinct().sorted()
    val durationMs get() = intervals().zipWithNext().sumOf { (a, b) -> (b - a) / speedAt(a) }.roundToLong().coerceAtLeast(1)
    fun sourceAt(outputMs: Long): Long {
        var remaining = outputMs.toDouble().coerceIn(0.0, durationMs.toDouble())
        for ((a, b) in intervals().zipWithNext()) {
            val duration = (b - a) / speedAt(a)
            if (remaining <= duration) return (a + remaining * speedAt(a)).roundToLong().coerceIn(startMs, endMs)
            remaining -= duration
        }
        return endMs
    }
    fun trim(start: Long, end: Long) = copy(startMs = start, endMs = end)
    fun split(relativeMs: Long): List<Clip> {
        require(relativeMs in 1 until durationMs)
        val cut = sourceAt(relativeMs)
        require(cut in (startMs + 1) until endMs)
        return listOf(copy(endMs = cut), copy(id = UUID.randomUUID().toString(), startMs = cut))
    }
}

data class SpeedPoint(val sourceMs: Long, val speed: Double) { init { require(sourceMs >= 0 && speed in 0.25..4.0) } }
data class Adjustments(val brightness: Float = 0f, val contrast: Float = 0f, val saturation: Float = 0f, val warmth: Float = 0f)
data class TransformKeyframe(val sourceMs: Long, val x: Float = 0f, val y: Float = 0f, val scale: Float = 1f, val rotation: Float = 0f, val opacity: Float = 1f)
data class AudioClip(val id: String = UUID.randomUUID().toString(), val uri: String, val name: String,
    val sourceDurationMs: Long, val offsetMs: Long = 0, val startMs: Long = 0, val endMs: Long = sourceDurationMs,
    val track: Int = 0, val volume: Float = 0.7f, val fadeMs: Long = 500) {
    init { require(offsetMs >= 0 && track in 0..2 && startMs >= 0 && endMs > startMs && endMs <= sourceDurationMs) }
    val durationMs get() = endMs - startMs
}
data class VideoOverlay(val id: String = UUID.randomUUID().toString(), val clip: Clip, val offsetMs: Long = 0,
    val track: Int = 0, val x: Float = 0.55f, val y: Float = 0.55f, val scale: Float = 0.35f, val opacity: Float = 1f) {
    init { require(offsetMs >= 0 && track in 0..1) }
}
data class TextLayer(val id: String = UUID.randomUUID().toString(), val text: String, val startMs: Long = 0,
    val endMs: Long = 3000, val x: Float = 0f, val y: Float = -0.65f, val size: Float = 1f,
    val color: Int = -1, val animated: Boolean = true) { init { require(endMs > startMs && startMs >= 0) } }
data class ExportSettings(val height: Int = 720, val fps: Int = 30, val bitrate: Int = 8_000_000) {
    init { require(height in listOf(480, 720, 1080) && fps in listOf(24, 30, 60) && bitrate in 2_000_000..24_000_000) }
}
data class Project(val clips: List<Clip> = emptyList(), val id: String = UUID.randomUUID().toString(),
    val name: String = "Novo projeto", val modifiedAt: Long = System.currentTimeMillis(), val favorite: Boolean = false,
    val audio: List<AudioClip> = emptyList(), val texts: List<TextLayer> = emptyList(), val overlays: List<VideoOverlay> = emptyList(),
    val aspectRatio: Float = 0f, val export: ExportSettings = ExportSettings()) {
    val durationMs get() = clips.sumOf { it.durationMs }
    fun replace(index: Int, clip: Clip) = copy(clips = clips.toMutableList().apply { set(index, clip) })
    fun split(index: Int, relativeMs: Long) = copy(clips = clips.toMutableList().apply {
        val parts = get(index).split(relativeMs)
        removeAt(index); addAll(index, parts)
    })
    fun move(index: Int, delta: Int): Project {
        val target = index + delta
        if (target !in clips.indices) return this
        return copy(clips = clips.toMutableList().apply { add(target, removeAt(index)) })
    }
}
