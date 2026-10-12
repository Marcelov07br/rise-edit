package br.com.riseedit

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import br.com.riseedit.core.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.calib3d.Calib3d
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.video.Video
import kotlin.math.*

/** Bidirectional pyramidal flow and RANSAC; global rigid correction, not Adobe's mesh warp. */
class MotionAnalyzer(private val context: Context) {
    suspend fun analyze(clip: Clip, progress: (Float) -> Unit): MotionAnalysis {
        require(clip.sourceDurationMs <= 120_000) { "Nesta versão, analise vídeos de até 2 minutos." }
        check(OpenCVLoader.initLocal()) { "OpenCV não pôde iniciar neste aparelho." }
        val retriever = MediaMetadataRetriever()
        var previous: Mat? = null
        val samples = mutableListOf<MotionSample>()
        var pose = Pose(); var segment = 0; var unreliable = 0
        var width = 0; var height = 0
        try {
            retriever.setDataSource(context, Uri.parse(clip.effectiveUri(preferProxy = true)))
            var time = 0L
            while (time < clip.sourceDurationMs) {
                currentCoroutineContext().ensureActive()
                val bitmap = retriever.getScaledFrameAtTime(time * 1000, MediaMetadataRetriever.OPTION_CLOSEST, 640, 640)
                    ?: error("Não foi possível decodificar o quadro em ${time}ms.")
                val rgba = Mat(); val gray = Mat()
                try { Utils.bitmapToMat(bitmap, rgba); Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY) }
                finally { bitmap.recycle(); rgba.release() }
                width = gray.cols(); height = gray.rows()
                val prev = previous
                if (prev != null) {
                    val delta = estimate(prev, gray)
                    if (delta == null) { unreliable++; segment++; pose = Pose() }
                    else pose = delta.compose(pose)
                }
                samples.add(MotionSample(time, pose, segment))
                previous?.release(); previous = gray
                progress(time.toFloat() / clip.sourceDurationMs)
                time += 42
            }
            samples.add(MotionSample(clip.sourceDurationMs, pose, segment))
            if (unreliable >= samples.size * 0.5) throw br.com.riseedit.core.MediaFault.StabilizationNoTexture
            progress(1f)
            return MotionAnalysis(width, height, samples, unreliable)
        } finally { previous?.release(); retriever.release() }
    }

    private fun estimate(previous: Mat, current: Mat): Pose? {
        val corners = MatOfPoint(); val p0 = MatOfPoint2f(); val p1 = MatOfPoint2f()
        val status = MatOfByte(); val errors = MatOfFloat(); val inliers = Mat()
        val good0 = MatOfPoint2f(); val good1 = MatOfPoint2f()
        val back = MatOfPoint2f(); val backStatus = MatOfByte(); val backErrors = MatOfFloat()
        var affine: Mat? = null
        try {
            Imgproc.goodFeaturesToTrack(previous, corners, 400, 0.01, 12.0)
            if (corners.rows() < 12) return null
            p0.fromArray(*corners.toArray())
            Video.calcOpticalFlowPyrLK(previous, current, p0, p1, status, errors)
            Video.calcOpticalFlowPyrLK(current, previous, p1, back, backStatus, backErrors)
            val a = p0.toArray(); val b = p1.toArray(); val st = status.toArray(); val err = errors.toArray()
            val reverse = back.toArray(); val reverseStatus = backStatus.toArray()
            val indices = a.indices.filter { st[it].toInt() != 0 && reverseStatus[it].toInt() != 0 && err[it] < 30f &&
                hypot(a[it].x-reverse[it].x,a[it].y-reverse[it].y) < 1.5 && b[it].x >= 0 && b[it].x < current.cols() && b[it].y >= 0 && b[it].y < current.rows() }
            if (indices.size < 12) return null
            good0.fromArray(*indices.map { a[it] }.toTypedArray()); good1.fromArray(*indices.map { b[it] }.toTypedArray())
            affine = Calib3d.estimateAffinePartial2D(good0, good1, inliers, Calib3d.RANSAC, 2.0, 2500, 0.995, 10)
            if (affine.empty() || Core.countNonZero(inliers) < max(10, indices.size / 2)) return null
            val coverage = indices.indices.filter { inliers.get(it,0)[0] > 0 }.map { i ->
                val point=a[indices[i]]
                (if(point.x >= previous.cols()/2.0) 1 else 0) + (if(point.y >= previous.rows()/2.0) 2 else 0)
            }.toSet()
            if (coverage.size < 3) return null // Avoid tracking only a small foreground subject.
            val aa = affine.get(0, 0)[0]; val bb = affine.get(1, 0)[0]
            val angle = atan2(bb, aa); val scale = hypot(aa, bb)
            val cx = current.cols() / 2.0; val cy = current.rows() / 2.0
            val x = affine.get(0, 2)[0] + (aa - 1) * cx - bb * cy
            val y = affine.get(1, 2)[0] + bb * cx + (aa - 1) * cy
            if (!x.isFinite() || !y.isFinite() || scale !in 0.95..1.05 || abs(angle) > 0.15 || hypot(x, y) > current.cols() * 0.18) return null
            return Pose(x, y, angle)
        } finally {
            listOf(corners, p0, p1, status, errors, inliers, good0, good1, back, backStatus, backErrors).forEach { it.release() }; affine?.release()
        }
    }
}
