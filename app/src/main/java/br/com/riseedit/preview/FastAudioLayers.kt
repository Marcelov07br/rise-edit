@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package br.com.riseedit.preview

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import br.com.riseedit.core.AudioClip
import br.com.riseedit.core.Project
import br.com.riseedit.core.timeline.AudioSchedule
import kotlin.math.abs

private const val TAG = "RiseAudio"
private const val MAX_LAYERS = 6
private const val DRIFT_MS = 120L

/**
 * 0.18.1: the fast preview's main ExoPlayer only plays the main-track clips, so audio-track items
 * (imported music, separated audio) never reached the speakers. Each audible item now gets its own
 * audio-only ExoPlayer that follows the main playhead ([tick]); the export mixes the same items
 * through AudioSequences.
 */
internal class FastAudioLayers(private val context: Context) {
    private class Layer(val audio: AudioClip, val player: ExoPlayer)

    private var layers = emptyMap<String, Layer>()
    private var projectMs = 0L

    fun update(project: Project) {
        projectMs = project.durationMs
        val wanted = AudioSchedule.layers(project).take(MAX_LAYERS)
        if (project.audio.size > wanted.size) {
            Log.i(
                TAG,
                "prévia rápida: ${project.audio.size - wanted.size} item(ns) fora (mudo/após o fim/limite $MAX_LAYERS)"
            )
        }
        val next = mutableMapOf<String, Layer>()
        wanted.forEach { a ->
            val old = layers[a.id]
            if (old != null && sameMedia(old.audio, a)) {
                next[a.id] = Layer(a, old.player)
            } else {
                old?.player?.release()
                next[a.id] = Layer(a, create(a))
            }
        }
        (layers.keys - next.keys).forEach { id -> layers[id]?.player?.release() }
        if (next.keys != layers.keys) {
            Log.i(
                TAG,
                "faixas montadas: " + next.values.joinToString { l ->
                    "${l.audio.name} off=${l.audio.offsetMs} src=${l.audio.startMs}..${l.audio.endMs} vol=${l.audio.volume}"
                }.ifEmpty { "nenhuma" }
            )
        }
        layers = next
    }

    private fun sameMedia(a: AudioClip, b: AudioClip) =
        a.uri == b.uri && a.startMs == b.startMs && a.endMs == b.endMs

    private fun create(a: AudioClip): ExoPlayer = ExoPlayer.Builder(context).build().apply {
        addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "erro na faixa ${a.name} (${a.uri}): ${error.errorCodeName}", error)
            }
        })
        setMediaItem(
            MediaItem.Builder().setUri(a.uri).setMediaId(a.id).setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(a.startMs)
                    .setEndPositionMs(a.endMs)
                    .build()
            ).build()
        )
        volume = 0f
        prepare()
    }

    /** Follows the main player: [globalMs] = project playhead, [playing] = main is playing. */
    fun tick(globalMs: Long, playing: Boolean, muted: Boolean) {
        layers.values.forEach { l ->
            val p = l.player
            val cue = AudioSchedule.at(l.audio, projectMs, globalMs)
            if (cue == null) {
                if (p.playWhenReady) p.playWhenReady = false
                if (p.volume != 0f) p.volume = 0f
                return@forEach
            }
            val target = cue.sourceMs - l.audio.startMs
            val drift = abs(p.currentPosition - target)
            if (drift > DRIFT_MS || (!playing && drift > 30)) p.seekTo(target)
            if (p.playbackState == Player.STATE_IDLE) p.prepare()
            if (p.playWhenReady != playing) p.playWhenReady = playing
            val v = if (muted) 0f else cue.gain.coerceIn(0f, 1f)
            if (p.volume != v) p.volume = v
        }
    }

    fun pause() = layers.values.forEach { it.player.playWhenReady = false }

    fun release() {
        layers.values.forEach { runCatching { it.player.release() } }
        layers = emptyMap()
    }
}
