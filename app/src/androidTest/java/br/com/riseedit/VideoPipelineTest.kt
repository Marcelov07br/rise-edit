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
        val project = Project(listOf(stabilized)).split(0, 1000).copy(
            audio = listOf(AudioClip(uri = clip.uri, name = "Music", sourceDurationMs = 3000, offsetMs = 300, endMs = 1500, track = 2, volume = 0.25f)),
            texts = listOf(TextLayer(text = "RISE", startMs = 100, endMs = 1800)),
            overlays = listOf(VideoOverlay(clip = clip.copy(startMs = 500, endMs = 1500), offsetMs = 500, track = 1)))
        store.save(project); assertEquals(project, store.load())
        val failures = mutableListOf<String>()
        val variants = listOf("base" to project.copy(audio=emptyList(),texts=emptyList(),overlays=emptyList()),
            "audio-text" to project.copy(overlays=emptyList()), "full" to project)
        for ((variant, compositionProject) in variants) {
        androidx.media3.effect.DebugTraceUtil.enableTracing=true
        androidx.media3.effect.DebugTraceUtil.reset()
        val destination = File(context.cacheDir, "pipeline-$variant.mp4").apply { delete() }
        val done = CountDownLatch(1)
        var failure: Exception? = null
        var engine: Transformer? = null
        instrumentation.runOnMainSync {
            engine = Transformer.Builder(context).setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) { done.countDown() }
                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) { failure = exportException; done.countDown() }
                }).build()
            engine!!.start(VideoEngine.composition(compositionProject, mapOf(clip.uri to analysis)), destination.absolutePath)
        }
        val completed = done.await(90, TimeUnit.SECONDS)
        if (!completed) instrumentation.runOnMainSync { engine?.cancel() }
        if (!completed || failure!=null) {
            val reason="$variant: ${failure?.stackTraceToString() ?: "Export timed out"}"
            android.util.Log.e("RisePipeline",reason)
            android.util.Log.e("RisePipeline",androidx.media3.effect.DebugTraceUtil.generateTraceSummary())
            failures.add(reason);continue
        }
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
        android.util.Log.i("RisePipeline","$variant export passed")
        }
        androidx.media3.effect.DebugTraceUtil.enableTracing=false
        assertTrue(failures.joinToString("\n"),failures.isEmpty())
        assertEquals(originalHash, source.readBytes().contentHashCode())
        val manager = UpdateManager(context)
        manager.saveToken("synthetic-test-value")
        assertTrue(manager.hasToken); manager.forgetToken(); assertFalse(manager.hasToken)
    }
    @Test fun projectLibraryRoundTripAndIsolation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = ProjectStore(context)
        val clip = Clip(uri = "content://test/clip", name = "Clip", sourceDurationMs = 10000, startMs = 2000, endMs = 8000,
            speedPoints = listOf(SpeedPoint(2000,0.5),SpeedPoint(4000,2.0)), adjustments = Adjustments(0.1f,0.2f,0.3f,-0.2f),
            keyframes = listOf(TransformKeyframe(2000,x=0.2f,scale=1.2f,opacity=0.5f)))
        val p = Project(listOf(clip),name="Persistence test",favorite=true,aspectRatio=9f/16f,export=ExportSettings(1080,24,16_000_000),
            audio=listOf(AudioClip(uri="content://test/audio",name="Audio",sourceDurationMs=5000,track=2)),
            texts=listOf(TextLayer(text="Title")),overlays=listOf(VideoOverlay(clip=clip,track=1)))
        val duplicate=p.copy(id=java.util.UUID.randomUUID().toString(),name="Copy")
        try {
            store.save(p);store.save(duplicate)
            assertEquals(p,store.load(p.id));assertEquals(duplicate,store.load(duplicate.id))
            store.delete(duplicate.id);assertEquals(p,store.load(p.id));assertTrue(store.list().any {it.id==p.id})
        } finally {store.delete(p.id);store.delete(duplicate.id)}
    }
}
