@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class, androidx.media3.common.util.ExperimentalApi::class])
package br.com.riseedit.preview

import android.content.Context
import android.os.SystemClock
import androidx.media3.common.Player
import androidx.media3.effect.MultipleInputVideoGraph
import androidx.media3.transformer.Composition
import androidx.media3.transformer.CompositionPlayer
import java.util.concurrent.atomic.AtomicLong

/** Constructed only after an explicit faithful request and its local resource-budget check. */
internal class FaithfulPreview(context: Context) {
    // 0.19.2: FrameCompat (tolerant frame registration) once this device showed the 7001/NoSuchElement failure.
    val player = CompositionPlayer.Builder(context).setVideoGraphFactory(
        br.com.riseedit.media.FrameCompat.factory()?.let { MultipleInputVideoGraph.Factory(it) } ?: MultipleInputVideoGraph.Factory()
    ).build()
    private val lastFrameAt = AtomicLong(0)
    private val lastFramePts = AtomicLong(Long.MIN_VALUE)
    private var lastPosition = 0L
    private var lastProgressAt = SystemClock.elapsedRealtime()
    private var watchStartedAt = lastProgressAt
    init { player.setVideoFrameMetadataListener { pts, _, _, _ ->
        if (lastFramePts.getAndSet(pts) != pts) lastFrameAt.set(SystemClock.elapsedRealtime())
    } }
    fun prepare(composition: Composition, positionMs: Long) {
        player.setComposition(composition, positionMs)
        player.prepare()
        resetWatchdog()
    }
    fun seek(positionMs: Long) { player.seekTo(positionMs); resetWatchdog() }
    fun resetWatchdog() {
        lastPosition = player.currentPosition
        lastProgressAt = SystemClock.elapsedRealtime()
        watchStartedAt = lastProgressAt
        lastFrameAt.set(0); lastFramePts.set(Long.MIN_VALUE)
    }
    fun stalled(tryingToPlay: Boolean, scrubbing: Boolean): Boolean {
        if (!tryingToPlay || scrubbing || player.playbackState == Player.STATE_ENDED) { resetWatchdog(); return false }
        val now = SystemClock.elapsedRealtime()
        val current = player.currentPosition
        if (current != lastPosition) lastProgressAt = now
        lastPosition = current
        val lastFrame = lastFrameAt.get().takeIf { it > 0 } ?: watchStartedAt
        return now - lastProgressAt >= 3_000 || now - lastFrame >= 3_000
    }
    fun release() = player.release()
}
