package br.com.riseedit.transcode

import android.media.MediaFormat
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ProxyPlanTest {
    @Test fun bitrateIsHighAndCapped() {
        assertEquals(18_662_400, ProxyPlan.bitrate(1080, 1920, 30.0), 200_000)
        assertEquals(120_000_000, ProxyPlan.bitrate(7680, 4320, 60.0))
        assertEquals(10_000_000, ProxyPlan.bitrate(640, 360, 24.0))
    }

    private fun assertEquals(e: Int, a: Int, d: Int) = org.junit.Assert.assertTrue("$a", kotlin.math.abs(e - a) <= d)

    @Test fun colorTagsMapHdr() {
        assertEquals(MediaFormat.COLOR_TRANSFER_HLG, ProxyPlan.transfer(18))
        assertEquals(MediaFormat.COLOR_TRANSFER_ST2084, ProxyPlan.transfer(16))
        assertEquals(MediaFormat.COLOR_TRANSFER_SDR_VIDEO, ProxyPlan.transfer(1))
        assertEquals(MediaFormat.COLOR_STANDARD_BT2020, ProxyPlan.standard(9, 9))
        assertEquals(MediaFormat.COLOR_STANDARD_BT709, ProxyPlan.standard(1, 1))
        assertEquals(MediaFormat.COLOR_RANGE_FULL, ProxyPlan.range(2))
    }

    @Test fun downmixKeepsFrontPair() {
        val pcm = byteArrayOf(1, 0, 2, 0, 3, 0, 4, 0) // one frame, 4 channels
        assertArrayEquals(byteArrayOf(1, 0, 2, 0), AudioPlan.downmix(pcm, 4, 2))
    }
}
