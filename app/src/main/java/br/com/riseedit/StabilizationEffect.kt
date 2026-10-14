@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package br.com.riseedit

import android.graphics.Matrix
import androidx.media3.common.util.Size
import androidx.media3.effect.MatrixTransformation
import br.com.riseedit.core.Clip
import br.com.riseedit.core.MotionAnalysis
import br.com.riseedit.core.MotionSmoother
import kotlin.math.cos
import kotlin.math.sin

class StabilizationEffect(
    private val clip: Clip,
    private val analysis: MotionAnalysis,
    private val timelineStartMs: Long
) : MatrixTransformation {
    private val corrections = MotionSmoother.corrections(
        analysis,
        clip.stabilization,
        clip.startMs,
        clip.endMs
    )
    private val matrix = Matrix()

    override fun configure(inputWidth: Int, inputHeight: Int) = Size(inputWidth, inputHeight)

    override fun getMatrix(presentationTimeUs: Long): Matrix {
        // Both CompositionPlayer and Transformer use the composition timescale.
        val sourceTime = clip.sourceAt(presentationTimeUs / 1000 - timelineStartMs)
        val correction = MotionSmoother.at(corrections, sourceTime)
        val pose = correction.pose
        val zoom = correction.zoom
        val cosine = cos(pose.angle)
        val sine = sin(pose.angle)
        val aspect = analysis.width.toDouble() / analysis.height
        // Convert image coordinates (Y down) to OpenGL NDC (Y up), including aspect ratio.
        matrix.setValues(
            floatArrayOf(
                (zoom * cosine).toFloat(),
                (zoom * sine / aspect).toFloat(),
                (2 * pose.x / analysis.width).toFloat(),
                (-zoom * sine * aspect).toFloat(),
                (zoom * cosine).toFloat(),
                (-2 * pose.y / analysis.height).toFloat(),
                0f,
                0f,
                1f
            )
        )
        return matrix
    }
}
