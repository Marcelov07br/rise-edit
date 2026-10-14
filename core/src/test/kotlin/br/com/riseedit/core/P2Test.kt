package br.com.riseedit.core

import br.com.riseedit.core.editing.AudioTrimSession
import br.com.riseedit.core.editing.OverlayTrimSession
import br.com.riseedit.core.editing.SeparateAudio
import br.com.riseedit.core.editing.TrimEdge
import br.com.riseedit.core.keys.Easing
import br.com.riseedit.core.keys.TimeDomain
import br.com.riseedit.core.keys.TransformTracks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class P2Test {
    private fun clip(name: String = "a") =
        Clip(uri = "file:///$name.mp4", name = name, sourceDurationMs = 4000)

    @Test fun separateAudioMutesSourceAndLinksTrims() {
        val p0 = Project(listOf(clip("a"), clip("b").trim(500, 3500)))
        val p1 = SeparateAudio.apply(p0, 1)
        val audio = p1.audio.single()
        assertTrue(p1.clips[1].muted)
        assertEquals(p1.clips[1].id, audio.linkedClipId)
        assertEquals(4000L, audio.offsetMs)
        assertEquals(500L to 3500L, audio.startMs to audio.endMs)
        assertEquals(p1, SeparateAudio.apply(p1, 1))
        val trimmed = p1.replace(1, p1.clips[1].trim(1000, 3000))
        val synced = SeparateAudio.sync(trimmed, p1.clips[1].id).audio.single()
        assertEquals(1000L to 3000L, synced.startMs to synced.endMs)
        val split = SeparateAudio.apply(Project(listOf(clip())), 0).split(0, 1000)
        assertEquals(2, split.audio.size)
        assertEquals(split.clips.map { it.id }, split.audio.map { it.linkedClipId })
        assertEquals(1000L, split.audio[1].offsetMs)
    }

    @Test fun layerEdgeTrimKeepsOppositeEdge() {
        val a = AudioClip(uri = "u", name = "a", sourceDurationMs = 5000, offsetMs = 1000)
        val s = AudioTrimSession(a, TrimEdge.START).dragged(500_000.0)
        assertEquals(500L, s.startMs)
        assertEquals(1500L, s.offsetMs)
        assertEquals(6000L, s.offsetMs + s.durationMs)
        assertEquals(3000L, AudioTrimSession(a, TrimEdge.END).dragged(-2_000_000.0).endMs)
        assertEquals(40L, AudioTrimSession(a, TrimEdge.END).dragged(-9e9).durationMs)
        val o = VideoOverlay(clip = clip(), offsetMs = 2000)
        val os = OverlayTrimSession(o, TrimEdge.START).dragged(1_000_000.0)
        assertEquals(1000L, os.clip.startMs)
        assertEquals(3000L, os.offsetMs)
        assertEquals(6000L, os.offsetMs + os.clip.durationMs)
    }

    @Test fun orientationSteps() {
        val o = Orientation().rotate90().rotate90().rotate90().rotate90()
        assertEquals(0, o.turns)
        assertEquals(90f, Orientation().rotate90().rotationDegrees)
        assertEquals(ScaleMode.FIT, Orientation().cycleScale().scaleMode)
    }

    @Test fun keyEasingEditAndDeleteThroughTracks() {
        val keys = listOf(TransformKeyframe(0, x = 0f), TransformKeyframe(1000, x = 1f))
        val c = Clip(uri = "u", name = "c", sourceDurationMs = 2000, keyframes = keys)
        val tracks = KeyframeEdits.resolve(c)
        val linear = KeyframeEdits.applyToClip(c, KeyframeEdits.setEasing(tracks, 0, Easing.Linear))
        assertEquals(0.25f, Timeline.keyframe(linear, 250).x, 1e-5f)
        assertEquals(Easing.Linear, KeyframeEdits.easingAt(linear.tracks!!, 0))
        val hold = KeyframeEdits.applyToClip(c, KeyframeEdits.setEasing(tracks, 0, Easing.Hold))
        assertEquals(0f, Timeline.keyframe(hold, 900).x)
        val removed = KeyframeEdits.applyToClip(linear, KeyframeEdits.remove(linear.tracks!!, 1000))
        assertEquals(listOf(0L), removed.keyframes.map { it.sourceMs })
        val none = KeyframeEdits.applyToClip(removed, KeyframeEdits.remove(removed.tracks!!, 0))
        assertNull(none.tracks)
        // Legacy keys without tracks evaluate exactly like migrated tracks.
        assertEquals(
            TransformTracks.fromLegacy(keys, TimeDomain.SOURCE).at(333),
            Timeline.keyframe(c, 333)
        )
        // Auto-key on the preview keeps chosen easing.
        val moved = TransformEdits.clip(linear, 1000, TransformKeyframe(1000, x = 2f))
        assertEquals(Easing.Linear, KeyframeEdits.easingAt(moved.tracks!!, 0))
        assertEquals(0.5f, Timeline.keyframe(moved, 250).x, 1e-5f)
    }
}
