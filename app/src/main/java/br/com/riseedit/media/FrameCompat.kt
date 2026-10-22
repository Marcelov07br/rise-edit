@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package br.com.riseedit.media

import android.content.Context
import androidx.media3.common.VideoFrameProcessor
import androidx.media3.effect.DefaultVideoFrameProcessor

/**
 * 0.19.2: compatibility mode for Media3's GL frame pipeline.
 *
 * By default Media3 pairs every decoded frame on its SurfaceTexture with a frame "registered"
 * by the player/Transformer (ExternalTextureManager.pendingFrames). Some ROMs deliver extra
 * or early frame callbacks; the queue is then empty and `Queue.element()` throws a bare
 * NoSuchElementException (error 7001 in playback, 5001 in export) and the picture freezes.
 * `setRequireRegisteringAllInputFrames(false)` makes Media3 re-use the last registered frame
 * info instead. Turned on per device after the first such failure and remembered.
 */
object FrameCompat {
    private const val PREFS = "rise_compat"
    private const val KEY = "tolerantFrames"

    @Volatile
    var tolerant: Boolean = false
        private set

    fun load(context: Context) {
        tolerant = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)
    }

    fun enable(context: Context) {
        tolerant = true
        context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        ).edit().putBoolean(KEY, true).apply()
    }

    /** Null = Media3 default; otherwise the tolerant processor. */
    fun factory(): VideoFrameProcessor.Factory? = if (tolerant) tolerantFactory() else null

    fun tolerantFactory(): VideoFrameProcessor.Factory =
        DefaultVideoFrameProcessor.Factory.Builder().setRequireRegisteringAllInputFrames(
            false
        ).build()

    /** The frame-registration signature: an empty-queue NoSuchElementException in the cause chain. */
    fun isFrameRegistrationFailure(error: Throwable?): Boolean {
        var c = error
        var depth = 0
        while (c != null && depth++ < 16) {
            if (c is NoSuchElementException) return true
            c = c.cause.takeIf { it !== c }
        }
        return false
    }
}
