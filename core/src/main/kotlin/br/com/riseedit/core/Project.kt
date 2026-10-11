package br.com.riseedit.core

import java.util.UUID

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
    val stabilization: Stabilization = Stabilization()
) {
    init { require(sourceDurationMs > 0 && startMs >= 0 && endMs <= sourceDurationMs && endMs > startMs) }
    val durationMs get() = endMs - startMs
    fun trim(start: Long, end: Long) = copy(startMs = start, endMs = end)
    fun split(relativeMs: Long): List<Clip> {
        require(relativeMs in 1 until durationMs)
        val cut = startMs + relativeMs
        return listOf(copy(endMs = cut), copy(id = UUID.randomUUID().toString(), startMs = cut))
    }
}

data class Project(val clips: List<Clip> = emptyList()) {
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
