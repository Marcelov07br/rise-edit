package br.com.riseedit.core

import br.com.riseedit.core.keys.TimeDomain
import br.com.riseedit.core.keys.TransformTracks

/**
 * Applies a direct-manipulation result (drag/pinch/rotate) to an item. Without keyframes the
 * static transform changes; with keyframes the value is keyed at the playhead (auto-key).
 */
object TransformEdits {
    fun clip(clip: Clip, sourceMs: Long, t: TransformKeyframe): Clip {
        val keys = if (clip.keyframes.size <= 1) {
            listOf(t.copy(sourceMs = clip.keyframes.firstOrNull()?.sourceMs ?: sourceMs))
        } else {
            TransformPath.upsert(clip.keyframes, t.copy(sourceMs = sourceMs))
        }
        return clip.copy(
            keyframes = keys,
            tracks = syncTracks(clip.tracks, keys, TimeDomain.SOURCE)
        )
    }

    fun overlay(overlay: VideoOverlay, localMs: Long, t: TransformKeyframe): VideoOverlay {
        if (overlay.keyframes.isEmpty() && overlay.tracks == null) {
            return overlay.copy(
                x = t.x,
                y = t.y,
                scale = t.scale,
                rotation = t.rotation,
                opacity = t.opacity
            )
        }
        val keys = TransformPath.upsert(overlay.keyframes, t.copy(sourceMs = localMs))
        return overlay.copy(
            keyframes = keys,
            tracks = syncTracks(overlay.tracks, keys, TimeDomain.LOCAL_OUTPUT)
        )
    }

    fun text(text: TextLayer, localMs: Long, t: TransformKeyframe): TextLayer {
        if (text.keyframes.isEmpty() && text.tracks == null) {
            return text.copy(x = t.x, y = t.y, size = t.scale, rotation = t.rotation)
        }
        val keys = TransformPath.upsert(text.keyframes, t.copy(sourceMs = localMs))
        return text.copy(
            keyframes = keys,
            tracks = syncTracks(text.tracks, keys, TimeDomain.LOCAL_OUTPUT)
        )
    }

    /** Adds a keyframe with the current value at [timeMs], or removes the one already there. */
    fun toggle(
        keys: List<TransformKeyframe>,
        timeMs: Long,
        current: TransformKeyframe
    ): List<TransformKeyframe> = if (TransformPath.hasKeyAt(keys, timeMs)) {
        TransformPath.remove(keys, timeMs)
    } else {
        TransformPath.upsert(keys, current.copy(sourceMs = timeMs))
    }

    fun resetClip(clip: Clip) = clip.copy(keyframes = emptyList(), tracks = null)

    fun resetOverlay(o: VideoOverlay): VideoOverlay {
        val d = VideoOverlay(clip = o.clip)
        return o.copy(
            x = d.x,
            y = d.y,
            scale = d.scale,
            rotation = 0f,
            opacity = 1f,
            keyframes = emptyList(),
            tracks = null
        )
    }

    fun resetText(t: TextLayer): TextLayer {
        val d = TextLayer(text = t.text)
        return t.copy(
            x = d.x,
            y = d.y,
            size = d.size,
            rotation = 0f,
            keyframes = emptyList(),
            tracks = null
        )
    }

    /**
     * After a legacy key list change, rebuild tracks from the values while preserving easings
     * on keys that still exist.
     */
    fun syncTracks(
        previous: TransformTracks?,
        keys: List<TransformKeyframe>,
        domain: TimeDomain
    ): TransformTracks? {
        if (previous == null) return null
        if (keys.isEmpty()) return null
        val fresh = TransformTracks.fromLegacy(keys, domain)
        val grid = TransformPath.DEFAULT_GRID
        fun keep(
            track: br.com.riseedit.core.keys.ParameterTrack<Float>,
            next: br.com.riseedit.core.keys.ParameterTrack<Float>
        ) = next.copy(
            keys = next.keys.map { key ->
                val old = track.keys.firstOrNull { grid.sameFrame(it.timeMs, key.timeMs) }
                if (old != null) key.copy(easing = old.easing) else key
            }
        )
        return TransformTracks(
            keep(previous.x, fresh.x),
            keep(previous.y, fresh.y),
            keep(previous.scale, fresh.scale),
            keep(previous.rotation, fresh.rotation),
            keep(previous.opacity, fresh.opacity)
        )
    }
}
