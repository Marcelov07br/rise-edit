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
import br.com.riseedit.core.OverlayBlend
import br.com.riseedit.core.OverlayMask
import br.com.riseedit.core.VideoOverlay

/** Mask and blend for one overlay; formulas mirror [br.com.riseedit.core.OverlayLook]. */
internal class OverlayLookEffect(private val overlay: VideoOverlay) : GlEffect {
    val needed get() = overlay.blend != OverlayBlend.NORMAL ||
        overlay.mask != OverlayMask.NONE ||
        !overlay.clip.masks.isEmpty

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        Program(overlay, useHdr)

    private class Program(private val overlay: VideoOverlay, useHdr: Boolean) :
        BaseGlShaderProgram(useHdr, 1) {
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
        private var aspect = 1f

        override fun configure(inputWidth: Int, inputHeight: Int): Size {
            aspect = inputWidth.toFloat() / inputHeight.coerceAtLeast(1)
            return Size(inputWidth, inputHeight)
        }

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            try {
                program.use()
                program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
                program.setIntUniform("uMask", overlay.mask.ordinal)
                program.setIntUniform("uBlend", overlay.blend.ordinal)
                program.setFloatUniform("uFeather", overlay.maskFeather)
                program.setFloatUniform("uAspect", aspect)
                program.setFloatUniform("uInvert", if (overlay.maskInvert) 1f else 0f)
                program.setFloatUniform("uFlipY", 0f)
                // Ferramenta Máscara (0.17): alpha × coverage. Same mapping as the take color
                // (sequence time − offset → overlay clip source time); keys in source time.
                val masks = if (overlay.clip.masks.isEmpty) {
                    emptyList()
                } else {
                    val local = (presentationTimeUs / 1000 - overlay.offsetMs).coerceAtLeast(0)
                    overlay.clip.masks.at(overlay.clip.startMs + local)
                }
                br.com.riseedit.core.mask.MaskMath.uniforms(masks).forEach { (name, v) ->
                    if (v.size ==
                        1
                    ) {
                        program.setFloatUniform(name, v[0])
                    } else {
                        program.setFloatsUniform(name, v)
                    }
                }
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
        // uAspect / uFlipY / sstep come from LocalColorMath.GLSL.
        const val VERTEX = """
attribute vec4 aFramePosition;
varying vec2 vTex;
void main() {
  gl_Position = aFramePosition;
  vTex = vec2(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5);
}
"""

        // v grows downwards in OverlayLook, so the mask uses 1 - vTex.y.
        val FRAGMENT = "precision highp float;\n" + br.com.riseedit.core.ColorAdjustMath.GLSL +
            br.com.riseedit.core.color.LocalColorMath.GLSL +
            br.com.riseedit.core.mask.MaskMath.GLSL +
            """
uniform sampler2D uTexSampler;
uniform int uMask;
uniform int uBlend;
uniform float uFeather;
uniform float uInvert;
varying vec2 vTex;
float maskAt(vec2 p) {
  float f = max(0.001, uFeather * 0.5);
  if (uMask == 1) {
    vec2 d = (p - 0.5) * vec2(max(1.0, uAspect), max(1.0, 1.0 / uAspect));
    float r = 0.5 * min(max(1.0, uAspect), max(1.0, 1.0 / uAspect));
    return smoothstep(r, r - f, length(d));
  }
  if (uMask == 2) {
    float radius = 0.15;
    vec2 q = max(abs(p - 0.5) - (0.5 - radius), 0.0);
    return smoothstep(radius, radius - f, length(q));
  }
  if (uMask == 3) return smoothstep(0.5 + f, 0.5 - f, 1.0 - p.y);
  return 1.0;
}
float blendAt(vec3 c) {
  float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));
  if (uBlend == 1) return smoothstep(0.05, 0.6, luma);
  if (uBlend == 2) return 1.0 - smoothstep(0.4, 0.95, luma);
  if (uBlend == 3) return 1.0 - smoothstep(0.08, 0.25, c.g - max(c.r, c.b));
  return 1.0;
}
void main() {
  vec4 color = texture2D(uTexSampler, vTex);
  float m = maskAt(vec2(vTex.x, 1.0 - vTex.y));
  if (uMask != 0 && uInvert > 0.5) m = 1.0 - m;
  float cov = maskCoverage(vec2(vTex.x, 1.0 - vTex.y), uAspect, clamp(color.rgb, 0.0, 1.0));
  gl_FragColor = vec4(color.rgb, color.a * m * blendAt(color.rgb) * cov);
}
"""
    }
}
