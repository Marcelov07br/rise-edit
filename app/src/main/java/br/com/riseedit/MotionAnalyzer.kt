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

/** Experimental rigid global tracking, 12 samples/s; no optical-flow mesh or perspective warp. */
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
            retriever.setDataSource(context, Uri.parse(clip.uri))
            var time = 0L
            while (time < clip.sourceDurationMs) {
                currentCoroutineContext().ensureActive()
                val bitmap = retriever.getScaledFrameAtTime(time * 1000, MediaMetadataRetriever.OPTION_CLOSEST, 320, 320)
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
                time += 83
            }
            samples.add(MotionSample(clip.sourceDurationMs, pose, segment))
            check(unreliable < samples.size * 0.5) { "Rastreamento insuficiente: pouca textura, cortes ou movimento rápido." }
            progress(1f)
            return MotionAnalysis(width, height, samples, unreliable)
        } finally { previous?.release(); retriever.release() }
    }

    private fun estimate(previous: Mat, current: Mat): Pose? {
        val corners = MatOfPoint(); val p0 = MatOfPoint2f(); val p1 = MatOfPoint2f()
        val status = MatOfByte(); val errors = MatOfFloat(); val inliers = Mat()
        val good0 = MatOfPoint2f(); val good1 = MatOfPoint2f()
        var affine: Mat? = null
        try {
            Imgproc.goodFeaturesToTrack(previous, corners, 180, 0.015, 8.0)
            if (corners.rows() < 12) return null
            p0.fromArray(*corners.toArray())
            Video.calcOpticalFlowPyrLK(previous, current, p0, p1, status, errors)
            val a = p0.toArray(); val b = p1.toArray(); val st = status.toArray(); val err = errors.toArray()
            val indices = a.indices.filter { st[it].toInt() != 0 && err[it] < 30f && b[it].x >= 0 && b[it].x < current.cols() && b[it].y >= 0 && b[it].y < current.rows() }
            if (indices.size < 12) return null
            good0.fromArray(*indices.map { a[it] }.toTypedArray()); good1.fromArray(*indices.map { b[it] }.toTypedArray())
            affine = Calib3d.estimateAffinePartial2D(good0, good1, inliers, Calib3d.RANSAC, 2.5, 1500, 0.99, 10)
            if (affine.empty() || Core.countNonZero(inliers) < max(10, indices.size / 2)) return null
            val aa = affine.get(0, 0)[0]; val bb = affine.get(1, 0)[0]
            val angle = atan2(bb, aa); val scale = hypot(aa, bb)
            val cx = current.cols() / 2.0; val cy = current.rows() / 2.0
            val x = affine.get(0, 2)[0] + (aa - 1) * cx - bb * cy
            val y = affine.get(1, 2)[0] + bb * cx + (aa - 1) * cy
            if (!x.isFinite() || !y.isFinite() || scale !in 0.95..1.05 || abs(angle) > 0.15 || hypot(x, y) > current.cols() * 0.18) return null
            return Pose(x, y, angle)
        } finally {
            listOf(corners, p0, p1, status, errors, inliers, good0, good1).forEach { it.release() }; affine?.release()
        }
    }
}
