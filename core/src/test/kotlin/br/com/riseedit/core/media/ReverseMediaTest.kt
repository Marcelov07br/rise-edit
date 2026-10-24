package br.com.riseedit.core.media

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReverseMediaTest {
    @Test fun reverseKeepsChannelOrder() {
        val st = shortArrayOf(1, -1, 2, -2, 3, -3)
        assertArrayEquals(shortArrayOf(3, -3, 2, -2, 1, -1), ReverseMedia.reverseFrames(st, 2))
        assertArrayEquals(shortArrayOf(3, 2, 1), ReverseMedia.reverseFrames(shortArrayOf(1, 2, 3), 1))
    }
    @Test fun windowMatchesVideoFrames() {
        // 10 s at 40 ms → 250 frames → exactly the trimmed range.
        assertEquals(2_000L..12_000L, ReverseMedia.window(2_000, 12_000, 40))
        // 1 010 ms → 25 frames = 1 000 ms, aligned to the END (reverse starts at endMs).
        assertEquals(1_010L..2_010L, ReverseMedia.window(1_000, 2_010, 40))
    }
    @Test fun sliceAndWavHeader() {
        val pcm = ShortArray(48_000 * 2) { it.toShort() } // 1 s stereo @48k
        val s = ReverseMedia.slice(pcm, 2, 48_000, 0, 500, 750)
        assertEquals(12_000 * 2, s.size)
        assertEquals((24_000 * 2).toShort(), s[0])
        val w = ReverseMedia.wav(s, 48_000, 2)
        assertEquals(44 + s.size * 2, w.size)
        assertEquals("RIFF", String(w, 0, 4)); assertEquals("WAVE", String(w, 8, 4))
    }
    @Test fun rotation90And270() {
        assertEquals(1080 to 1920, FrameRotation.size(1920, 1080, 90))
        assertEquals(1080 to 1920, FrameRotation.size(1920, 1080, 270))
        assertEquals(1920 to 1080, FrameRotation.size(1920, 1080, 180))
        assertTrue(FrameRotation.swapsAxes(-90)); assertFalse(FrameRotation.swapsAxes(180))
        assertEquals(270, FrameRotation.normalize(-90))
        // top-left stored pixel of a 4×2 frame: 90° cw → top-right; 270° → bottom-left.
        assertEquals(1 to 0, FrameRotation.map(0, 0, 4, 2, 90))
        assertEquals(0 to 3, FrameRotation.map(0, 0, 4, 2, 270))
    }
    @Test fun pendingRotationOnlyWhenRetrieverIgnoredIt() {
        // phone portrait video: stored 1920×1080 with rotation 90 / 270
        assertEquals(90, FrameRotation.pending(1280, 720, 1920, 1080, 90))   // came back sideways
        assertEquals(270, FrameRotation.pending(1280, 720, 1920, 1080, 270))
        assertEquals(0, FrameRotation.pending(720, 1280, 1920, 1080, 90))    // already upright
        assertEquals(0, FrameRotation.pending(720, 1280, 1920, 1080, 270))
        assertEquals(0, FrameRotation.pending(1280, 720, 1920, 1080, 0))
        assertEquals(0, FrameRotation.pending(1280, 720, 1920, 1080, 180))
    }
}
