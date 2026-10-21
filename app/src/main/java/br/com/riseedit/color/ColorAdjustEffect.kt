@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package br.com.riseedit.color

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import br.com.riseedit.core.ColorAdjust
import br.com.riseedit.core.ColorAdjustMath
import br.com.riseedit.core.color.ColorGrade
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Live state for the fast preview player and the hold-to-compare button. */
internal object ColorLive {
    @Volatile var adjust: ColorAdjust = ColorAdjust.NEUTRAL
    @Volatile var grade: ColorGrade = ColorGrade()
    /** True while the user holds "Comparar": every ColorAdjustEffect outputs the original. */
    @Volatile var holdOriginal = false
}

/**
 * One pass, fixed order: [grade LUT if lutBeforeAdjust] → exposure → temperature/tint →
 * contrast(pivot) → shadows/highlights → saturation/vibrance → HSL → lift/gamma/gain → curves →
 * [grade LUT]. Formulas come from [ColorAdjustMath.GLSL] (shared source).
 * Static instances (export/faithful preview) are no-ops when neutral; the live instance reads
 * [ColorLive] every frame so the fast preview needs no pipeline rebuild on slider changes.
 */
internal class ColorAdjustEffect private constructor(
    private val source: () -> Pair<ColorAdjust, ColorGrade>,
    private val static: Boolean,
    private val preview: Boolean
) : GlEffect {
    constructor(adjust: ColorAdjust, grade: ColorGrade, preview: Boolean) :
        this({ adjust to grade }, true, preview)

    val neutral get() = source().let { it.first.isNeutral && it.second.isIdentity }

    override fun isNoOp(inputWidth: Int, inputHeight: Int) = static && neutral

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        Program(source, preview, useHdr)

    companion object {
        val live = ColorAdjustEffect({ ColorLive.adjust to ColorLive.grade }, false, true)
        private const val SIZE = 33
    }

    private class Program(
        private val source: () -> Pair<ColorAdjust, ColorGrade>,
        private val preview: Boolean,
        useHdr: Boolean
    ) :
        BaseGlShaderProgram(useHdr, 1) {
        private val program = try {
            GlProgram(Shaders.VERTEX, Shaders.FRAGMENT).apply {
                setBufferAttribute(
                    "aFramePosition",
                    GlUtil.getNormalizedCoordinateBounds(),
                    GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
                )
            }
        } catch (error: GlUtil.GlException) {
            throw VideoFrameProcessingException(error)
        }
        private var lutTex = 0
        private var lutGrade: ColorGrade? = null
        private var curveTex = 0
        private var curveData: br.com.riseedit.core.ColorCurves? = null

        override fun configure(inputWidth: Int, inputHeight: Int) = Size(inputWidth, inputHeight)

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            try {
                val (adjust, grade) = source()
                val bypass = preview && ColorLive.holdOriginal
                val useLut = !bypass && !grade.isIdentity
                if (useLut && grade != lutGrade) uploadLut(grade)
                program.use()
                program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
                program.setSamplerTexIdUniform("uLut", if (useLut) lutTex else inputTexId, 1)
                program.setFloatUniform("uUseLut", if (useLut) 1f else 0f)
                program.setFloatUniform("uLutSize", SIZE.toFloat())
                program.setFloatUniform("uLutFirst", if (grade.lutBeforeAdjust) 1f else 0f)
                val a = if (bypass) ColorAdjust.NEUTRAL else adjust
                a.uniforms().forEach { (name, v) -> program.setFloatUniform(name, v) }
                a.vectorUniforms().forEach { (name, v) -> program.setFloatsUniform(name, v) }
                program.setFloatUniform("uUseHsl", if (a.hsl.isNeutral) 0f else 1f)
                program.setFloatUniform("uUseWheels", if (a.wheels.isNeutral) 0f else 1f)
                val useCurves = !a.curves.isIdentity
                if (useCurves && a.curves != curveData) uploadCurves(a.curves)
                program.setSamplerTexIdUniform("uCurves", if (useCurves) curveTex else inputTexId, 2)
                program.setFloatUniform("uUseCurves", if (useCurves) 1f else 0f)
                program.setFloatUniform("uNeutral", if (bypass || adjust.isNeutral) 1f else 0f)
                program.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            } catch (error: GlUtil.GlException) {
                throw VideoFrameProcessingException(error, presentationTimeUs)
            }
        }

        /** 33³ cube as a 2D atlas: x = b * 33 + r, y = g (GLES2 has no 3D textures). */
        private fun uploadLut(grade: ColorGrade) {
            val data = GradeLuts.cube(grade)
            val buf = ByteBuffer.allocateDirect(SIZE * SIZE * SIZE * 4).order(ByteOrder.nativeOrder())
            for (g in 0 until SIZE) for (b in 0 until SIZE) for (r in 0 until SIZE) {
                val i = ((b * SIZE + g) * SIZE + r) * 3
                buf.put(byte(data[i])).put(byte(data[i + 1])).put(byte(data[i + 2])).put(-1)
            }
            buf.flip()
            if (lutTex == 0) {
                val ids = IntArray(1)
                GLES20.glGenTextures(1, ids, 0)
                lutTex = ids[0]
            }
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lutTex)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, SIZE * SIZE, SIZE, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf
            )
            GlUtil.checkGlError()
            lutGrade = grade
        }

        /** Curves (master∘channel) baked into a 256×1 RGBA texture; re-uploaded only on change. */
        private fun uploadCurves(curves: br.com.riseedit.core.ColorCurves) {
            val t = curves.table(256)
            val buf = ByteBuffer.allocateDirect(256 * 4).order(ByteOrder.nativeOrder())
            for (i in 0 until 256) buf.put(byte(t[i * 3])).put(byte(t[i * 3 + 1])).put(byte(t[i * 3 + 2])).put(-1)
            buf.flip()
            if (curveTex == 0) {
                val ids = IntArray(1)
                GLES20.glGenTextures(1, ids, 0)
                curveTex = ids[0]
            }
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, curveTex)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 256, 1, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf
            )
            GlUtil.checkGlError()
            curveData = curves
        }

        private fun byte(v: Float) = (v * 255f + 0.5f).toInt().coerceIn(0, 255).toByte()

        override fun release() {
            super.release()
            try {
                if (lutTex != 0) GlUtil.deleteTexture(lutTex)
                if (curveTex != 0) GlUtil.deleteTexture(curveTex)
                program.delete()
            } catch (error: GlUtil.GlException) {
                throw VideoFrameProcessingException(error)
            }
        }
    }

}

