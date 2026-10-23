package br.com.riseedit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportOptionsTest {
    private val basic = EncoderSupport(h264 = true, hevc = true)
    private val main10 = basic.copy(hevc10 = true)
    private val full = main10.copy(hevc422 = true, avc422 = true, apv = true, prores = true)

    @Test fun onlyWhatThePhoneEncodesIsOffered() {
        assertEquals(listOf(VideoCodec.H264, VideoCodec.HEVC), ExportOptions.codecs(basic))
        assertEquals(listOf(8), ExportOptions.depths(VideoCodec.HEVC, basic))
        assertEquals(listOf(8, 10), ExportOptions.depths(VideoCodec.HEVC, main10))
        assertEquals(listOf(Chroma.YUV420), ExportOptions.chromas(VideoCodec.HEVC, 10, main10))
        assertEquals(listOf(Chroma.YUV420, Chroma.YUV422), ExportOptions.chromas(VideoCodec.HEVC, 10, full))
        assertEquals(VideoCodec.entries.toList(), ExportOptions.codecs(full))
        assertFalse(VideoCodec.APV in ExportOptions.codecs(main10.copy(prores = true)))
    }

    @Test fun defaultIsHevcTenBit420WhenMain10Exists() {
        val d = ExportOptions.defaults(ExportSettings(), main10)
        assertEquals(VideoCodec.HEVC, d.codec); assertEquals(10, d.bitDepth); assertEquals(Chroma.YUV420, d.chroma)
        assertEquals(ExportRoute.DIRECT, ExportOptions.route(d))
        assertEquals(8, ExportOptions.defaults(ExportSettings(), basic).bitDepth)
    }

    @Test fun unsupportedChoicesSnapBack() {
        val apv = ExportSettings(codec = VideoCodec.APV, bitDepth = 10, chroma = Chroma.YUV422)
        val n = ExportOptions.normalize(apv, main10)
        assertEquals(VideoCodec.HEVC, n.codec); assertEquals(Chroma.YUV420, n.chroma)
        val h264x = ExportOptions.normalize(ExportSettings(codec = VideoCodec.H264, bitDepth = 10), basic)
        assertEquals(8, h264x.bitDepth)
        val h264on422phone = ExportOptions.normalize(ExportSettings(codec = VideoCodec.H264, bitDepth = 10), full)
        assertEquals(8, h264on422phone.bitDepth); assertEquals(Chroma.YUV420, h264on422phone.chroma)
    }

    @Test fun routesAndContainers() {
        val pr = ExportSettings(codec = VideoCodec.PRORES, bitDepth = 10, chroma = Chroma.YUV422)
        assertEquals(ExportRoute.PRORES, ExportOptions.route(pr))
        assertEquals("mov", ExportOptions.extension(pr)); assertFalse(ExportOptions.usesBitrate(pr))
        assertEquals(ExportRoute.HARDWARE_422, ExportOptions.route(ExportSettings(codec = VideoCodec.APV, bitDepth = 10, chroma = Chroma.YUV422)))
        assertEquals(ExportRoute.HARDWARE_422, ExportOptions.route(ExportSettings(codec = VideoCodec.HEVC, bitDepth = 10, chroma = Chroma.YUV422)))
        val hq = ExportOptions.proresMbps(pr.copy(height = 1080, fps = 30), 1920)
        assertTrue("HQ 1080p30 ≈ 220 Mb/s: $hq", hq in 210..230)
        assertEquals("MOV · ProRes 422 HQ · 10 bits 4:2:2 · AAC", ExportOptions.summary(pr))
    }
}
