@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package br.com.riseedit.composition

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import br.com.riseedit.core.Project
import br.com.riseedit.core.VideoOverlay
import kotlin.math.min

internal fun buildOverlaySequences(
    project: Project,
    overlays: List<VideoOverlay>,
    preview: Boolean
): List<EditedMediaItemSequence> = overlays.map { overlay ->
    val clip = overlay.clip
    val sequence = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_VIDEO))
    if (overlay.offsetMs > 0) {
        sequence.addItem(TransparentGapItem.create(overlay.offsetMs, project.export.fps))
    }
    val availableMs = project.durationMs - overlay.offsetMs
    val durationMs = min(clip.durationMs, availableMs)
    val endMs = clip.sourceAt(durationMs)
    val media = MediaItem.Builder()
        .setUri(clip.effectiveUri(preview))
        .setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(clip.startMs)
                .setEndPositionMs(endMs)
                .build()
        )
        .build()
    // Media3 1.9 setSpeed waits for an audio processor even when audio is removed (#3006).
    // Overlay speed is not exposed in this release; leave its native timestamps intact.
    sequence.addItem(
        EditedMediaItem.Builder(media)
            .setDurationUs(clip.sourceDurationMs * 1000)
            .setRemoveAudio(true)
            .build()
    )
    val tailMs = project.durationMs - overlay.offsetMs - durationMs
    if (tailMs > 0) {
        sequence.addItem(TransparentGapItem.create(tailMs, project.export.fps))
    }
    sequence.build()
}
