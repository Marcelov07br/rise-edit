@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class, androidx.media3.common.util.ExperimentalApi::class])
package br.com.riseedit

import android.graphics.Matrix
import androidx.media3.common.MediaItem
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.Effect
import androidx.media3.common.OverlaySettings
import androidx.media3.common.VideoCompositorSettings
import androidx.media3.effect.*
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
        val sourceTime = clip.sourceAt(presentationTimeUs / 1000 - timelineStartMs)
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
    private fun videoGap(durationMs: Long, fps: Int) = EditedMediaItem.Builder(MediaItem.Builder()
        .setUri("android.resource://br.com.riseedit/${R.raw.transparent}").setMimeType(MimeTypes.IMAGE_PNG).setImageDurationMs(durationMs).build())
        .setDurationUs(durationMs*1000).setFrameRate(fps).setRemoveAudio(true).build()
    fun composition(project: Project, analyses: Map<String, MotionAnalysis>, preview: Boolean = false): Composition {
        require(project.clips.isNotEmpty())
        var offset = 0L
        val items = project.clips.map { clip ->
            val media = MediaItem.Builder().setUri(clip.uri).setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder().setStartPositionMs(clip.startMs).setEndPositionMs(clip.endMs).build()
            ).build()
            val effects = mutableListOf<Effect>()
            if (clip.stabilization.enabled) {
                val analysis = requireNotNull(analyses[clip.uri]) { "Analise o movimento de ${clip.name} antes de estabilizar." }
                effects.add(StabilizationEffect(clip, analysis, offset))
            }
            effects.addAll(listOf(ClipTransform(clip,offset), Brightness(clip.adjustments.brightness), Contrast(clip.adjustments.contrast),
                HslAdjustment.Builder().adjustSaturation(clip.adjustments.saturation * 100).build(), ClipColor(clip,offset)))
            offset += clip.durationMs
            val builder = EditedMediaItem.Builder(media).setDurationUs(clip.sourceDurationMs * 1000)
            if (clip.speedPoints.isNotEmpty()) builder.setSpeed(ClipSpeed(clip))
            builder.setEffects(Effects(listOf(AudioEnvelope(clip.volume,clip.durationMs,clip.fadeMs)), effects)).build()
        }
        val sequences = mutableListOf(EditedMediaItemSequence.withAudioAndVideoFrom(items))
        val overlays = project.overlays.filter { it.offsetMs < project.durationMs }
        overlays.forEach { o ->
            val c = o.clip
            val sequence = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_VIDEO))
            if (o.offsetMs > 0) sequence.addItem(videoGap(o.offsetMs,project.export.fps))
            val available = project.durationMs - o.offsetMs
            val end = c.sourceAt(min(c.durationMs,available))
            val media = MediaItem.Builder().setUri(c.uri).setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(c.startMs).setEndPositionMs(end).build()).build()
            // Media3 1.9 setSpeed waits for an audio processor even when audio is removed (#3006).
            // Overlay speed is not exposed in this release; leave its native timestamps intact.
            sequence.addItem(EditedMediaItem.Builder(media).setDurationUs(c.sourceDurationMs*1000).setRemoveAudio(true).build())
            val tail = project.durationMs - o.offsetMs - min(c.durationMs,available)
            if (tail > 0) sequence.addItem(videoGap(tail,project.export.fps))
            sequences.add(sequence.build())
        }
        project.audio.filter { it.offsetMs < project.durationMs }.forEach { a ->
            val duration = min(a.durationMs,project.durationMs-a.offsetMs)
            val media = MediaItem.Builder().setUri(a.uri).setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(a.startMs).setEndPositionMs(a.startMs+duration).build()).build()
            val sequence = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))
            if (a.offsetMs > 0) sequence.addGap(a.offsetMs*1000)
            sequence.addItem(EditedMediaItem.Builder(media).setDurationUs(a.sourceDurationMs*1000).setRemoveVideo(true)
                .setEffects(Effects(listOf(AudioEnvelope(a.volume,duration,a.fadeMs)),emptyList())).build())
            val tail = project.durationMs-a.offsetMs-duration
            if (tail > 0) sequence.addGap(tail*1000)
            sequences.add(sequence.build())
        }
        val output = mutableListOf<Effect>()
        if (project.aspectRatio > 0) output.add(Presentation.createForAspectRatio(project.aspectRatio,Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP))
        output.add(Presentation.createForHeight(if (preview) 480 else project.export.height))
        if (project.texts.isNotEmpty()) output.add(OverlayEffect(project.texts.map(::TimedText)))
        if (!preview) output.add(FrameDropEffect.createDefaultFrameDropEffect(project.export.fps.toFloat()))
        val composition = Composition.Builder(sequences)
        if (overlays.isNotEmpty()) composition.setVideoCompositorSettings(object : VideoCompositorSettings {
                override fun getOutputSize(sizes: List<Size>) = sizes.first()
                override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): OverlaySettings {
                    if (inputId == 0) return StaticOverlaySettings.Builder().build()
                    val o = overlays.getOrNull(inputId-1) ?: return StaticOverlaySettings.Builder().build()
                    val time = presentationTimeUs/1000
                    val alpha = if (time in o.offsetMs until min(project.durationMs,o.offsetMs+o.clip.durationMs)) o.opacity else 0f
                    return StaticOverlaySettings.Builder().setAlphaScale(alpha).setScale(o.scale,o.scale).setBackgroundFrameAnchor(o.x,o.y).build()
                }
            })
        return composition.setEffects(Effects(emptyList(), output))
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL).build()
    }
}
