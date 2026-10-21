package br.com.riseedit.core.timeline

import br.com.riseedit.core.AudioClip
import br.com.riseedit.core.Project
import br.com.riseedit.core.audibleAudio
import kotlin.math.min

/**
 * 0.18.1: where each audio-track item is at a project time, for the fast preview's per-layer
 * players. Mirrors the export (AudioSequences): starts at offsetMs, plays [startMs, startMs + d)
 * with d clamped to the project end, gain = volume × fade-in/out envelope.
 */
object AudioSchedule {
    data class Cue(val sourceMs: Long, val gain: Float)

    fun playableMs(audio: AudioClip, projectMs: Long) =
        min(audio.durationMs, projectMs - audio.offsetMs).coerceAtLeast(0)

    fun at(audio: AudioClip, projectMs: Long, globalMs: Long): Cue? {
        val d = playableMs(audio, projectMs)
        val local = globalMs - audio.offsetMs
        if (audio.muted || d <= 0 || local < 0 || local >= d) return null
        val fade = min(audio.fadeMs, d / 2)
        val env = if (fade <=
            0
        ) {
            1f
        } else {
            min(1f, min(local.toFloat() / fade, (d - local).toFloat() / fade))
        }
        return Cue(audio.startMs + local, audio.volume * env.coerceAtLeast(0f))
    }

    /** Items the preview must mount (same filter as the export). */
    fun layers(project: Project) = project.audibleAudio()
}
