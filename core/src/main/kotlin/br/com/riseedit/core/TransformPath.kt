package br.com.riseedit.core

import br.com.riseedit.core.keys.FrameGrid
import br.com.riseedit.core.keys.TimeDomain
import br.com.riseedit.core.keys.TrackValue
import br.com.riseedit.core.keys.TransformTracks

/**
 * Keyframe interpolation shared by preview and export.
 *
 * Whole-transform keyframes are evaluated through per-parameter [br.com.riseedit.core.keys.ParameterTrack]s
 * (see [TransformTracks.fromLegacy]): position follows the Smooth (Catmull-Rom Hermite) path, scale,
 * rotation and opacity the smoothstep cubic Bézier; rotation takes the shortest angular path.
 * Key identity is frame-quantized ([FrameGrid]) instead of a millisecond tolerance.
 */
object TransformPath {
    fun at(
        keys: List<TransformKeyframe>,
        timeMs: Long,
        base: TransformKeyframe = TransformKeyframe(timeMs),
        domain: TimeDomain = TimeDomain.LOCAL_OUTPUT
    ): TransformKeyframe {
        if (keys.isEmpty()) return base.copy(sourceMs = timeMs)
        return TransformTracks.fromLegacy(keys, domain).at(timeMs, base)
    }

    /** Smooth ease-in-out (cubic smoothstep). */
    fun ease(u: Double): Double = u * u * (3 - 2 * u)

    /** Signed smallest rotation from [from] to [to], in degrees within (-180, 180]. */
    fun shortestDelta(from: Float, to: Float): Float = TrackValue.Angle.delta(from, to).toFloat()

    /** True if [keys] has a keyframe on the same frame as [timeMs]. */
    fun hasKeyAt(keys: List<TransformKeyframe>, timeMs: Long, grid: FrameGrid = DEFAULT_GRID) =
        keys.any { grid.sameFrame(it.sourceMs, timeMs) }

    /** Replaces the keyframe on [key]'s frame or inserts one on the frame boundary, keeping order. */
    fun upsert(
        keys: List<TransformKeyframe>,
        key: TransformKeyframe,
        grid: FrameGrid = DEFAULT_GRID
    ): List<TransformKeyframe> {
        val existing = keys.firstOrNull { grid.sameFrame(it.sourceMs, key.sourceMs) }
        val time = existing?.sourceMs ?: grid.quantize(key.sourceMs)
        return (
            keys.filterNot {
                it === existing
            } + key.copy(sourceMs = time)
            ).sortedBy { it.sourceMs }
    }

    fun remove(
        keys: List<TransformKeyframe>,
        timeMs: Long,
        grid: FrameGrid = DEFAULT_GRID
    ): List<TransformKeyframe> = keys.filterNot { grid.sameFrame(it.sourceMs, timeMs) }

    val DEFAULT_GRID = FrameGrid(30)
}
