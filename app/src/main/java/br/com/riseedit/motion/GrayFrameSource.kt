package br.com.riseedit.motion

import org.opencv.core.Mat

/** Delivers display-oriented, downscaled 8-bit luma frames in presentation order. */
internal fun interface GrayFrameSource {
    /** [onFrame] receives the frame time in ms and a Mat that is only valid during the call. */
    suspend fun read(minIntervalMs: Long, progress: (Float) -> Unit, onFrame: (Long, Mat) -> Unit)
}

internal const val ANALYSIS_MAX_SIDE = 640
