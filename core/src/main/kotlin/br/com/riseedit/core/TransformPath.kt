package br.com.riseedit.core

import br.com.riseedit.core.keys.Easing
import br.com.riseedit.core.keys.FrameGrid
import br.com.riseedit.core.keys.Key
import br.com.riseedit.core.keys.ParameterTrack
import br.com.riseedit.core.keys.TimeDomain
import br.com.riseedit.core.keys.TrackValue
import br.com.riseedit.core.keys.TransformTracks

/**
 * Keyframe interpolation shared by preview and export.
 *
 * Whole-transform keyframes are evaluated through per-parameter [TransformTracks]
 * (see [TransformTracks.fromLegacy]): position follows the Smooth (Catmull-Rom Hermite) path, scale,
 * rotation and opacity the smoothstep cubic Bézier; rotation takes the shortest angular path.
 * Key identity is frame-quantized ([FrameGrid]) instead of a millisecond tolerance.
 * When [tracks] is present it wins; otherwise [keys] are migrated on the fly.
 */
object TransformPath {
    fun at(
        keys: List<TransformKeyframe>,
        timeMs: Long,
        base: TransformKeyframe = TransformKeyframe(timeMs),
        domain: TimeDomain = TimeDomain.LOCAL_OUTPUT,
        tracks: TransformTracks? = null
    ): TransformKeyframe {
        val resolved = tracks ?: if (keys.isEmpty()) {
            return base.copy(sourceMs = timeMs)
        } else {
            TransformTracks.fromLegacy(keys, domain)
        }
        if (resolved.x.keys.isEmpty() &&
            resolved.y.keys.isEmpty() &&
            resolved.scale.keys.isEmpty() &&
            resolved.rotation.keys.isEmpty() &&
            resolved.opacity.keys.isEmpty()
        ) {
            return base.copy(sourceMs = timeMs)
        }
        return resolved.at(timeMs, base)
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

    /** Flat key list derived from tracks (for diamonds / legacy UI). */
    fun flatten(tracks: TransformTracks): List<TransformKeyframe> {
        val times = (
            tracks.x.keys.map { it.timeMs } + tracks.y.keys.map { it.timeMs } +
                tracks.scale.keys.map { it.timeMs } + tracks.rotation.keys.map { it.timeMs } +
                tracks.opacity.keys.map { it.timeMs }
            ).distinct().sorted()
        return times.map { t -> tracks.at(t, TransformKeyframe(t)) }
    }
}

/** Edits that keep [Clip.keyframes] and [Clip.tracks] aligned. */
object KeyframeEdits {
    fun resolve(clip: Clip): TransformTracks =
        clip.tracks ?: TransformTracks.fromLegacy(clip.keyframes, TimeDomain.SOURCE)

    fun resolve(overlay: VideoOverlay): TransformTracks = overlay.tracks
        ?: TransformTracks.fromLegacy(overlay.keyframes, TimeDomain.LOCAL_OUTPUT)

    fun resolve(text: TextLayer): TransformTracks =
        text.tracks ?: TransformTracks.fromLegacy(text.keyframes, TimeDomain.LOCAL_OUTPUT)

    fun setEasing(
        tracks: TransformTracks,
        timeMs: Long,
        easing: Easing,
        grid: FrameGrid =
            TransformPath.DEFAULT_GRID
    ): TransformTracks {
        fun <T> ParameterTrack<T>.withEasing(): ParameterTrack<T> {
            val existing = keys.firstOrNull { grid.sameFrame(it.timeMs, timeMs) } ?: return this
            return copy(
                keys = keys.map {
                    if (it === existing) Key(it.timeMs, it.value, easing) else it
                }
            )
        }
        return tracks.copy(
            x = tracks.x.withEasing(),
            y = tracks.y.withEasing(),
            scale = tracks.scale.withEasing(),
            rotation = tracks.rotation.withEasing(),
            opacity = tracks.opacity.withEasing()
        )
    }

    fun remove(
        tracks: TransformTracks,
        timeMs: Long,
        grid: FrameGrid = TransformPath.DEFAULT_GRID
    ) = tracks.copy(
        x = tracks.x.remove(timeMs, grid),
        y = tracks.y.remove(timeMs, grid),
        scale = tracks.scale.remove(timeMs, grid),
        rotation = tracks.rotation.remove(timeMs, grid),
        opacity = tracks.opacity.remove(timeMs, grid)
    )

    fun easingAt(
        tracks: TransformTracks,
        timeMs: Long,
        grid: FrameGrid = TransformPath.DEFAULT_GRID
    ): Easing? = tracks.x.keys.firstOrNull { grid.sameFrame(it.timeMs, timeMs) }?.easing
        ?: tracks.scale.keys.firstOrNull { grid.sameFrame(it.timeMs, timeMs) }?.easing

    fun times(tracks: TransformTracks): List<Long> = (
        tracks.x.keys.map { it.timeMs } + tracks.y.keys.map { it.timeMs } +
            tracks.scale.keys.map { it.timeMs } + tracks.rotation.keys.map { it.timeMs } +
            tracks.opacity.keys.map { it.timeMs }
        ).distinct().sorted()

    fun applyToClip(clip: Clip, tracks: TransformTracks): Clip {
        val flat = TransformPath.flatten(tracks)
        return clip.copy(keyframes = flat, tracks = tracks.takeUnless { flat.isEmpty() })
    }

    fun applyToOverlay(overlay: VideoOverlay, tracks: TransformTracks): VideoOverlay {
        val flat = TransformPath.flatten(tracks)
        return overlay.copy(keyframes = flat, tracks = tracks.takeUnless { flat.isEmpty() })
    }

    fun applyToText(text: TextLayer, tracks: TransformTracks): TextLayer {
        val flat = TransformPath.flatten(tracks)
        return text.copy(keyframes = flat, tracks = tracks.takeUnless { flat.isEmpty() })
    }
}
