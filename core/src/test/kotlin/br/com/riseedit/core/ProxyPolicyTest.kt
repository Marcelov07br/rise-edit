package br.com.riseedit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyPolicyTest {
    private fun info(w: Int, h: Int, depth: ColorDepth = ColorDepth.EIGHT, chroma: ChromaFormat = ChromaFormat.YUV420, rot: Int = 0) =
        SourceInfo("video/hevc", w, h, bitDepth = depth, chroma = chroma, frameRate = 60f, rotationDegrees = rot)

    @Test fun heavyMediaReasons() {
        assertEquals(listOf("4K"), ProxyPolicy.reasons(info(3840, 2160)))
        assertEquals(listOf("10 bits", "4:2:2"), ProxyPolicy.reasons(info(1920, 1080, ColorDepth.TEN, ChromaFormat.YUV422)))
        assertEquals(listOf("100 Mbps"), ProxyPolicy.reasons(info(1920, 1080), 100_000_000))
        assertFalse(ProxyPolicy.heavy(info(1920, 1080), 20_000_000))
        assertTrue(ProxyPolicy.heavy(info(2560, 1440)))
    }

    @Test fun specFitsResolutionKeepsOrientationAndCodec() {
        val s = ProxyPolicy.spec(info(3840, 2160), ProxyResolution.P720, ProxyCodec.HEVC)
        assertEquals(ProxySpec(1280, 720, 4_000_000, 30, "video/hevc"), s)
        val portrait = ProxyPolicy.spec(info(3840, 2160, rot = 90), ProxyResolution.P1080, ProxyCodec.H264)
        assertEquals(1080 to 1920, portrait.width to portrait.height)
        assertEquals("video/avc", portrait.mime)
        // never upscales
        val small = ProxyPolicy.spec(info(1280, 720, ColorDepth.TEN), ProxyResolution.P1080, ProxyCodec.H264)
        assertEquals(1280 to 720, small.width to small.height)
    }

    @Test fun exportAlwaysReadsOriginalForPreviewProxies() {
        val c = Clip(uri = "file:///a.mp4", name = "a", sourceDurationMs = 1000).copy(proxyUri = "file:///p.mp4")
        assertTrue(c.hasPreviewProxy)
        assertEquals("file:///a.mp4", c.effectiveUri(false))
        val p = Project(clips = listOf(c))
        assertEquals("file:///p.mp4", p.previewUri(c))
        assertEquals("file:///a.mp4", p.copy(useProxies = false).previewUri(c))
        val required = c.copy(proxyRequired = true)
        assertEquals("file:///p.mp4", p.copy(useProxies = false).previewUri(required))
    }
}
