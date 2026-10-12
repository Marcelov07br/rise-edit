package br.com.riseedit

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import br.com.riseedit.core.Adjustments

/** Approximate SDR display adjustments; faithful preview/export uses the Media3 effects. */
internal object FastPreviewStyle {
    fun filter(adjustments: Adjustments): ColorMatrixColorFilter {
        val contrast = 1 + adjustments.contrast
        val brightness = adjustments.brightness * 255 + (1 - contrast) * 127.5f
        val warmth = adjustments.warmth * 0.18f
        val matrix = ColorMatrix().apply { setSaturation(1 + adjustments.saturation) }
        matrix.postConcat(ColorMatrix(floatArrayOf(
            contrast*(1+warmth), 0f, 0f, 0f, brightness,
            0f, contrast, 0f, 0f, brightness,
            0f, 0f, contrast*(1-warmth), 0f, brightness,
            0f, 0f, 0f, 1f, 0f
        )))
        return ColorMatrixColorFilter(matrix)
    }
}
