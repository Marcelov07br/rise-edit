@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package br.com.riseedit

import android.graphics.SurfaceTexture
import android.media.MediaCodecList
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import android.view.Surface
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import br.com.riseedit.core.ChromaFormat
import br.com.riseedit.core.ColorDepth
import br.com.riseedit.core.MediaFault
import br.com.riseedit.core.ProxySpec
import br.com.riseedit.media.MediaProbe
import br.com.riseedit.media.ProbeResult
import br.com.riseedit.media.ProxyTranscoder
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Real, tiny encoded streams; decoder availability is a property of the test device. */
class MediaCompatibilityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val target = ProxySpec(128, 72, 1_000_000, 30)

    @Test
    fun probeVideoCorpusRetainsEncodedSemantics() = runBlocking {
        for (fixture in videos) withFixture(fixture.name) { _, uri ->
            val result = MediaProbe.probe(context, uri)
            assertNull("${fixture.name}: ${result.fault}", result.fault)
            val info = requireNotNull(result.sourceInfo) { "${fixture.name}: video track was not read" }
            assertEquals(fixture.name, fixture.mime, info.mime)
            assertEquals(fixture.name, 160, info.width)
            assertEquals(fixture.name, 90, info.height)
            assertTrue("${fixture.name}: duration ${result.durationMs}", result.durationMs in 900..1_150)
            assertTrue("${fixture.name}: frame rate ${info.frameRate}", info.frameRate == 0f || info.frameRate in 11.5f..12.5f)
            assertTrue("${fixture.name}: fabricated bit depth ${info.bitDepth}", info.bitDepth in setOf(fixture.depth, ColorDepth.UNKNOWN))
            assertTrue("${fixture.name}: fabricated chroma ${info.chroma}", info.chroma in setOf(fixture.chroma, ChromaFormat.UNKNOWN))
            assertEquals("${fixture.name}: HDR source signalling", fixture.hdr, info.isHdr)
            assertTrue(info.profile >= 0 && info.level >= 0)
            assertEquals(fixture.name, result.decoderFound, !result.decoderName.isNullOrBlank())
            assertEquals(fixture.name, fixture.audioMime, result.audioMime)
            assertEquals(fixture.name, fixture.audioMime, info.audioMime)
            assertEquals(fixture.name, fixture.audioMime != null, result.hasAudio)
            assertEquals(fixture.name, result.audioDecoderFound, !result.audioDecoderName.isNullOrBlank())
            if (fixture.audioMime != null) {
                assertEquals(1, info.audioChannels)
                assertEquals(48_000, info.audioSampleRate)
            }
            Log.i(TAG, "${fixture.name}: $info; video decoder=${result.decoderName}; audio decoder=${result.audioDecoderName}")
        }
    }

    @Test
    fun probeAudioCorpusDoesNotInventVideoMetadata() = runBlocking {
        for ((name, mime) in audios) withFixture(name) { _, uri ->
            val result = MediaProbe.probe(context, uri)
            assertNull("$name: ${result.fault}", result.fault)
            assertNull("$name must not have invented video metadata", result.sourceInfo)
            assertFalse(result.decoderFound)
            assertNull(result.decoderName)
            assertTrue(result.hasAudio)
            assertEquals(name, mime, result.audioMime)
            assertEquals(name, result.audioDecoderFound, !result.audioDecoderName.isNullOrBlank())
            assertTrue("$name: duration ${result.durationMs}", result.durationMs in 900..1_150)
            Log.i(TAG, "$name: audio decoder=${result.audioDecoderName}")
        }
    }

    @Test
    fun forcedProxyIsH264AndRendersFirstFrame() = runBlocking {
        // Changed dimensions force real decoding and encoding, including for an H.264 input.
        val candidates = videos.filter { it.depth == ColorDepth.EIGHT && it.chroma == ChromaFormat.YUV420 && !it.hdr }
        var converted = 0
        for (fixture in candidates) withFixture(fixture.name) { source, uri ->
            val original = source.readBytes()
            val probe = MediaProbe.probe(context, uri)
            assertNull("${fixture.name}: ${probe.fault}", probe.fault)
            val info = requireNotNull(probe.sourceInfo)
            val transcoder = ProxyTranscoder(context)
            if (!probe.decoderFound || (probe.hasAudio && !probe.audioDecoderFound)) {
                assertMissingDecoder(uri, probe, transcoder)
            } else if (!hasH264Encoder()) {
                val fault = expectMediaFault { transcoder.prepare(uri, info, target) }
                assertTrue("Expected an explicit encoder failure, got $fault", fault is MediaFault.ProxyFailed)
                assertActionable(fault)
            } else {
                val proxy = withTimeout(90_000) { transcoder.prepare(uri, info, target) }
                try {
                    assertNotEquals(uri, proxy)
                    assertEquals("file", proxy.scheme)
                    val output = File(requireNotNull(proxy.path))
                    assertTrue("${fixture.name}: empty proxy", output.length() > 1_000)
                    val proxyProbe = MediaProbe.probe(context, proxy)
                    assertNull("${fixture.name}: ${proxyProbe.fault}", proxyProbe.fault)
                    val outputInfo = requireNotNull(proxyProbe.sourceInfo)
                    assertEquals(MimeTypes.VIDEO_H264, outputInfo.mime)
                    assertEquals(target.width, outputInfo.width)
                    assertEquals(target.height, outputInfo.height)
                    assertTrue(outputInfo.bitDepth in setOf(ColorDepth.EIGHT, ColorDepth.UNKNOWN))
                    assertTrue(outputInfo.chroma in setOf(ChromaFormat.YUV420, ChromaFormat.UNKNOWN))
                    assertFalse(outputInfo.isHdr)
                    assertTrue(proxyProbe.decoderFound)
                    assertTrue("Unexpected proxy duration ${proxyProbe.durationMs}", proxyProbe.durationMs in 750..1_250)
                    if (probe.hasAudio) {
                        assertEquals(MimeTypes.AUDIO_AAC, proxyProbe.audioMime)
                        assertEquals(2, outputInfo.audioChannels)
                        assertEquals(48_000, outputInfo.audioSampleRate)
                    }
                    assertRendersFirstFrame(proxy, fixture.name)
                    converted++
                    Log.i(TAG, "${fixture.name}: actual proxy ${output.length()} bytes rendered by ExoPlayer")
                } finally {
                    proxy.path?.let { File(it).delete() }
                }
            }
            assertArrayEquals("${fixture.name}: source changed", original, source.readBytes())
        }
        assumeTrue("No complete native codec path was available; no proxy playback was validated", converted > 0)
    }

    @Test
    fun missingNativeDecoderIsRejectedBeforeConversion() = runBlocking {
        var rejected = 0
        for (fixture in videos) withFixture(fixture.name) { _, uri ->
            val probe = MediaProbe.probe(context, uri)
            assertNull("${fixture.name}: ${probe.fault}", probe.fault)
            assertNotNull(probe.sourceInfo)
            if (!probe.decoderFound || (probe.hasAudio && !probe.audioDecoderFound)) {
                assertMissingDecoder(uri, probe, ProxyTranscoder(context))
                rejected++
            }
        }
        // A future device may support every fixture; do not falsely label its codecs unsupported.
        Log.i(TAG, "Missing-decoder rejection exercised for $rejected source(s); availability is device-specific")
        assumeTrue("Every corpus source has a native decoder; the missing-decoder branch was not exercised", rejected > 0)
    }

    @Test
    fun concurrentRequestsReuseTheCompletedProxy() = runBlocking {
        withFixture("h264_8bit_aac.mp4") { _, uri ->
            val probe = MediaProbe.probe(context, uri)
            assertNull(probe.fault)
            val info = requireNotNull(probe.sourceInfo)
            if (!probe.decoderFound || !probe.audioDecoderFound) {
                assertMissingDecoder(uri, probe, ProxyTranscoder(context))
                assumeTrue("Native H.264/AAC input decoder unavailable; concurrent conversion was not validated", false)
                return@withFixture
            }
            if (!hasH264Encoder()) {
                val fault = expectMediaFault { ProxyTranscoder(context).prepare(uri, info, target) }
                assertTrue(fault is MediaFault.ProxyFailed)
                assertActionable(fault)
                assumeTrue("Native H.264 encoder unavailable; concurrent conversion was not validated", false)
                return@withFixture
            }
            val outputs = withTimeout(90_000) {
                coroutineScope {
                    val first = async { ProxyTranscoder(context).prepare(uri, info, target) }
                    val second = async { ProxyTranscoder(context).prepare(uri, info, target) }
                    listOf(first.await(), second.await())
                }
            }
            try {
                assertEquals("Concurrent requests published different proxies", outputs[0], outputs[1])
                assertTrue(File(requireNotNull(outputs[0].path)).length() > 1_000)
                assertRendersFirstFrame(outputs[0], "concurrent proxy")
            } finally {
                outputs.distinct().forEach { it.path?.let { path -> File(path).delete() } }
            }
        }
    }

    private suspend fun assertMissingDecoder(uri: Uri, probe: ProbeResult, transcoder: ProxyTranscoder) {
        val fault = expectMediaFault { transcoder.prepare(uri, requireNotNull(probe.sourceInfo), target) }
        assertTrue("Missing native input decoder must be reported explicitly, got $fault", fault is MediaFault.MissingDecoder)
        assertActionable(fault)
    }

    private suspend fun expectMediaFault(block: suspend () -> Uri): MediaFault = try {
        block()
        throw AssertionError("Conversion unexpectedly succeeded without the required native codec")
    } catch (fault: MediaFault) {
        fault
    }

    private fun assertActionable(fault: MediaFault) {
        assertTrue("Missing user-facing failure message", fault.userMessage.isNotBlank())
        assertTrue("Missing recovery action", fault.suggestedAction.isNotBlank())
    }

    private fun hasH264Encoder(): Boolean = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        .findEncoderForFormat(MediaFormat.createVideoFormat(MimeTypes.VIDEO_H264, target.width, target.height)) != null

    private fun assertRendersFirstFrame(uri: Uri, name: String) {
        val rendered = CountDownLatch(1)
        val failure = AtomicReference<PlaybackException?>(null)
        var player: ExoPlayer? = null
        var texture: SurfaceTexture? = null
        var surface: Surface? = null
        try {
            instrumentation.runOnMainSync {
                // Detached texture supplies a real codec surface without launching or changing app UI.
                texture = SurfaceTexture(false).apply { setDefaultBufferSize(target.width, target.height) }
                surface = Surface(requireNotNull(texture))
                player = ExoPlayer.Builder(context).build().apply {
                    addListener(object : Player.Listener {
                        override fun onRenderedFirstFrame() { rendered.countDown() }
                        override fun onPlayerError(error: PlaybackException) {
                            failure.set(error)
                            rendered.countDown()
                        }
                    })
                    setVideoSurface(surface)
                    setMediaItem(MediaItem.fromUri(uri))
                    prepare()
                    playWhenReady = true
                }
            }
            val completed = rendered.await(20, TimeUnit.SECONDS)
            assertNull("$name: ExoPlayer failed: ${failure.get()?.stackTraceToString()}", failure.get())
            assertTrue("$name: ExoPlayer did not render its first frame", completed)
        } finally {
            instrumentation.runOnMainSync {
                player?.release()
                surface?.release()
                texture?.release()
            }
        }
    }

    private suspend fun <T> withFixture(name: String, block: suspend (File, Uri) -> T): T {
        val source = File.createTempFile("compat-${name.substringBeforeLast('.')}-", ".${name.substringAfterLast('.')}", context.cacheDir)
        return try {
            instrumentation.context.assets.open("compatibility/$name").use { input ->
                source.outputStream().use { input.copyTo(it) }
            }
            block(source, Uri.fromFile(source))
        } finally {
            source.delete()
        }
    }

    private data class VideoFixture(
        val name: String,
        val mime: String,
        val depth: ColorDepth,
        val chroma: ChromaFormat = ChromaFormat.YUV420,
        val hdr: Boolean = false,
        val audioMime: String? = null
    )

    companion object {
        private const val TAG = "RiseCompatibility"
        private val videos = listOf(
            VideoFixture("h264_8bit_aac.mp4", MimeTypes.VIDEO_H264, ColorDepth.EIGHT, audioMime = MimeTypes.AUDIO_AAC),
            VideoFixture("h264_10bit.mp4", MimeTypes.VIDEO_H264, ColorDepth.TEN),
            VideoFixture("h264_422.mkv", MimeTypes.VIDEO_H264, ColorDepth.EIGHT, ChromaFormat.YUV422),
            VideoFixture("hevc_8bit.mkv", MimeTypes.VIDEO_H265, ColorDepth.EIGHT),
            VideoFixture("hevc_10bit.mkv", MimeTypes.VIDEO_H265, ColorDepth.TEN),
            VideoFixture("hevc_hdr10.mkv", MimeTypes.VIDEO_H265, ColorDepth.TEN, hdr = true),
            VideoFixture("vp9_8bit.webm", MimeTypes.VIDEO_VP9, ColorDepth.EIGHT),
            VideoFixture("av1_8bit.mkv", MimeTypes.VIDEO_AV1, ColorDepth.EIGHT)
        )
        private val audios = listOf("mp3.mp3" to MimeTypes.AUDIO_MPEG, "opus.ogg" to MimeTypes.AUDIO_OPUS, "pcm_s16le.wav" to MimeTypes.AUDIO_RAW)
    }
}
