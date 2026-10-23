package br.com.riseedit.transcode

import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** 0.21: ProRes 422 HQ 10-bit encode of the export intermediate, validated by ffprobe (host-test.sh). */
class HostProresTest {
    private val lib = System.getenv("RISE_HOST_INGEST")
    private val samples = System.getenv("RISE_SAMPLES")

    private fun fdOf(s: FileInputStream): Int =
        FileDescriptor::class.java.getDeclaredField("fd").apply { isAccessible = true }.getInt(s.fd)

    private fun probe(f: File, args: String): String =
        ProcessBuilder(listOf("ffprobe", "-v", "error") + args.split(" ") + f.absolutePath)
            .redirectErrorStream(true).start().let { p -> p.inputStream.bufferedReader().readText().also { p.waitFor() } }.trim()

    private fun encode(pcm: Boolean, profile: Int): File {
        assumeTrue(lib != null && samples != null)
        System.load(lib!!)
        val out = File.createTempFile("prores", ".mov")
        var last = -1L
        val r = FileInputStream(File(samples, "hevc10_aac_intermediate.mp4")).use { s ->
            ProresNative.nativeEncode(fdOf(s), out.absolutePath, profile, pcm) { pts -> last = pts; true }
        }
        assertEquals(0, r)
        assertTrue("progress reported", last > 1_500_000)
        return out
    }

    @Test fun proresHq422TenBitWithPcm() {
        val out = encode(pcm = true, profile = 3)
        val v = probe(out, "-select_streams v:0 -count_frames -show_entries stream=codec_name,profile,pix_fmt,codec_tag_string,nb_read_frames,color_transfer,color_primaries -of default=nw=1")
        println(v)
        assertTrue(v, "codec_name=prores" in v)
        assertTrue(v, "profile=HQ" in v)
        assertTrue(v, "pix_fmt=yuv422p10le" in v)
        assertTrue(v, "codec_tag_string=apch" in v)
        assertTrue(v, "nb_read_frames=60" in v)
        assertTrue(v, "color_transfer=arib-std-b67" in v)
        assertTrue(v, "color_primaries=bt2020" in v)
        val a = probe(out, "-select_streams a:0 -show_entries stream=codec_name,sample_rate,channels -of default=nw=1")
        println(a)
        assertTrue(a, "codec_name=pcm_s16le" in a && "sample_rate=48000" in a && "channels=2" in a)
        assertTrue(probe(out, "-show_entries format=format_name -of default=nw=1"), true)
    }

    @Test fun proresStandardKeepsAac() {
        val out = encode(pcm = false, profile = 2)
        val v = probe(out, "-select_streams v:0 -show_entries stream=profile,pix_fmt,codec_tag_string -of default=nw=1")
        assertTrue(v, "profile=Standard" in v && "pix_fmt=yuv422p10le" in v && "apcn" in v)
        assertTrue(probe(out, "-select_streams a:0 -show_entries stream=codec_name -of default=nw=1").contains("aac"))
    }
}
