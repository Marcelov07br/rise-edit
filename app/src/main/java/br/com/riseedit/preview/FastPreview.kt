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
internal class FastPreview(context: Context, liveEffects: Boolean = true) {
    val player = ExoPlayer.Builder(context).build().apply {
        setSeekParameters(androidx.media3.exoplayer.SeekParameters.EXACT)
        // 0.19.3: scrub decodes video only and asks the codec to run faster (Media3 1.9 API).
        setScrubbingModeParameters(
            androidx.media3.exoplayer.ScrubbingModeParameters.DEFAULT.buildUpon()
                .setDisabledTrackTypes(setOf(androidx.media3.common.C.TRACK_TYPE_AUDIO))
                .setShouldIncreaseCodecOperatingRate(true)
                .build()
        )
        // Same shaders as export; they read ColorLive/StabilizationLive each frame (slider
        // changes need no rebuild). Must be set before the first prepare().
        if (liveEffects) {
            runCatching {
                setVideoEffects(
                    listOf(
                        br.com.riseedit.color.ColorAdjustEffect.live,
                        br.com.riseedit.color.AdjustmentLayersEffect.live,
                        br.com.riseedit.StabilizationEffect.live
                    )
                )
            }
                .onFailure {
                    br.com.riseedit.diagnostics.Diagnostics.log(
                        "cor",
                        "Efeitos ao vivo indisponíveis na prévia",
                        it
                    )
                }
        }
    }
    private val sources = DefaultMediaSourceFactory(context)
    private val audioLayers = FastAudioLayers(context)
    private var timeline = PreviewTimeline(emptyList())
    private var slots = emptyList<PreviewSlot>()
    private var faults = emptyMap<String, MediaFault>()
    private var atEnd = false
    val currentIndex: Int get() = player.currentMediaItemIndex
    val fault: MediaFault? get() = timeline.clips.getOrNull(currentIndex)?.let { faults[it.id] }
    val positionMs: Long get() = if (atEnd && !player.playWhenReady) {
        timeline.durationMs
    } else {
        timeline.globalPosition(
            currentIndex,
            player.currentPosition.coerceAtLeast(0),
            slots.getOrNull(currentIndex)?.unavailable == true
        )
    }

    fun update(project: Project, newFaults: Map<String, MediaFault>, positionMs: Long) {
        val speedChanged =
            timeline.clips.map { it.speedPoints to it.keepPitch } !=
                project.clips.map { it.speedPoints to it.keepPitch }
        timeline = PreviewTimeline(project.clips)
        faults = newFaults
        audioLayers.update(project)
        val newSlots = project.clips.map { clip ->
            PreviewSlot(
                clip.id,
                project.previewUri(clip),
                clip.startMs,
                clip.endMs,
                clip.durationMs,
                newFaults.containsKey(clip.id)
            )
        }
        if (slots != newSlots) {
            slots = newSlots
            val playlist = slots.map { slot ->
                if (slot.unavailable) {
                    SilenceMediaSource.Factory().setDurationUs(
                        slot.durationMs * 1_000
                    ).setTag(slot.id).createMediaSource()
                } else {
                    sources.createMediaSource(
                        MediaItem.Builder().setUri(
                            slot.uri
                        ).setMediaId(slot.id).setClippingConfiguration(
                            MediaItem.ClippingConfiguration.Builder().setStartPositionMs(
                                slot.startMs
                            ).setEndPositionMs(slot.endMs).build()
                        ).build()
                    )
                }
            }
            if (playlist.isEmpty()) {
                player.clearMediaItems()
                player.stop()
                atEnd = false
            } else {
                val index = timeline.indexAt(positionMs)
                player.setMediaSources(playlist, index, seekPosition(index, positionMs))
                player.prepare()
                atEnd = positionMs >= timeline.durationMs
            }
        } else if (slots.isNotEmpty() && player.playbackState == Player.STATE_IDLE) {
            seek(positionMs)
            player.prepare()
        } else if (speedChanged) {
            seek(positionMs)
        }
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
        val local = if (slot.unavailable) {
            timeline.localOutput(
                index,
                globalMs
            )
        } else {
            timeline.sourcePosition(index, globalMs)
        }
        return local.coerceIn(0, (duration - 1).coerceAtLeast(0))
    }

    fun tick(muted: Boolean) {
        val index = currentIndex
        val clip = timeline.clips.getOrNull(index) ?: return
        val unavailable = slots.getOrNull(index)?.unavailable ?: return
        val source = if (unavailable) {
            clip.sourceAt(timeline.localOutput(index, positionMs))
        } else {
            clip.startMs +
                player.currentPosition
        }
        val speed = if (unavailable) 1f else clip.speedAt(source).toFloat()
        // Curves are sampled every 50 ms; faithful preview/export apply their precise timestamps.
        // ExoPlayer's Sonic caps at 8×; export applies the real value (up to 10×).
        val live = speed.coerceAtMost(PREVIEW_MAX_SPEED)
        val pitch = if (clip.keepPitch) 1f else live
        val params = player.playbackParameters
        if (params.speed != live || params.pitch != pitch) {
            player.playbackParameters = androidx.media3.common.PlaybackParameters(live, pitch)
        }
        val local = timeline.localOutput(index, positionMs)
        val fade = min(clip.fadeMs, clip.durationMs / 2)
        val envelope = if (fade <=
            0
        ) {
            1f
        } else {
            min(
                1f,
                min(
                    local.toFloat() / fade,
                    (clip.durationMs - local).toFloat() / fade
                )
            ).coerceAtLeast(0f)
        }
        // Player volume supports 0..1. Gain above one is an explicit fast-preview approximation.
        val volume = if (muted ||
            unavailable ||
            clip.muted
        ) {
            0f
        } else {
            (clip.volume * envelope).coerceIn(0f, 1f)
        }
        if (player.volume != volume) player.volume = volume
        audioLayers.tick(positionMs, player.isPlaying, muted)
    }
    fun release() {
        audioLayers.release()
        player.release()
    }
}

private const val PREVIEW_MAX_SPEED = 8f
