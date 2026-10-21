package br.com.riseedit.color

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import br.com.riseedit.core.Clip
import br.com.riseedit.core.ColorAdjust
import br.com.riseedit.core.color.ColorGrade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

private const val TAG = PausedColorRenderer.TAG
private const val FRAME_INTERVAL_MS = 33L

/**
 * While the fast preview is paused, covers the player with the current frame re-colored by the
 * export shader. Changes are coalesced (latest wins) and throttled to ~30 fps. Hidden on play.
 */
@Composable
internal fun PausedColorOverlay(
    clip: Clip,
    sourceMs: () -> Long,
    playing: Boolean,
    adjust: ColorAdjust,
    grade: ColorGrade,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var ready by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf<Bitmap?>(null) }
    val currentAdjust by rememberUpdatedState(adjust)
    val currentGrade by rememberUpdatedState(grade)
    val uri = clip.effectiveUri(true)
    DisposableEffect(Unit) { onDispose { ColorLive.overlayActive = false } }
    val currentSourceMs by rememberUpdatedState(sourceMs)
    // Keyed on state changes only: while playing the source position is NOT a key (it changes every
    // frame and used to relaunch this effect + log "overlay hidden" ~30x/s).
    LaunchedEffect(uri, playing) {
        ready = false
        shown = null
        ColorLive.overlayActive = false
        if (playing) {
            Log.d(TAG, "overlay hidden (playing)")
            return@LaunchedEffect
        }
        snapshotFlow { currentSourceMs() }.distinctUntilChanged().collectLatest { sourceMs ->
            ready = false
            ColorLive.overlayActive = false
            delay(120) // debounce scrubbing; keep the previous image until the new one is ready
            val t0 = System.nanoTime()
            val frame = withContext(Dispatchers.IO) { grab(context, uri, sourceMs) }
            if (frame == null) {
                shown = null
                Log.w(TAG, "frame grab failed uri=$uri at ${sourceMs}ms -> seek fallback")
                return@collectLatest
            }
            Log.d(TAG, "frame grabbed ${frame.width}x${frame.height} at ${sourceMs}ms in ${(System.nanoTime() - t0) / 1_000_000}ms")
            if (!PausedColorRenderer.setFrame(frame)) return@collectLatest
            ready = true
        }
    }
    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        var last = 0L
        snapshotFlow { Triple(currentAdjust, currentGrade, ColorLive.tick.intValue) }
            .collectLatest {
                val wait = FRAME_INTERVAL_MS - (System.currentTimeMillis() - last)
                if (wait > 0) delay(wait)
                last = System.currentTimeMillis()
                val out = PausedColorRenderer.render(it.first, it.second, ColorLive.holdOriginal)
                if (out != null) {
                    shown = out
                    if (!ColorLive.overlayActive) Log.i(TAG, "overlay active (paused)")
                    ColorLive.overlayActive = true
                }
            }
    }
    shown?.let {
        Image(it.asImageBitmap(), contentDescription = null, modifier = modifier, contentScale = ContentScale.Fit)
    }
}

internal fun grab(context: android.content.Context, uri: String, sourceMs: Long, max: Int = 1280): Bitmap? {
    val r = MediaMetadataRetriever()
    return try {
        r.setDataSource(context, Uri.parse(uri))
        r.getScaledFrameAtTime(sourceMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST, max, max)
            ?.let { if (it.config == Bitmap.Config.ARGB_8888) it else it.copy(Bitmap.Config.ARGB_8888, false) }
    } catch (error: RuntimeException) {
        Log.w(TAG, "grab error", error)
        null
    } finally {
        r.release()
    }
}
