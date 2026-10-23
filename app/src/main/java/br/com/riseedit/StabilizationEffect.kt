@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package br.com.riseedit

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import br.com.riseedit.core.Clip
import br.com.riseedit.core.Correction
import br.com.riseedit.core.MotionAnalysis
import br.com.riseedit.core.MotionSmoother
import br.com.riseedit.core.Pose
import br.com.riseedit.core.Stabilization

/**
 * Live state for the fast preview player, following the ColorLive pattern: PreviewPanel writes
 * the current clip's corrections/analysis and the playhead source time every frame, so slider
 * changes never rebuild the player — the GL pass just reads new uniforms.
 */
internal object StabilizationLive {
    /** Corrections of the clip under the playhead (empty = pass-through). */
    @Volatile var corrections: List<Correction> = emptyList()

    /** Analysis frame of that clip (aspect + pixel→UV conversion); null = pass-through. */
    @Volatile var analysis: MotionAnalysis? = null

    @Volatile var settings: Stabilization = Stabilization()

    /** True while the user holds "Antes / depois": the pass draws the original. */
    @Volatile var comparing = false

    /** Source position of the playing clip (set by PreviewPanel each frame). */
    @Volatile var sourceMs: Long = 0

    /** Bumped on every live change so Compose state that depends on it recomposes. */
    val tick = androidx.compose.runtime.mutableIntStateOf(0)
}

/**
 * Rise stabilization as one GL pass: the rigid correction (translate/rotate/zoom — same matrix
 * the former MatrixTransformation produced) plus, when [Stabilization.method] is WARP, a
 * per-line rolling-shutter straightening driven by the measured frame-to-frame velocity.
 * Static instances (export/faithful preview) resolve corrections from the clip, computed once;
 * the live instance reads [StabilizationLive] every frame so the fast preview needs no rebuild.
 */
internal class StabilizationEffect private constructor(
    private val resolve: (Long) -> Frame?
) : GlEffect {
    /** One clip in the export/faithful composition: corrections keyed by composition time. */
    constructor(
        clip: Clip,
        analysis: MotionAnalysis,
        timelineStartMs: Long
    ) : this(staticResolver(clip, analysis, timelineStartMs))

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        Program(resolve, useHdr)

    /** What the pass draws for one frame; null = identity (pass-through copy). */
    internal data class Frame(
        val correction: Correction,
        val analysis: MotionAnalysis,
        val warpAmount: Double
    )

    companion object {
        val live = StabilizationEffect { _ ->
            val analysis = StabilizationLive.analysis
            val corrections = StabilizationLive.corrections
            if (analysis == null || corrections.isEmpty()) {
                null
            } else {
                Frame(
                    MotionSmoother.at(corrections, StabilizationLive.sourceMs),
                    analysis,
                    StabilizationLive.settings.warpAmount
                )
            }
        }
    }

    private class Program(
        private val resolve: (Long) -> Frame?,
        useHdr: Boolean
    ) : BaseGlShaderProgram(useHdr, 1) {
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

        override fun configure(inputWidth: Int, inputHeight: Int) = Size(inputWidth, inputHeight)

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            try {
                program.use()
                program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
                val frame = if (StabilizationLive.comparing) null else resolve(presentationTimeUs)
                if (frame == null) {
                    program.setFloatUniform("uZoom", 1f)
                    program.setFloatUniform("uAngle", 0f)
                    program.setFloatsUniform("uShift", IDENTITY_SHIFT)
                    program.setFloatsUniform("uWarp", IDENTITY_WARP)
                    program.setFloatUniform("uWarpOn", 0f)
                    program.setFloatUniform("uAspect", 1f)
                } else {
                    val (correction, analysis, warpAmount) = frame
                    val pose = correction.pose
                    program.setFloatUniform("uZoom", correction.zoom.toFloat())
                    program.setFloatUniform("uAngle", pose.angle.toFloat())
                    // NDC shift, same convention as the former MatrixTransformation.
                    program.setFloatsUniform(
                        "uShift",
                        floatArrayOf(
                            (2 * pose.x / analysis.width).toFloat(),
                            (-2 * pose.y / analysis.height).toFloat()
                        )
                    )
                    program.setFloatsUniform(
                        "uWarp",
                        warpUniforms(correction.velocity, analysis, warpAmount)
                    )
                    program.setFloatUniform("uWarpOn", if (warpAmount > 0.0) 1f else 0f)
                    program.setFloatUniform(
                        "uAspect",
                        (analysis.width.toDouble() / analysis.height).toFloat()
                    )
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
}

/** Corrections are immutable per clip: compute once, interpolate per frame afterwards. */
private fun staticResolver(
    clip: Clip,
    analysis: MotionAnalysis,
    timelineStartMs: Long
): (Long) -> StabilizationEffect.Frame {
    val corrections =
        MotionSmoother.corrections(analysis, clip.stabilization, clip.startMs, clip.endMs)
    val warpAmount = clip.stabilization.warpAmount
    return { us ->
        // Both CompositionPlayer and Transformer use the composition timescale.
        val sourceTime = clip.sourceAt(us / 1000 - timelineStartMs)
        StabilizationEffect.Frame(MotionSmoother.at(corrections, sourceTime), analysis, warpAmount)
    }
}

/**
 * Velocity (analysis px/sample) → per-line shear uniforms in UV units:
 * x = horizontal shear, y = vertical shear, z = angular skew (small-angle rotation ≈ shear),
 * all already scaled by the rolling-shutter amount. Top edge (captured first) shifts opposite
 * to the bottom edge, straightening the jello/wobble a rigid transform cannot.
 */
internal fun warpUniforms(
    velocity: Pose,
    analysis: MotionAnalysis,
    amount: Double
): FloatArray = floatArrayOf(
    (velocity.x * amount / analysis.width).toFloat(),
    (velocity.y * amount / analysis.height).toFloat(),
    (velocity.angle * amount * analysis.height / (2 * analysis.width)).toFloat()
)

private val IDENTITY_SHIFT = floatArrayOf(0f, 0f)
private val IDENTITY_WARP = floatArrayOf(0f, 0f, 0f)

private object Shaders {
    const val VERTEX = """
attribute vec4 aFramePosition;
varying vec2 vTex;
void main() {
  gl_Position = aFramePosition;
  vTex = vec2(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5);
}
"""
    val FRAGMENT = """
precision highp float;
uniform sampler2D uTexSampler;
uniform float uZoom;
uniform float uAngle;
uniform vec2 uShift;
uniform vec3 uWarp;
uniform float uWarpOn;
uniform float uAspect;
varying vec2 vTex;
void main() {
  vec2 uv = vec2(vTex.x, 1.0 - vTex.y);              // y-down image coordinates
  vec2 n = vec2(uv.x * 2.0 - 1.0, 1.0 - 2.0 * uv.y); // NDC, y-up (former matrix space)
  float c = cos(uAngle);
  float s = sin(uAngle);
  vec2 r = uZoom * vec2(
      c * n.x + s * n.y / uAspect,
      -s * n.x * uAspect + c * n.y
  ) + uShift;
  vec2 outUv = vec2(r.x * 0.5 + 0.5, 0.5 - r.y * 0.5);
  if (uWarpOn > 0.5) {
    float dt = 0.5 - outUv.y; // top line captured first: negative above center
    outUv.x += (uWarp.x + uWarp.z) * dt;
    outUv.y += uWarp.y * dt;
  }
  gl_FragColor = texture2D(uTexSampler, outUv);
}
"""
}
