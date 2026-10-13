@file:androidx.annotation.OptIn(
    markerClass = [
        androidx.media3.common.util.UnstableApi::class,
        androidx.media3.common.util.ExperimentalApi::class
    ]
)

package br.com.riseedit

import androidx.media3.transformer.Composition
import androidx.media3.transformer.Effects
import br.com.riseedit.composition.ProjectVideoCompositor
import br.com.riseedit.composition.buildAudioSequences
import br.com.riseedit.composition.buildMainSequence
import br.com.riseedit.composition.buildOutputEffects
import br.com.riseedit.composition.buildOverlaySequences
import br.com.riseedit.core.MotionAnalysis
import br.com.riseedit.core.Project

object VideoEngine {
    fun composition(
        project: Project,
        analyses: Map<String, MotionAnalysis>,
        preview: Boolean = false
    ): Composition {
        require(project.clips.isNotEmpty())
        val overlays = project.overlays.filter { it.offsetMs < project.durationMs }
        val sequences = listOf(buildMainSequence(project, analyses, preview)) +
            buildOverlaySequences(project, overlays, preview) +
            buildAudioSequences(project)
        val composition = Composition.Builder(sequences)
        // Media3 requires its default compositor for a single video input.
        if (overlays.isNotEmpty()) {
            composition.setVideoCompositorSettings(
                ProjectVideoCompositor(overlays, project.durationMs)
            )
        }
        return composition
            .setEffects(Effects(emptyList(), buildOutputEffects(project, preview)))
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
            .build()
    }
}
