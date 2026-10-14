package br.com.riseedit

import android.net.Uri
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import br.com.riseedit.motion.CodecFrameSource
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opencv.android.OpenCVLoader

/** Sequential MediaCodec luma decode used by stabilization analysis, across the codec corpus. */
class MotionDecodeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun fixture(asset: String): Uri {
        val file =
            File(instrumentation.targetContext.cacheDir, "motion-${asset.substringAfterLast('/')}")
        instrumentation.context.assets.open(asset).use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        return Uri.fromFile(file)
    }

    private fun decode(asset: String): Triple<Int, Int, Int> = runBlocking {
        var frames = 0
        var width = 0
        var height = 0
        var last = -1L
        val started = System.nanoTime()
        CodecFrameSource(instrumentation.targetContext, fixture(asset), 0).read(30, {
        }) { time, gray ->
            assertTrue("frame times must increase", time > last)
            last = time
            frames++
            width = gray.cols()
            height = gray.rows()
        }
        val ms = (System.nanoTime() - started) / 1_000_000
        Log.i("RiseMotion", "decode $asset: $frames frames ${width}x$height in ${ms}ms")
        Triple(frames, width, height)
    }

    @Test fun decodesH264AndHevcLumaDownscaled() {
        assertTrue(OpenCVLoader.initLocal())
        for (asset in listOf(
            "shaky.mp4",
            "compatibility/h264_8bit_aac.mp4",
            "compatibility/hevc_8bit.mkv"
        )) {
            val (frames, width, height) = decode(asset)
            assertTrue("$asset frames=$frames", frames >= 10)
            assertTrue(
                "$asset size=${width}x$height",
                maxOf(width, height) <= 640 && minOf(width, height) > 0
            )
        }
    }

    @Test fun tenBitHdrAndAv1EitherDecodeOrFailCleanly() {
        assertTrue(OpenCVLoader.initLocal())
        val assets =
            listOf(
                "compatibility/hevc_10bit.mkv",
                "compatibility/hevc_hdr10.mkv",
                "compatibility/av1_8bit.mkv",
                "compatibility/vp9_8bit.webm"
            )
        val outcomes = assets.map { asset ->
            asset to
                runCatching {
                    decode(asset)
                }.fold({ "ok ${it.first} frames" }, { "fallback: ${it.javaClass.simpleName}" })
        }
        outcomes.forEach { Log.i("RiseMotion", "${it.first}: ${it.second}") }
        assertEquals(assets.size, outcomes.size)
    }

    @Test fun analyzerUsesCacheOnSecondRun() = runBlocking {
        val context = instrumentation.targetContext
        val uri = fixture("shaky.mp4")
        val clip = br.com.riseedit.core.Clip(
            uri = uri.toString(),
            name = "s",
            sourceDurationMs = 3000
        )
        val analyzer = MotionAnalyzer(context)
        val first = mutableListOf<br.com.riseedit.core.MotionAnalysisEvent>()
        analyzer.analysisFlow(clip).collect { first += it }
        val second = mutableListOf<br.com.riseedit.core.MotionAnalysisEvent>()
        analyzer.analysisFlow(clip).collect { second += it }
        val done = second.last() as br.com.riseedit.core.MotionAnalysisEvent.Finished
        assertTrue(done.fromCache)
        assertEquals(
            (first.last() as br.com.riseedit.core.MotionAnalysisEvent.Finished).analysis,
            done.analysis
        )
    }
}
