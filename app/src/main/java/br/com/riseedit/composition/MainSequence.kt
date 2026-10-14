@file:androidx.annotation.OptIn(
    markerClass = [
        androidx.media3.common.util.UnstableApi::class,
        androidx.media3.common.util.ExperimentalApi::class
    ]
)

package br.com.riseedit.composition

import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import br.com.riseedit.AudioEnvelope
import br.com.riseedit.ClipColor
import br.com.riseedit.ClipSpeed
import br.com.riseedit.ClipTransform
import br.com.riseedit.StabilizationEffect
import br.com.riseedit.core.Clip
import br.com.riseedit.core.MotionAnalysis
import br.com.riseedit.core.Project

internal fun buildMainSequence(
    project: Project,
    analyses: Map<String, MotionAnalysis>,
    preview: Boolean
): EditedMediaItemSequence {
    var offsetMs = 0L
    val items = project.clips.map { clip ->
        val effects = buildClipEffects(clip, analyses, offsetMs)
        offsetMs += clip.durationMs
        val media = MediaItem.Builder()
            .setUri(clip.effectiveUri(preview))
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(clip.startMs)
                    .setEndPositionMs(clip.endMs)
                    .build()
            )
            .build()
        val builder = EditedMediaItem.Builder(media)
            .setDurationUs(clip.sourceDurationMs * 1000)
        if (clip.speedPoints.isNotEmpty()) builder.setSpeed(ClipSpeed(clip))
        builder.setEffects(
            Effects(
                listOf(
                    AudioEnvelope(if (clip.muted) 0f else clip.volume, clip.durationMs, clip.fadeMs)
                ),
                effects
            )
        ).build()
    }
    return EditedMediaItemSequence.withAudioAndVideoFrom(items)
}

private fun buildClipEffects(
    clip: Clip,
    analyses: Map<String, MotionAnalysis>,
    offsetMs: Long
): List<Effect> = buildList {
    if (clip.stabilization.enabled) {
        val analysis = requireNotNull(analyses[clip.uri]) {
            "Analise o movimento de ${clip.name} antes de estabilizar."
        }
        add(StabilizationEffect(clip, analysis, offsetMs))
    }
    add(ClipTransform(clip, offsetMs))
    add(Brightness(clip.adjustments.brightness))
    add(Contrast(clip.adjustments.contrast))
    add(HslAdjustment.Builder().adjustSaturation(clip.adjustments.saturation * 100).build())
    add(ClipColor(clip, offsetMs))
}
