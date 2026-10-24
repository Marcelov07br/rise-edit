package br.com.riseedit.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import br.com.riseedit.core.Clip
import br.com.riseedit.core.motion.Reframe
import br.com.riseedit.core.motion.SubjectSample
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * 0.24.0 automatic reframe: samples frames of the clip (every ~200 ms, ≤600 frames) and finds the
 * subject with ML Kit face detection — bundled model (com.google.mlkit:face-detection), so it runs
 * on-device with no network and no Play Services download. Subject = faces weighted by area
 * (the speaker dominates); no face → null (Reframe holds the neighbors).
 */
internal class SubjectDetector(private val context: Context) {
    suspend fun samples(clip: Clip, progress: (Float) -> Unit): List<SubjectSample> =
        withContext(Dispatchers.Default) {
            val options = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setMinFaceSize(0.08f)
                .build()
            val detector = FaceDetection.getClient(options)
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(context, Uri.parse(clip.effectiveUri(true)))
                val upright = UprightFrames(r)
                val times = Reframe.sampleTimes(clip.startMs, clip.endMs)
                times.mapIndexed { i, t ->
                    coroutineContext.ensureActive()
                    progress(i.toFloat() / times.size)
                    val raw = r.getScaledFrameAtTime(t * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 640, 640)
                        ?: return@mapIndexed SubjectSample(t, null)
                    val bmp = upright.fix(raw)
                    val faces = Tasks.await(detector.process(InputImage.fromBitmap(bmp, 0)))
                    val total = faces.sumOf { it.boundingBox.width().toDouble() * it.boundingBox.height() }
                    val center = if (faces.isEmpty() || total <= 0) {
                        null
                    } else {
                        (faces.sumOf { f -> f.boundingBox.exactCenterX().toDouble() * f.boundingBox.width() * f.boundingBox.height() } / total / bmp.width).toFloat()
                    }
                    bmp.recycle()
                    SubjectSample(t, center, faces.size.toFloat())
                }
            } finally {
                r.release()
                detector.close()
            }
        }
}
