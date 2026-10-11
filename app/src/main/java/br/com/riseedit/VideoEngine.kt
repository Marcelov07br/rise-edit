@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class, androidx.media3.common.util.ExperimentalApi::class])
package br.com.riseedit

import android.graphics.Matrix
import androidx.media3.common.MediaItem
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.Presentation
import androidx.media3.common.util.Size
import androidx.media3.transformer.*
import br.com.riseedit.core.*
import kotlin.math.*

class StabilizationEffect(
    private val clip: Clip,
    private val analysis: MotionAnalysis,
    private val timelineStartMs: Long
) : MatrixTransformation {
    private val corrections = MotionSmoother.corrections(analysis, clip.stabilization, clip.startMs, clip.endMs)
    private val zoom = corrections.maxOf { MotionSmoother.requiredZoom(it.pose, analysis.width, analysis.height) }.coerceIn(1.0, clip.stabilization.maxZoom)
    private val matrix = Matrix()
    override fun configure(inputWidth: Int, inputHeight: Int) = Size(inputWidth, inputHeight)
    override fun getMatrix(presentationTimeUs: Long): Matrix {
        // Both CompositionPlayer and Transformer pass timestamps in the composition timescale.
        val sourceTime = presentationTimeUs / 1000 - timelineStartMs + clip.startMs
        val p = MotionSmoother.interpolate(corrections, sourceTime)
        val z = zoom
        val c = cos(p.angle); val s = sin(p.angle)
        val aspect = analysis.width.toDouble() / analysis.height
        // Convert image coordinates (Y down) to OpenGL NDC (Y up), including aspect ratio.
        matrix.setValues(floatArrayOf(
            (z*c).toFloat(), (z*s/aspect).toFloat(), (2*p.x/analysis.width).toFloat(),
            (-z*s*aspect).toFloat(), (z*c).toFloat(), (-2*p.y/analysis.height).toFloat(),
            0f, 0f, 1f
        ))
        return matrix
    }
}

object VideoEngine {
    fun composition(project: Project, analyses: Map<String, MotionAnalysis>): Composition {
        require(project.clips.isNotEmpty())
        var offset = 0L
        val items = project.clips.map { clip ->
            val media = MediaItem.Builder().setUri(clip.uri).setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder().setStartPositionMs(clip.startMs).setEndPositionMs(clip.endMs).build()
            ).build()
            val effects = if (clip.stabilization.enabled) {
                val analysis = requireNotNull(analyses[clip.uri]) { "Analise o movimento de ${clip.name} antes de estabilizar." }
                listOf(StabilizationEffect(clip, analysis, offset))
            } else emptyList()
            offset += clip.durationMs
            EditedMediaItem.Builder(media).setDurationUs(clip.sourceDurationMs * 1000).setEffects(Effects(emptyList(), effects)).build()
        }
        return Composition.Builder(EditedMediaItemSequence.withAudioAndVideoFrom(items))
            .setEffects(Effects(emptyList(), listOf(Presentation.createForHeight(720))))
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL).build()
    }
}
