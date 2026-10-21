package br.com.riseedit.core.timeline

import br.com.riseedit.core.AudioClip
import br.com.riseedit.core.Clip
import br.com.riseedit.core.Project
import br.com.riseedit.core.editing.SeparateAudio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AudioScheduleTest {
    private val music = AudioClip(
        uri = "file:///m.mp3",
        name = "M",
        sourceDurationMs = 10_000,
        offsetMs = 1000,
        startMs = 2000,
        endMs = 6000,
        volume = 0.8f,
        fadeMs = 0
    )

    @Test
    fun cueFollowsOffsetAndTrim() {
        assertNull(AudioSchedule.at(music, 8000, 999))
        assertEquals(AudioSchedule.Cue(2000, 0.8f), AudioSchedule.at(music, 8000, 1000))
        assertEquals(4500L, AudioSchedule.at(music, 8000, 3500)!!.sourceMs)
        assertNull(AudioSchedule.at(music, 8000, 5000))
    }

    @Test
    fun clampedToProjectEndAndFades() {
        assertNull(AudioSchedule.at(music, 3000, 3000))
        val faded = music.copy(fadeMs = 1000)
        assertEquals(0.4f, AudioSchedule.at(faded, 8000, 1500)!!.gain, 1e-4f)
        assertNull(AudioSchedule.at(music.copy(muted = true), 8000, 2000))
    }

    @Test
    fun separatedAudioIsMountedAndPlaysWhileTheClipIsMuted() {
        val clip = Clip(uri = "file:///v.mp4", name = "V", sourceDurationMs = 3000)
        val p = SeparateAudio.apply(Project(clips = listOf(clip)), 0)
        assertEquals(true, p.clips[0].muted)
        val layer = AudioSchedule.layers(p).single()
        assertNotNull(AudioSchedule.at(layer, p.durationMs, 1500))
    }
}
