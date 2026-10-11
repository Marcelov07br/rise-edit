package br.com.riseedit.core

import kotlin.math.abs

object Timeline {
    fun snap(time: Long, boundaries: List<Long>, toleranceMs: Long = 120): Long = boundaries.minByOrNull { abs(it - time) }?.takeIf { abs(it - time) <= toleranceMs } ?: time
    fun keyframe(clip: Clip, sourceMs: Long): TransformKeyframe {
        val keys = clip.keyframes.sortedBy { it.sourceMs }
        if (keys.isEmpty()) return TransformKeyframe(sourceMs)
        if (sourceMs <= keys.first().sourceMs) return keys.first().copy(sourceMs = sourceMs)
        if (sourceMs >= keys.last().sourceMs) return keys.last().copy(sourceMs = sourceMs)
        val b = keys.first { it.sourceMs >= sourceMs }; val a = keys.last { it.sourceMs < sourceMs }
        val f = (sourceMs - a.sourceMs).toFloat() / (b.sourceMs - a.sourceMs)
        fun mix(x: Float, y: Float) = x + (y - x) * f
        return TransformKeyframe(sourceMs, mix(a.x,b.x), mix(a.y,b.y), mix(a.scale,b.scale), mix(a.rotation,b.rotation), mix(a.opacity,b.opacity))
    }
}
