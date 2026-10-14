package br.com.riseedit.motion

import br.com.riseedit.core.Pose
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import org.opencv.calib3d.Calib3d
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.MatOfFloat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.imgproc.Imgproc
import org.opencv.video.Video

/** Bidirectional pyramidal flow and RANSAC; global rigid motion between two luma frames. */
internal object FeatureTracker {
    private const val MAX_SHIFT = 0.18

    @Suppress("CyclomaticComplexMethod", "ReturnCount", "LongMethod", "MagicNumber")
    fun estimate(previous: Mat, current: Mat): Pose? {
        val corners = MatOfPoint()
        val p0 = MatOfPoint2f()
        val p1 = MatOfPoint2f()
        val status = MatOfByte()
        val errors = MatOfFloat()
        val inliers = Mat()
        val good0 = MatOfPoint2f()
        val good1 = MatOfPoint2f()
        val back = MatOfPoint2f()
        val backStatus = MatOfByte()
        val backErrors = MatOfFloat()
        var affine: Mat? = null
        try {
            Imgproc.goodFeaturesToTrack(previous, corners, 400, 0.01, 12.0)
            if (corners.rows() < 12) return null
            p0.fromArray(*corners.toArray())
            Video.calcOpticalFlowPyrLK(previous, current, p0, p1, status, errors)
            Video.calcOpticalFlowPyrLK(current, previous, p1, back, backStatus, backErrors)
            val a = p0.toArray()
            val b = p1.toArray()
            val st = status.toArray()
            val err = errors.toArray()
            val reverse = back.toArray()
            val reverseStatus = backStatus.toArray()
            val indices =
                a.indices.filter {
                    st[it].toInt() != 0 &&
                        reverseStatus[it].toInt() != 0 &&
                        err[it] < 30f &&
                        hypot(a[it].x - reverse[it].x, a[it].y - reverse[it].y) < 1.5 &&
                        b[it].x >= 0 &&
                        b[it].x < current.cols() &&
                        b[it].y >= 0 &&
                        b[it].y < current.rows()
                }
            if (indices.size < 12) return null
            good0.fromArray(*indices.map { a[it] }.toTypedArray())
            good1.fromArray(*indices.map { b[it] }.toTypedArray())
            affine =
                Calib3d.estimateAffinePartial2D(
                    good0,
                    good1,
                    inliers,
                    Calib3d.RANSAC,
                    2.0,
                    2500,
                    0.995,
                    10
                )
            if (affine.empty() ||
                Core.countNonZero(inliers) < max(10, indices.size / 2)
            ) {
                return null
            }
            val coverage =
                indices.indices.filter { inliers.get(it, 0)[0] > 0 }.map { i ->
                    val point = a[indices[i]]
                    (if (point.x >= previous.cols() / 2.0) 1 else 0) +
                        (if (point.y >= previous.rows() / 2.0) 2 else 0)
                }.toSet()
            if (coverage.size < 3) return null // Avoid tracking only a small foreground subject.
            val aa = affine.get(0, 0)[0]
            val bb = affine.get(1, 0)[0]
            val angle = atan2(bb, aa)
            val scale = hypot(aa, bb)
            val cx = current.cols() / 2.0
            val cy = current.rows() / 2.0
            val x = affine.get(0, 2)[0] + (aa - 1) * cx - bb * cy
            val y = affine.get(1, 2)[0] + bb * cx + (aa - 1) * cy
            val plausible =
                x.isFinite() &&
                    y.isFinite() &&
                    scale in 0.95..1.05 &&
                    abs(angle) <= 0.15 &&
                    hypot(x, y) <= current.cols() * MAX_SHIFT
            return if (plausible) Pose(x, y, angle) else null
        } finally {
            listOf(corners, p0, p1, status, errors, inliers, good0, good1).forEach { it.release() }
            listOf(back, backStatus, backErrors).forEach { it.release() }
            affine?.release()
        }
    }
}
