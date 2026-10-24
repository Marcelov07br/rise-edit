package br.com.riseedit.media

import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import br.com.riseedit.core.media.FrameRotation

/**
 * 0.23.0: freeze-frame / reverse frames respect the container rotation metadata.
 * See [FrameRotation.pending]: rotate only when the retriever returned the stored picture.
 */
internal class UprightFrames(r: MediaMetadataRetriever) {
    private val degrees = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
    private val storedW = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
    private val storedH = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0

    fun fix(bmp: Bitmap): Bitmap = rotate(bmp, FrameRotation.pending(bmp.width, bmp.height, storedW, storedH, degrees))

    companion object {
        /** Clockwise rotation; recycles the input when a new bitmap is made. */
        fun rotate(bmp: Bitmap, degrees: Int): Bitmap {
            val d = FrameRotation.normalize(degrees)
            if (d == 0) return bmp
            val out = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(d.toFloat()) }, true)
            if (out !== bmp) bmp.recycle()
            return out
        }
    }
}
