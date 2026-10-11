@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class, androidx.media3.common.util.ExperimentalApi::class])
package br.com.riseedit

import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.*
import androidx.test.platform.app.InstrumentationRegistry
import br.com.riseedit.core.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class VideoPipelineTest {
    @Test fun analyzeSplitAndExportRealVideoWithAudio() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val source = File(context.cacheDir, "synthetic-shaky.mp4")
        instrumentation.context.assets.open("shaky.mp4").use { input -> source.outputStream().use { input.copyTo(it) } }
        val originalHash = source.readBytes().contentHashCode()
        val clip = Clip(uri = Uri.fromFile(source).toString(), name = "Synthetic", sourceDurationMs = 3000, startMs = 500, endMs = 2500)
        val analysis = MotionAnalyzer(context).analyze(clip) {}
        assertTrue(analysis.samples.size >= 35)
        assertTrue(analysis.samples.any { kotlin.math.abs(it.pose.x) > 0.5 || kotlin.math.abs(it.pose.y) > 0.5 })
        val store = ProjectStore(context)
        store.saveAnalysis(clip.uri, analysis)
        assertEquals(analysis, store.loadAnalysis(clip.uri))
        val stabilized = clip.copy(stabilization = Stabilization(enabled = true, strength = 1.0, maxZoom = 1.15))
        val project = Project(listOf(stabilized)).split(0, 1000)
        store.save(project); assertEquals(project, store.load())
        val destination = File(context.cacheDir, "pipeline-result.mp4").apply { delete() }
        val done = CountDownLatch(1)
        var failure: Exception? = null
        var engine: Transformer? = null
        instrumentation.runOnMainSync {
            engine = Transformer.Builder(context).setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) { done.countDown() }
                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) { failure = exportException; done.countDown() }
                }).build()
            engine!!.start(VideoEngine.composition(project, mapOf(clip.uri to analysis)), destination.absolutePath)
        }
        val completed = done.await(90, TimeUnit.SECONDS)
        if (!completed) instrumentation.runOnMainSync { engine?.cancel() }
        assertTrue("Export timed out", completed)
        failure?.let { throw it }
        assertTrue(destination.length() > 1000)
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(destination.absolutePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
            assertTrue("Unexpected duration $duration", duration in 1900..2150)
            assertEquals("yes", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
            assertEquals("720", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT))
            assertNotNull(retriever.getFrameAtTime(1_000_000))
        } finally { retriever.release() }
        assertEquals(originalHash, source.readBytes().contentHashCode())
        val manager = UpdateManager(context)
        manager.saveToken("synthetic-test-value")
        assertTrue(manager.hasToken); manager.forgetToken(); assertFalse(manager.hasToken)
    }
}