private object Shaders {
        const val VERTEX = """
attribute vec4 aFramePosition;
varying vec2 vTex;
void main() {
  gl_Position = aFramePosition;
  vTex = vec2(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5);
}
"""
        const val FRAGMENT = "precision highp float;\n" + ColorAdjustMath.GLSL + """
uniform sampler2D uTexSampler;
uniform sampler2D uLut;
uniform float uUseLut;
uniform float uLutSize;
uniform float uNeutral;
uniform float uLutFirst;
uniform sampler2D uCurves;
uniform float uUseCurves;
varying vec2 vTex;
vec3 lut(vec3 c) {
  float n = uLutSize;
  float bz = c.b * (n - 1.0);
  float b0 = floor(bz);
  float b1 = min(b0 + 1.0, n - 1.0);
  float x = c.r * (n - 1.0) + 0.5;
  float y = (c.g * (n - 1.0) + 0.5) / n;
  vec3 s0 = texture2D(uLut, vec2((b0 * n + x) / (n * n), y)).rgb;
  vec3 s1 = texture2D(uLut, vec2((b1 * n + x) / (n * n), y)).rgb;
  return mix(s0, s1, bz - b0);
}
void main() {
  vec4 color = texture2D(uTexSampler, vTex);
  vec3 c = color.rgb;
  if (uUseLut > 0.5 && uLutFirst > 0.5) c = lut(clamp(c, 0.0, 1.0));
  if (uNeutral < 0.5) c = colorAdjust(c);
  if (uUseCurves > 0.5) {
    float o = 0.5 / 256.0; float k = 255.0 / 256.0;
    c = vec3(texture2D(uCurves, vec2(c.r * k + o, 0.5)).r,
             texture2D(uCurves, vec2(c.g * k + o, 0.5)).g,
             texture2D(uCurves, vec2(c.b * k + o, 0.5)).b);
  }
  if (uUseLut > 0.5 && uLutFirst < 0.5) c = lut(clamp(c, 0.0, 1.0));
  gl_FragColor = vec4(c, color.a);
}
"""
}
