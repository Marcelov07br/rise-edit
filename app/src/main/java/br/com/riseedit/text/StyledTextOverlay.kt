@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package br.com.riseedit.text

import android.graphics.Bitmap
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.Size
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.StaticOverlaySettings
import br.com.riseedit.core.TextLayer
import br.com.riseedit.core.TextMotion

/** Export/faithful-preview text: same bitmap as the editor, positioned by the shared model. */
class StyledTextOverlay(private val layer: TextLayer) : BitmapOverlay() {
    private var width = 1280
    private var height = 720

    override fun configure(videoSize: Size) {
        super.configure(videoSize)
        width = videoSize.width
        height = videoSize.height
    }

    private fun local(presentationTimeUs: Long) = presentationTimeUs / 1000 - layer.startMs

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val frame = TextMotion.at(
            layer.style,
            local(presentationTimeUs),
            layer.endMs - layer.startMs
        )
        val local = local(presentationTimeUs)
        return TextRenderer.render(
            layer,
            width,
            height,
            if (frame.alpha > 0) frame.reveal else 1f,
            br.com.riseedit.core.CaptionWords.activeRange(layer, local),
            local
        )
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        val local = local(presentationTimeUs)
        val frame = TextMotion.at(layer.style, local, layer.endMs - layer.startMs)
        val t = layer.transformAt(local.coerceAtLeast(0))
        val scale = t.scale * frame.scale
        return StaticOverlaySettings.Builder()
            .setAlphaScale(frame.alpha * layer.style.opacity)
            .setBackgroundFrameAnchor(t.x, t.y + frame.offsetY)
            .setScale(scale, scale)
            .setRotationDegrees(t.rotation)
            .build()
    }
}
