package br.com.riseedit.core

/**
 * Applies a direct-manipulation result (drag/pinch/rotate) to an item. Without keyframes the
 * static transform changes; with keyframes the value is keyed at the playhead (auto-key).
 */
object TransformEdits {
    fun clip(clip: Clip, sourceMs: Long, t: TransformKeyframe): Clip =
        if (clip.keyframes.size <= 1) {
            clip.copy(
                keyframes = listOf(
                    t.copy(
                        sourceMs =
                        clip.keyframes.firstOrNull()?.sourceMs ?: sourceMs
                    )
                )
            )
        } else {
            clip.copy(keyframes = TransformPath.upsert(clip.keyframes, t.copy(sourceMs = sourceMs)))
        }

    fun overlay(overlay: VideoOverlay, localMs: Long, t: TransformKeyframe): VideoOverlay =
        if (overlay.keyframes.isEmpty()) {
            overlay.copy(
                x = t.x,
                y = t.y,
                scale = t.scale,
                rotation = t.rotation,
                opacity = t.opacity
            )
        } else {
            overlay.copy(
                keyframes = TransformPath.upsert(overlay.keyframes, t.copy(sourceMs = localMs))
            )
        }

    fun text(text: TextLayer, localMs: Long, t: TransformKeyframe): TextLayer =
        if (text.keyframes.isEmpty()) {
            text.copy(x = t.x, y = t.y, size = t.scale, rotation = t.rotation)
        } else {
            text.copy(keyframes = TransformPath.upsert(text.keyframes, t.copy(sourceMs = localMs)))
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

    fun resetClip(clip: Clip) = clip.copy(keyframes = emptyList())

    fun resetOverlay(o: VideoOverlay): VideoOverlay {
        val d = VideoOverlay(clip = o.clip)
        return o.copy(
            x = d.x,
            y = d.y,
            scale = d.scale,
            rotation = 0f,
            opacity = 1f,
            keyframes = emptyList()
        )
    }

    fun resetText(t: TextLayer): TextLayer {
        val d = TextLayer(text = t.text)
        return t.copy(x = d.x, y = d.y, size = d.size, rotation = 0f, keyframes = emptyList())
    }
}
