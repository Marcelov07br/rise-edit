@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package br.com.riseedit.composition

import androidx.media3.common.Effect
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import br.com.riseedit.core.Project
import br.com.riseedit.text.StyledTextOverlay

internal fun buildOutputEffects(project: Project, preview: Boolean): List<Effect> = buildList {
    if (project.aspectRatio > 0) {
        add(
            Presentation.createForAspectRatio(
                project.aspectRatio,
                Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
            )
        )
    }
    add(Presentation.createForHeight(if (preview) 480 else project.export.height))
    if (project.texts.isNotEmpty()) add(OverlayEffect(project.texts.map(::StyledTextOverlay)))
    if (!preview) add(FrameDropEffect.createDefaultFrameDropEffect(project.export.fps.toFloat()))
}
