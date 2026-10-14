@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package br.com.riseedit.composition

import androidx.media3.common.OverlaySettings
import androidx.media3.common.VideoCompositorSettings
import androidx.media3.common.util.Size
import androidx.media3.effect.StaticOverlaySettings
import br.com.riseedit.core.VideoOverlay
import kotlin.math.min

internal class ProjectVideoCompositor(
    private val overlays: List<VideoOverlay>,
    private val durationMs: Long
) : VideoCompositorSettings {
    override fun getOutputSize(sizes: List<Size>) = sizes.first()

    override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): OverlaySettings {
        if (inputId == 0) return StaticOverlaySettings.Builder().build()
        val overlay = overlays.getOrNull(inputId - 1)
            ?: return StaticOverlaySettings.Builder().build()
        val timeMs = presentationTimeUs / 1000
        val endMs = min(durationMs, overlay.offsetMs + overlay.clip.durationMs)
        val t = overlay.transformAt((timeMs - overlay.offsetMs).coerceAtLeast(0))
        val alpha = if (timeMs in overlay.offsetMs until endMs) t.opacity else 0f
        return StaticOverlaySettings.Builder()
            .setAlphaScale(alpha)
            .setScale(t.scale, t.scale)
            .setRotationDegrees(t.rotation)
            .setBackgroundFrameAnchor(t.x, t.y)
            .build()
    }
}
