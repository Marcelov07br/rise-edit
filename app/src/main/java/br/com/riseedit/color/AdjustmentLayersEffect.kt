@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package br.com.riseedit.color

import android.content.Context
import android.opengl.GLES20
import android.util.Log
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import br.com.riseedit.core.AdjustmentLayer
import br.com.riseedit.core.AdjustmentPass
import br.com.riseedit.core.AdjustmentStack
import br.com.riseedit.core.ColorAdjust
import br.com.riseedit.core.color.ColorGrade

/**
 * Camadas de ajuste after composition. Export/Fiel: added to the Composition-level effects
 * (`Composition.Builder.setEffects`, Media3 1.9.0 — runs on the compositor output, i.e. main video
 * + overlays, before the text overlay), activated per frame by presentationTimeUs (composition
 * time) inside each layer's window. Prévia Rápida: [live] after the clip color effect, timed by
 * [ColorLive.projectMs] (ExoPlayer's presentation time carries renderer offsets).
 * Each active layer = one [ColorPass] draw (same shader as the take color), chained through two
 * scratch textures, the last one into Media3's output framebuffer; opacity = uAmount mix.
 */
internal class AdjustmentLayersEffect private constructor(
    private val source: (Long) -> List<AdjustmentPass>,
    private val static: Boolean,
    private val preview: Boolean,
    private val empty: Boolean
) : GlEffect {
    override fun isNoOp(inputWidth: Int, inputHeight: Int) = static && empty

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        Program(source, preview, useHdr)

    companion object {
        const val TAG = "RiseColor"

        fun forComposition(
            layers: List<AdjustmentLayer>,
            preview: Boolean
        ): AdjustmentLayersEffect {
            val snapshot = layers.toList()
            Log.i(TAG, "adjustment layers in composition: ${snapshot.size} preview=$preview")
            return AdjustmentLayersEffect({ us ->
                AdjustmentStack.at(snapshot, us / 1000)
            }, true, preview, snapshot.all { it.isNeutral })
        }

        val live = AdjustmentLayersEffect({ _ -> ColorLive.layerPasses() }, false, true, false)
    }

    private class Program(
        private val source: (Long) -> List<AdjustmentPass>,
        private val preview: Boolean,
        useHdr: Boolean
    ) : BaseGlShaderProgram(useHdr, 1) {
        private val pass = try {
            ColorPass()
        } catch (error: GlUtil.GlException) {
            throw VideoFrameProcessingException(error)
        }
        private var width = 0
        private var height = 0
        private val tex = IntArray(2)
        private val fbo = IntArray(2)
        private var lastCount = -1

        override fun configure(inputWidth: Int, inputHeight: Int): Size {
            if (inputWidth != width || inputHeight != height) {
                width = inputWidth
                height = inputHeight
                for (i in 0..1) {
                    if (tex[i] == 0) tex[i] = IntArray(1).also { GLES20.glGenTextures(1, it, 0) }[0]
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[i])
                    GLES20.glTexParameteri(
                        GLES20.GL_TEXTURE_2D,
                        GLES20.GL_TEXTURE_MIN_FILTER,
                        GLES20.GL_LINEAR
                    )
                    GLES20.glTexParameteri(
                        GLES20.GL_TEXTURE_2D,
                        GLES20.GL_TEXTURE_MAG_FILTER,
                        GLES20.GL_LINEAR
                    )
                    GLES20.glTexParameteri(
                        GLES20.GL_TEXTURE_2D,
                        GLES20.GL_TEXTURE_WRAP_S,
                        GLES20.GL_CLAMP_TO_EDGE
                    )
                    GLES20.glTexParameteri(
                        GLES20.GL_TEXTURE_2D,
                        GLES20.GL_TEXTURE_WRAP_T,
                        GLES20.GL_CLAMP_TO_EDGE
                    )
                    GLES20.glTexImage2D(
                        GLES20.GL_TEXTURE_2D,
                        0,
                        GLES20.GL_RGBA,
                        width,
                        height,
                        0,
                        GLES20.GL_RGBA,
                        GLES20.GL_UNSIGNED_BYTE,
                        null
                    )
                    if (fbo[i] ==
                        0
                    ) {
                        fbo[i] = IntArray(1).also { GLES20.glGenFramebuffers(1, it, 0) }[0]
                    }
                    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[i])
                    GLES20.glFramebufferTexture2D(
                        GLES20.GL_FRAMEBUFFER,
                        GLES20.GL_COLOR_ATTACHMENT0,
                        GLES20.GL_TEXTURE_2D,
                        tex[i],
                        0
                    )
                }
            }
            return Size(inputWidth, inputHeight)
        }

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            try {
                val passes = source(presentationTimeUs)
                val bypass = preview && ColorLive.holdOriginal
                val aspect = if (height > 0) width.toFloat() / height else 16f / 9f
                if (passes.size != lastCount) {
                    val ids = passes.map { it.layerId.take(6) }
                    Log.d(
                        TAG,
                        "adjustment layers active=${passes.size} at ${presentationTimeUs / 1000}ms $ids"
                    )
                    lastCount = passes.size
                }
                if (passes.isEmpty() || bypass) {
                    pass.draw(inputTexId, ColorAdjust.NEUTRAL, ColorGrade(), true, aspect)
                    return
                }
                val out = IntArray(1)
                GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, out, 0)
                val viewport = IntArray(4)
                GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, viewport, 0)
                var src = inputTexId
                passes.forEachIndexed { i, p ->
                    val last = i == passes.lastIndex
                    if (last) {
                        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, out[0])
                        GLES20.glViewport(viewport[0], viewport[1], viewport[2], viewport[3])
                    } else {
                        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[i % 2])
                        GLES20.glViewport(0, 0, width, height)
                    }
                    pass.draw(
                        src,
                        p.adjust,
                        p.grade,
                        false,
                        aspect,
                        amount = p.opacity,
                        maskView =
                        preview && ColorLive.maskViewLayer == p.layerId
                    )
                    if (!last) src = tex[i % 2]
                }
            } catch (error: GlUtil.GlException) {
                throw VideoFrameProcessingException(error, presentationTimeUs)
            }
        }

        override fun release() {
            super.release()
            try {
                pass.release()
                for (i in 0..1) {
                    if (tex[i] != 0) GlUtil.deleteTexture(tex[i])
                    if (fbo[i] != 0) GLES20.glDeleteFramebuffers(1, intArrayOf(fbo[i]), 0)
                }
            } catch (error: GlUtil.GlException) {
                throw VideoFrameProcessingException(error)
            }
        }
    }
}
