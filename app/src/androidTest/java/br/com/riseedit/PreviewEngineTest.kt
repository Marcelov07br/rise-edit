@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class, androidx.media3.common.util.ExperimentalApi::class])

package br.com.riseedit

import android.graphics.SurfaceTexture
import android.media.MediaCodecInfo.CodecProfileLevel
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline as MediaTimeline
import androidx.media3.common.util.Size
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.transformer.CompositionPlayer
import androidx.test.platform.app.InstrumentationRegistry
import br.com.riseedit.core.*
import br.com.riseedit.preview.PreviewEngine
import br.com.riseedit.preview.PreviewQuality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.Closeable
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

class PreviewEngineTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun fastPreviewUsesTrimmedClipsAndGlobalSeeksAcrossARealTransition() = withFixture { uri ->
        val first = clip(uri, "First", 500, 1_500)
        val second = clip(uri, "Second", 1_600, 2_600)
        val project = Project(listOf(first, second))
        PreviewSession().use { session ->
            session.awaitFrame("Fast 720p first frame", 0) {
                it.update(project, emptyMap(), emptyMap(), 0, PreviewQuality.Fast)
            }
            assertEquals(PreviewQuality.Fast, onMain { session.engine.quality })
            assertTrue(onMain { session.engine.player is ExoPlayer })
            assertEquals(2, onMain { session.engine.player.mediaItemCount })
            session.assertTimelineDuration(project.durationMs)

            session.awaitFrame("Global seek into second trimmed clip", 1) { it.seek(1_350) }
            session.assertPosition(1_350, second, 1_950)
            session.awaitFrame("Global seek back into first trimmed clip", 0) { it.seek(400) }
            session.assertPosition(400, first, 900)

            session.awaitFrame("Prepare natural clip transition", 0) { it.seek(700) }
            session.awaitFrame("Natural transition renders the second clip", 1) { it.togglePlay() }
            onMain { session.engine.pause() }
            assertEquals(second.id, onMain { session.engine.activeClip?.id })
            assertTrue(onMain { session.engine.positionMs in first.durationMs..project.durationMs })
            session.assertNoPlaybackErrors()
        }
    }

    @Test
    fun missingFirstAndMiddleSlotsKeepTheirDurationAndFollowingVideoRenders() = withFixture { uri ->
        val missingFirst = missingClip("Missing first", 600)
        val following = clip(uri, "Following missing first", 400, 1_300)
        PreviewSession().use { session ->
            val project = Project(listOf(missingFirst, following))
            session.awaitFrame("Real frame after missing first slot", 1) {
                it.update(project, emptyMap(), mapOf(missingFirst.id to MediaFault.AssetUnavailable(missingFirst.uri)), 0, PreviewQuality.Fast)
                it.togglePlay()
            }
            onMain { session.engine.pause() }
            session.assertTimelineDuration(project.durationMs)
            assertEquals(2, onMain { session.engine.player.mediaItemCount })
            assertEquals(following.id, onMain { session.engine.activeClip?.id })
            assertTrue(onMain { session.engine.positionMs >= missingFirst.durationMs })
            session.assertNoPlaybackErrors()
        }

        val first = clip(uri, "Before gap", 100, 700)
        val middle = missingClip("Missing middle", 500)
        val last = clip(uri, "After gap", 1_500, 2_400)
        val project = Project(listOf(first, middle, last))
        PreviewSession().use { session ->
            session.awaitFrame("Real frame before missing middle slot", 0) {
                it.update(project, emptyMap(), mapOf(middle.id to MediaFault.AssetUnavailable(middle.uri)), 0, PreviewQuality.Fast)
            }
            session.awaitFrame("Real frame after missing middle slot", 2) { it.togglePlay() }
            onMain { session.engine.pause() }
            session.assertTimelineDuration(project.durationMs)
            assertEquals(3, onMain { session.engine.player.mediaItemCount })
            assertEquals(last.id, onMain { session.engine.activeClip?.id })
            assertTrue(onMain { session.engine.positionMs >= first.durationMs + middle.durationMs })
            session.assertNoPlaybackErrors()
        }
    }

    @Test
    fun repeated720pScrubsProduceRealFramesAndRecordLatencyAndReportedDrops() = withFixture { uri ->
        PreviewSession().use { session ->
            val source = clip(uri, "720p scrub", 0, 3_000)
            session.awaitFrame("720p scrub initial frame", 0) {
                it.update(Project(listOf(source)), emptyMap(), emptyMap(), 0, PreviewQuality.Fast)
            }
            onMain { session.engine.scrubbing(true) }
            val latency = listOf(100L, 2_400L, 700L, 1_800L, 300L, 2_600L, 1_300L, 900L).map { position ->
                val elapsed = session.awaitFrame("720p scrub to ${position}ms", 0) { it.seek(position) }
                session.assertPosition(position, source, position)
                assertFalse("Scrubbing unexpectedly started playback", onMain { session.engine.player.isPlaying })
                elapsed
            }
            onMain { session.engine.scrubbing(false) }
            session.assertNoPlaybackErrors()
            Log.i(TAG, "720p scrub seek-to-surface-frame ms=$latency; surface frame callbacks=${session.output.frames.get()}; dropped-frame reports=${session.dropReports.get()}; Media3 reported dropped frames=${session.reportedDrops.get()}. These callbacks are not a physical-display FPS measurement.")
        }
    }

    @Test
    fun faithfulCompositionConstructionFailureFallsBackAndRetainsGlobalPosition() = withFixture { uri ->
        val stabilized = clip(uri, "Analysis unavailable", 400, 2_400)
            .copy(stabilization = Stabilization(enabled = true))
        PreviewSession().use { session ->
            // Empty analysis is a public composition precondition failure, not an injected player stub.
            onMain { session.engine.update(Project(listOf(stabilized)), emptyMap(), emptyMap(), 450, PreviewQuality.Faithful) }
            session.waitUntil("Faithful construction failure did not fall back") {
                session.messages.any { it.isNotBlank() } && onMain { session.engine.quality == PreviewQuality.Fast }
            }
            assertEquals(PreviewQuality.Fast, onMain { session.engine.quality })
            assertTrue(onMain { session.engine.player is ExoPlayer })
            session.assertPosition(450, stabilized, 850)
            assertTrue("Fallback needs an explanatory message", session.messages.any { it.isNotBlank() })
            session.awaitFrame("Real Fast frame after Faithful construction failure", 0) { it.seek(600) }
            session.assertPosition(600, stabilized, 1_000)
            session.assertNoPlaybackErrors()
        }
    }

    @Test
    fun playingFaithfulPreviewFallsBackOnMissingMediaPreflightAndContinues() = withFixture { uri ->
        val first = clip(uri, "Faithful playing", 0, 3_000)
        PreviewSession().use { session ->
            session.awaitFrame("Faithful actual first frame", 0, PreviewQuality.Faithful) {
                it.update(Project(listOf(first)), emptyMap(), emptyMap(), 0, PreviewQuality.Faithful)
            }
            assertEquals("Faithful playback must run before testing its fallback", PreviewQuality.Faithful, onMain { session.engine.quality })
            onMain { session.engine.togglePlay() }
            session.waitUntil("Faithful preview did not advance") { onMain { session.engine.positionMs >= 250 && session.engine.player.isPlaying } }
            val before = onMain { session.engine.positionMs }
            val unavailable = missingClip("Unavailable while playing", 600)
            val following = clip(uri, "Playable after fallback", 1_000, 1_900)
            val changed = Project(listOf(first, unavailable, following))
            session.awaitFrame("Playing Faithful preflight fallback frame", 0, PreviewQuality.Fast) {
                it.update(changed, emptyMap(), mapOf(unavailable.id to MediaFault.AssetUnavailable(unavailable.uri)), before, PreviewQuality.Faithful)
            }
            assertEquals(PreviewQuality.Fast, onMain { session.engine.quality })
            assertTrue("Fallback reset the global position", onMain { session.engine.positionMs >= before - 150 })
            session.waitUntil("Fallback did not preserve playback") { onMain { session.engine.player.isPlaying } }
            assertTrue("Fallback needs an explanatory message", session.messages.any { it.isNotBlank() })
            session.awaitFrame("Playable slot remains available after fallback", 2) {
                it.pause()
                it.seek(first.durationMs + unavailable.durationMs + 250)
            }
            session.assertPosition(first.durationMs + unavailable.durationMs + 250, following, 1_250)
            session.assertTimelineDuration(changed.durationMs)
            session.assertNoPlaybackErrors()
        }
    }

    @Test
    fun schema3MetadataAndProxyRoundTripAndSchema2DefaultsPreserveSpeedPoints() {
        val info = SourceInfo("video/hevc", 3_840, 2_160, CodecProfileLevel.HEVCProfileMain10,
            CodecProfileLevel.HEVCMainTierLevel51, ColorDepth.TEN, ChromaFormat.YUV420, true, 59.94f,
            "audio/opus", 2, 48_000, 90)
        val source = Clip(uri = "content://preview-test/original", name = "Metadata", sourceDurationMs = 3_000,
            startMs = 200, endMs = 2_800, speedPoints = listOf(SpeedPoint(200, 0.5), SpeedPoint(900, 1.5), SpeedPoint(1_900, 2.0)),
            sourceInfo = info, proxyUri = "file:///preview-test/main-proxy.mp4", proxyRequired = true)
        val overlay = source.copy(id = UUID.randomUUID().toString(), uri = "content://preview-test/overlay",
            sourceInfo = SourceInfo("video/avc", 1_920, 1_080, CodecProfileLevel.AVCProfileHigh422,
                CodecProfileLevel.AVCLevel41, ColorDepth.EIGHT, ChromaFormat.YUV422, false, 30f,
                rotationDegrees = 180), proxyUri = "file:///preview-test/overlay-proxy.mp4")
        val project = Project(listOf(source), name = "Schema compatibility", overlays = listOf(VideoOverlay(clip = overlay)))
        val store = ProjectStore(context)
        try {
            store.save(project)
            assertEquals(project, store.load(project.id))
            assertEquals(source.proxyUri, store.load(project.id).clips.single().effectiveUri())
            val file = File(context.filesDir, "projects/${project.id}.json")
            val json = JSONObject(file.readText())
            assertEquals(4, json.getInt("schema"))
            fun removeNewFields(clipJson: JSONObject) {
                clipJson.remove("sourceInfo")
                clipJson.remove("proxyUri")
                clipJson.remove("proxyRequired")
            }
            removeNewFields(json.getJSONArray("clips").getJSONObject(0))
            removeNewFields(json.getJSONArray("overlays").getJSONObject(0).getJSONObject("clip"))
            json.put("schema", 2)
            file.writeText(json.toString())
            val legacy = store.load(project.id)
            val expectedSource = source.copy(sourceInfo = null, proxyUri = null, proxyRequired = false)
            val expectedOverlay = overlay.copy(sourceInfo = null, proxyUri = null, proxyRequired = false)
            assertEquals(project.copy(clips = listOf(expectedSource), overlays = listOf(project.overlays.single().copy(clip = expectedOverlay))), legacy)
            assertEquals(source.speedPoints, legacy.clips.single().speedPoints)
            assertEquals(overlay.speedPoints, legacy.overlays.single().clip.speedPoints)
            assertEquals(source.uri, legacy.clips.single().effectiveUri())
        } finally { store.delete(project.id) }
    }

    private fun clip(uri: String, name: String, start: Long, end: Long) = Clip(uri = uri, name = name, sourceDurationMs = 3_000, startMs = start, endMs = end)

    private fun missingClip(name: String, duration: Long): Clip = Clip(
        uri = Uri.fromFile(File(context.cacheDir, "absent-${UUID.randomUUID()}.mp4")).toString(), name = name, sourceDurationMs = duration)

    private fun withFixture(block: (String) -> Unit) {
        val file = File.createTempFile("preview-720p-", ".mp4", context.cacheDir)
        try {
            instrumentation.context.assets.open("compatibility/h264_720p_scrub.mp4").use { input -> file.outputStream().use { input.copyTo(it) } }
            block(Uri.fromFile(file).toString())
        } finally { file.delete() }
    }

    private fun <T> onMain(block: () -> T): T {
        val value = AtomicReference<T>()
        val error = AtomicReference<Throwable?>()
        instrumentation.runOnMainSync { try { value.set(block()) } catch (failure: Throwable) { error.set(failure) } }
        error.get()?.let { throw it }
        return value.get()
    }

    private inner class PreviewSession : Closeable {
        val output = FrameSurface()
        val messages = CopyOnWriteArrayList<String>()
        val errors = CopyOnWriteArrayList<PlaybackException>()
        val reportedDrops = AtomicInteger()
        val dropReports = AtomicInteger()
        private val lastRenderedIndex = AtomicInteger(-1)
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        lateinit var engine: PreviewEngine
            private set
        private var bound: Player? = null
        private val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() { lastRenderedIndex.set(engine.player.currentMediaItemIndex) }
            override fun onPlayerError(error: PlaybackException) { errors.add(error) }
        }
        private val analytics = object : AnalyticsListener {
            override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
                reportedDrops.addAndGet(droppedFrames)
                dropReports.incrementAndGet()
                Log.i(TAG, "Media3 reported $droppedFrames dropped frames over ${elapsedMs}ms")
            }
        }
        init {
            onMain {
                engine = PreviewEngine(context, scope, onChanged = { if (::engine.isInitialized) bind() }, onMessage = { messages.add(it) })
                bind()
            }
        }
        private fun bind() {
            val next = engine.player
            if (next === bound) return
            bound?.removeListener(listener)
            (bound as? ExoPlayer)?.removeAnalyticsListener(analytics)
            bound = next
            lastRenderedIndex.set(-1)
            next.addListener(listener)
            (next as? ExoPlayer)?.addAnalyticsListener(analytics)
            if (next is CompositionPlayer) next.setVideoSurface(output.surface, Size(1_280, 720))
            else next.setVideoSurface(output.surface)
        }
        fun awaitFrame(label: String, index: Int, quality: PreviewQuality? = null, action: (PreviewEngine) -> Unit): Long {
            val baseline = output.frames.get()
            val started = SystemClock.elapsedRealtimeNanos()
            onMain { action(engine); bind() }
            waitUntil("$label: no real frame for media index $index") {
                output.failure.get()?.let { throw AssertionError("Frame consumer failed", it) }
                output.frames.get() > baseline && lastRenderedIndex.get() == index && onMain {
                    engine.player.currentMediaItemIndex == index && (quality == null || engine.quality == quality)
                }
            }
            val elapsed = (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000
            Log.i(TAG, "$label: update/seek-to-surface-frame=${elapsed}ms; physical first-frame target=1500ms (informational)")
            return elapsed
        }
        fun waitUntil(message: String, condition: () -> Boolean) {
            val deadline = SystemClock.elapsedRealtime() + 20_000
            while (SystemClock.elapsedRealtime() < deadline) {
                if (condition()) return
                Thread.sleep(10)
            }
            throw AssertionError("$message; errors=${errors.joinToString { it.stackTraceToString() }}; messages=$messages")
        }
        fun assertPosition(global: Long, expectedClip: Clip, source: Long) = onMain {
            assertEquals(expectedClip.id, engine.activeClip?.id)
            assertTrue("Global ${engine.positionMs}, expected $global", abs(engine.positionMs - global) <= 100)
            assertTrue("Source ${engine.activeSourceMs}, expected $source", abs(engine.activeSourceMs - source) <= 100)
        }
        fun timelineDuration(): Long = onMain {
            val timeline = engine.player.currentTimeline
            val window = MediaTimeline.Window()
            (0 until timeline.windowCount).sumOf { timeline.getWindow(it, window).durationMs }
        }
        fun assertTimelineDuration(expected: Long) {
            waitUntil("Timeline did not retain its $expected ms of logical slots") { timelineDuration() == expected }
            assertEquals(expected, timelineDuration())
        }
        fun assertNoPlaybackErrors() { assertTrue(errors.joinToString("\n") { it.stackTraceToString() }, errors.isEmpty()) }
        override fun close() {
            onMain { engine.release(); scope.cancel() }
            output.close()
        }
    }

    /** Consumes actual codec buffers on an EGL thread so longer playback cannot fill the surface queue. */
    private class FrameSurface : Closeable {
        val frames = AtomicInteger()
        val failure = AtomicReference<Throwable?>()
        private val thread = HandlerThread("PreviewTestFrames").apply { start() }
        private val handler = Handler(thread.looper)
        private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        private var pbuffer: EGLSurface = EGL14.EGL_NO_SURFACE
        private var texture: SurfaceTexture? = null
        private var textureName = 0
        lateinit var surface: Surface
            private set
        init {
            val ready = CountDownLatch(1)
            handler.post {
                try {
                    display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
                    val versions = IntArray(2)
                    check(EGL14.eglInitialize(display, versions, 0, versions, 1))
                    val configs = arrayOfNulls<EGLConfig>(1)
                    val count = IntArray(1)
                    check(EGL14.eglChooseConfig(display, intArrayOf(EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                        EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_RED_SIZE, 8,
                        EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_NONE), 0, configs, 0, 1, count, 0))
                    val config = requireNotNull(configs[0])
                    eglContext = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
                    pbuffer = EGL14.eglCreatePbufferSurface(display, config, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
                    check(EGL14.eglMakeCurrent(display, pbuffer, pbuffer, eglContext))
                    val names = IntArray(1)
                    GLES20.glGenTextures(1, names, 0)
                    textureName = names[0]
                    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureName)
                    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
                    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
                    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
                    texture = SurfaceTexture(textureName).apply {
                        setDefaultBufferSize(1_280, 720)
                        setOnFrameAvailableListener({ image ->
                            try { image.updateTexImage(); frames.incrementAndGet() }
                            catch (error: Throwable) { failure.compareAndSet(null, error) }
                        }, handler)
                    }
                    surface = Surface(requireNotNull(texture))
                } catch (error: Throwable) { failure.set(error) }
                finally { ready.countDown() }
            }
            check(ready.await(10, TimeUnit.SECONDS)) { "EGL surface initialization timed out" }
            failure.get()?.let { close(); throw AssertionError("Cannot create real preview surface", it) }
        }
        override fun close() {
            val closed = CountDownLatch(1)
            handler.post {
                try {
                    if (::surface.isInitialized) surface.release()
                    texture?.release()
                    if (textureName != 0) GLES20.glDeleteTextures(1, intArrayOf(textureName), 0)
                    EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                    if (pbuffer != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, pbuffer)
                    if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, eglContext)
                    if (display != EGL14.EGL_NO_DISPLAY) EGL14.eglTerminate(display)
                } finally { closed.countDown() }
            }
            check(closed.await(10, TimeUnit.SECONDS)) { "Preview surface cleanup timed out" }
            thread.quitSafely()
            thread.join(1_000)
        }
    }

    companion object { private const val TAG = "RisePreviewTest" }
}
