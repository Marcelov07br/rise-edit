package br.com.riseedit.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class FrameFormatTest {
    @Test fun fitCanvasLandscapeIntoVertical() {
        assertEquals(1920 to 3414, FrameFormat.fitCanvas(1920, 1080, 9f / 16f))
        assertEquals(1920 to 1080, FrameFormat.fitCanvas(1920, 1080, 16f / 9f))
        assertEquals(2520 to 1080, FrameFormat.fitCanvas(1920, 1080, 21f / 9f))
    }

    @Test fun contentRectCentered() {
        val r = FrameFormat.contentRect(1920, 1080, 1920, 3414)
        assertArrayEquals(floatArrayOf(0f, (1 - 1080f / 3414) / 2, 1f, 1080f / 3414), r, 1e-5f)
    }

    @Test fun reframeClampsInsideFrame() {
        val s = 16f / 9f
        val o = 9f / 16f
        assertEquals(0f, FrameFormat.reframeX(s, o, 0.5f), 1e-6f)
        val visible = o / s
        // far left subject: crop stops at the left edge
        assertEquals((0.5f - visible / 2) * 2, FrameFormat.reframeX(s, o, 0f), 1e-6f)
        assertEquals(-(0.5f - visible / 2) * 2, FrameFormat.reframeX(s, o, 1f), 1e-6f)
        assertEquals(0f, FrameFormat.reframeX(o, s, 0.1f), 0f) // narrower source: nothing to pan
    }

    @Test fun ratioCards() {
        assertEquals(FormatRatio.PORTRAIT, FormatRatio.of(0.8f))
        assertEquals(FormatRatio.ORIGINAL, FormatRatio.of(0f))
        assertEquals(FormatRatio.FREE, FormatRatio.of(1.85f))
    }
}
