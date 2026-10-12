@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package br.com.riseedit.composition

import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.EditedMediaItem
import br.com.riseedit.R

internal object TransparentGapItem {
    // Media3 1.9 native video gaps stall multi-input composition. A transparent PNG
    // is required to keep each overlay sequence producing frames before and after
    // its clip. Keep this resource in R8 and do not replace it with addGap().
    fun create(durationMs: Long, fps: Int): EditedMediaItem {
        val media = MediaItem.Builder()
            .setUri("android.resource://br.com.riseedit/${R.raw.transparent}")
            .setMimeType(MimeTypes.IMAGE_PNG)
            .setImageDurationMs(durationMs)
            .build()
        return EditedMediaItem.Builder(media)
            .setDurationUs(durationMs * 1000)
            .setFrameRate(fps)
            .setRemoveAudio(true)
            .build()
    }
}
