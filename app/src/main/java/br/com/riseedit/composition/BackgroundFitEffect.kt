@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package br.com.riseedit.composition

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import br.com.riseedit.core.BackgroundKind
import br.com.riseedit.core.FrameBackground
import br.com.riseedit.core.FrameFormat

/**
 * 0.24.0 Formato → Fundo: fits each frame into [ratio] (like Presentation LAYOUT_SCALE_TO_FIT)
 * and fills the bars with a solid color or a blurred, cover-scaled copy of the same frame.
 * One pass; the blur is a 2-ring Poisson-ish disc (24 taps) over the frame's own texture.
 */
internal class BackgroundFitEffect(
    private val ratio: Float,
    private val background: FrameBackground
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        Program(ratio, background, useHdr)

    private class Program(
        private val ratio: Float,
        private val bg: FrameBackground,
        useHdr: Boolean
    ) : BaseGlShaderProgram(useHdr, 1) {
        private val program = try {
            GlProgram(VERTEX, FRAGMENT).apply {
                setBufferAttribute(
                    "aFramePosition",
                    GlUtil.getNormalizedCoordinateBounds(),
                    GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
                )
            }
        } catch (error: GlUtil.GlException) {
            throw VideoFrameProcessingException(error)
        }
        private var rect = floatArrayOf(0f, 0f, 1f, 1f)

        override fun configure(inputWidth: Int, inputHeight: Int): Size {
            val (w, h) = FrameFormat.fitCanvas(inputWidth, inputHeight, ratio)
            rect = FrameFormat.contentRect(inputWidth, inputHeight, w, h)
            return Size(w, h)
        }

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            try {
                program.use()
                program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
                program.setFloatsUniform("uRect", rect)
                program.setFloatUniform("uMode", if (bg.kind == BackgroundKind.BLUR) 1f else 0f)
                val c = bg.color
                program.setFloatsUniform(
                    "uColor",
                    floatArrayOf(
                        (c shr 16 and 0xFF) / 255f,
                        (c shr 8 and 0xFF) / 255f,
                        (c and 0xFF) / 255f
                    )
                )
                program.setFloatUniform("uBlur", 0.01f + 0.07f * bg.blur.coerceIn(0f, 1f))
                program.setFloatUniform("uDim", bg.dim.coerceIn(0f, 1f))
                program.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            } catch (error: GlUtil.GlException) {
                throw VideoFrameProcessingException(error, presentationTimeUs)
            }
        }

        override fun release() {
            super.release()
            try {
                program.delete()
            } catch (error: GlUtil.GlException) {
                throw VideoFrameProcessingException(error)
            }
        }
    }

    private companion object {
        const val VERTEX = """
attribute vec4 aFramePosition;
varying vec2 vTex;
void main() {
  gl_Position = aFramePosition;
  vTex = vec2(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5);
}
"""
        const val FRAGMENT = """
precision highp float;
uniform sampler2D uTexSampler;
uniform vec4 uRect; // left, bottom, width, height of the content (0..1)
uniform float uMode; // 0 color, 1 blur
uniform vec3 uColor;
uniform float uBlur;
uniform float uDim;
varying vec2 vTex;
void main() {
  vec2 local = (vTex - uRect.xy) / uRect.zw;
  if (local.x >= 0.0 && local.x <= 1.0 && local.y >= 0.0 && local.y <= 1.0) {
    gl_FragColor = texture2D(uTexSampler, local);
    return;
  }
  if (uMode < 0.5) {
    gl_FragColor = vec4(uColor, 1.0);
    return;
  }
  // Cover-scale: the content fills the whole canvas (crop), then blur.
  vec2 uv = 0.5 + (vTex - 0.5) * min(uRect.z, uRect.w) / uRect.zw;
  vec3 sum = vec3(0.0);
  float total = 0.0;
  for (int ring = 1; ring <= 3; ring++) {
    float r = uBlur * float(ring) / 3.0;
    for (int i = 0; i < 8; i++) {
      float a = 6.2831853 * (float(i) + 0.5 * float(ring)) / 8.0;
      vec2 p = clamp(uv + r * vec2(cos(a), sin(a)), 0.0, 1.0);
      sum += texture2D(uTexSampler, p).rgb;
      total += 1.0;
    }
  }
  gl_FragColor = vec4(sum / total * (1.0 - uDim), 1.0);
}
"""
    }
}
