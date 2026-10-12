@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
package br.com.riseedit.preview

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.SilenceMediaSource
import br.com.riseedit.core.MediaFault
import br.com.riseedit.core.Project
import kotlin.math.min

/** Decodes the existing original/proxy track. A renderer size limit cannot turn it into a 480p decode. */
internal class FastPreview(context: Context) {
    val player = ExoPlayer.Builder(context).build()
    private val sources = DefaultMediaSourceFactory(context)
    private var timeline = PreviewTimeline(emptyList())
    private var slots = emptyList<PreviewSlot>()
    private var faults = emptyMap<String, MediaFault>()
    private var atEnd = false
    val currentIndex: Int get() = player.currentMediaItemIndex
    val fault: MediaFault? get() = timeline.clips.getOrNull(currentIndex)?.let { faults[it.id] }
    val positionMs: Long get() = if (atEnd && !player.playWhenReady) timeline.durationMs else
        timeline.globalPosition(currentIndex, player.currentPosition.coerceAtLeast(0), slots.getOrNull(currentIndex)?.unavailable == true)

    fun update(project: Project, newFaults: Map<String, MediaFault>, positionMs: Long) {
        val speedChanged = timeline.clips.map { it.speedPoints } != project.clips.map { it.speedPoints }
        timeline = PreviewTimeline(project.clips)
        faults = newFaults
        val newSlots = project.clips.map { clip -> PreviewSlot(clip.id, clip.effectiveUri(true), clip.startMs,
            clip.endMs, clip.durationMs, newFaults.containsKey(clip.id)) }
        if (slots != newSlots) {
            slots = newSlots
            val playlist = slots.map { slot ->
                if (slot.unavailable) SilenceMediaSource.Factory().setDurationUs(slot.durationMs * 1_000).setTag(slot.id).createMediaSource()
                else sources.createMediaSource(MediaItem.Builder().setUri(slot.uri).setMediaId(slot.id).setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder().setStartPositionMs(slot.startMs).setEndPositionMs(slot.endMs).build()
                ).build())
            }
            if (playlist.isEmpty()) { player.clearMediaItems(); player.stop(); atEnd = false }
            else {
                val index = timeline.indexAt(positionMs)
                player.setMediaSources(playlist, index, seekPosition(index, positionMs))
                player.prepare()
                atEnd = positionMs >= timeline.durationMs
            }
        } else if (slots.isNotEmpty() && player.playbackState == Player.STATE_IDLE) {
            seek(positionMs)
            player.prepare()
        } else if (speedChanged) seek(positionMs)
    }

    fun seek(positionMs: Long) {
        val bounded = positionMs.coerceIn(0, timeline.durationMs)
        val index = timeline.indexAt(bounded)
        if (index < 0) return
        atEnd = bounded == timeline.durationMs
        player.seekTo(index, seekPosition(index, bounded))
    }

    private fun seekPosition(index: Int, globalMs: Long): Long {
        val slot = slots[index]
        val duration = if (slot.unavailable) slot.durationMs else slot.endMs - slot.startMs
        val local = if (slot.unavailable) timeline.localOutput(index, globalMs) else timeline.sourcePosition(index, globalMs)
        return local.coerceIn(0, (duration - 1).coerceAtLeast(0))
    }

    fun tick(muted: Boolean) {
        val index = currentIndex
        val clip = timeline.clips.getOrNull(index) ?: return
        val unavailable = slots.getOrNull(index)?.unavailable ?: return
        val source = if (unavailable) clip.sourceAt(timeline.localOutput(index, positionMs)) else clip.startMs + player.currentPosition
        val speed = if (unavailable) 1f else clip.speedAt(source).toFloat()
        // Curves are sampled every 50 ms; faithful preview/export apply their precise timestamps.
        if (player.playbackParameters.speed != speed) player.setPlaybackSpeed(speed)
        val local = timeline.localOutput(index, positionMs)
        val fade = min(clip.fadeMs, clip.durationMs / 2)
        val envelope = if (fade <= 0) 1f else min(1f, min(local.toFloat() / fade, (clip.durationMs - local).toFloat() / fade)).coerceAtLeast(0f)
        // Player volume supports 0..1. Gain above one is an explicit fast-preview approximation.
        val volume = if (muted || unavailable) 0f else (clip.volume * envelope).coerceIn(0f, 1f)
        if (player.volume != volume) player.volume = volume
    }
    fun release() = player.release()
}
