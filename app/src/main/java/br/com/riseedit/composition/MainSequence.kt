@file:androidx.annotation.OptIn(
    markerClass = [
        androidx.media3.common.util.UnstableApi::class,
        androidx.media3.common.util.ExperimentalApi::class
    ]
)

package br.com.riseedit.composition

import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import br.com.riseedit.AudioEnvelope
import br.com.riseedit.ClipColor
import br.com.riseedit.ClipSpeed
import br.com.riseedit.ClipTransform
import br.com.riseedit.StabilizationEffect
import br.com.riseedit.color.ColorAdjustEffect
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
        val effects = buildClipEffects(clip, analyses, offsetMs, preview)
        offsetMs += clip.durationMs
        val media = MediaItem.Builder()
            .setUri(clip.effectiveUri(preview && project.useProxies))
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
                listOfNotNull(
                    AudioEnvelope(
                        if (clip.muted) 0f else clip.volume,
                        clip.durationMs,
                        clip.fadeMs
                    ),
                    pitchShift(clip)
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
    offsetMs: Long,
    preview: Boolean
): List<Effect> = buildList {
    if (clip.stabilization.enabled) {
        val analysis = requireNotNull(analyses[clip.uri]) {
            "Analise o movimento de ${clip.name} antes de estabilizar."
        }
        add(StabilizationEffect(clip, analysis, offsetMs))
    }
    add(ClipTransform(clip, offsetMs))
    ColorAdjustEffect(clip, offsetMs, preview)
        .takeUnless { it.neutral && !preview }?.let(::add)
    add(ClipColor(clip, offsetMs))
}

/** 0.18: "manter tom" off → shift pitch by the (constant) speed, like tape. Speed keeps pitch. */
private fun pitchShift(clip: Clip): androidx.media3.common.audio.AudioProcessor? {
    val speed = clip.speedAt(clip.startMs).toFloat()
    if (clip.keepPitch || speed == 1f) return null
    return androidx.media3.common.audio.SonicAudioProcessor().apply { setPitch(speed) }
}
