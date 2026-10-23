package br.com.riseedit.transcode

import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Runs only via tools/ffmpeg/host-test.sh (host build of the same JNI + real 4:2:2 10-bit files). */
class HostIngestTest {
    private val lib = System.getenv("RISE_HOST_INGEST")
    private val samples = System.getenv("RISE_SAMPLES")

    private fun fdOf(s: FileInputStream): Int =
        FileDescriptor::class.java.getDeclaredField("fd").apply { isAccessible = true }.getInt(s.fd)

    private fun decodeAll(name: String, tenBit: Boolean): Pair<IngestInfo, List<Long>> {
        assumeTrue(lib != null && samples != null)
        System.load(lib!!)
        FileInputStream(File(samples, name)).use { s ->
            val raw = LongArray(13)
            val h = FfmpegIngest.nativeOpen(fdOf(s), raw)
            assertTrue("open", h != 0L)
            val info = IngestInfo.of(raw)
            val bpp = if (tenBit) 2 else 1
            val y = ByteBuffer.allocateDirect(info.width * info.height * bpp)
            val uv = ByteBuffer.allocateDirect(info.width * info.height / 2 * bpp + 4)
            val u = uv.duplicate()
            val v = (uv.duplicate().position(bpp) as ByteBuffer).slice()
            val pts = mutableListOf<Long>()
            while (true) {
                val t = FfmpegIngest.nativeDecode(
                    h, y, info.width * bpp, u, info.width * bpp, 2 * bpp, v, info.width * bpp, 2 * bpp, tenBit
                )
                if (t < 0) { assertEquals("clean end", -1L, t); break }
                pts += t
            }
            if (tenBit) {
                y.order(ByteOrder.LITTLE_ENDIAN)
                val sample = y.getShort(info.width * 2 * (info.height / 2) + 200).toInt() and 0xFFFF
                assertEquals("P010 keeps 10 bits in the MSBs", 0, sample and 0x3F)
            }
            FfmpegIngest.nativeClose(h)
            return info to pts
        }
    }

    @Test fun h264High422TenBitToP010() {
        val (info, pts) = decodeAll("h264_422_10bit.mov", tenBit = true)
        assertEquals(1080, info.width); assertEquals(1920, info.height)
        assertEquals(10, info.bits); assertEquals(422, info.chroma); assertEquals("CW degrees (ffmpeg -display_rotation 90 is CCW)", 270, info.rotation)
        assertTrue("frames ${pts.size}", pts.size in 75..85)
        assertTrue("monotonic pts", pts.zipWithNext().all { (a, b) -> b > a })
        assertTrue("VFR gap kept", pts.zipWithNext().any { (a, b) -> b - a > 40_000 })
        println("h264 4:2:2 10-bit: ${pts.size} frames, last pts ${pts.last()} us, ${FfmpegIngest.nativeVersion()}")
    }

    @Test fun hevcMain42210HlgToNv12() {
        val (info, pts) = decodeAll("hevc_422_10bit_hlg.mp4", tenBit = false)
        assertEquals(18, info.transfer); assertEquals(9, info.primaries); assertEquals(422, info.chroma)
        assertEquals(50, pts.size)
        println("hevc 4:2:2 10-bit HLG: ${pts.size} frames → NV12")
    }
}
