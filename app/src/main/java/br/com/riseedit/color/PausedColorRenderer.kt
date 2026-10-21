@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package br.com.riseedit.color

import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLUtils
import android.util.Log
import br.com.riseedit.core.ColorAdjust
import br.com.riseedit.core.color.ColorGrade
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors

/**
 * Paused fast preview: the current frame is decoded once (bitmap) and re-colored here with the
 * exact export shader ([ColorPass]) in an offscreen EGL context, then read back. No player seek,
 * so it updates on every slider/curve change even when ExoPlayer would drop a same-position seek.
 */
internal object PausedColorRenderer {
    const val TAG = "RiseColor"
    private val dispatcher = Executors.newSingleThreadExecutor { Thread(it, "RiseColorGl") }.asCoroutineDispatcher()
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var surface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var pass: ColorPass? = null
    private var srcTex = 0
    private var fbo = 0
    private var outTex = 0
    private var width = 0
    private var height = 0
    private var buffer: ByteBuffer? = null
    /** Last uploaded ORIGINAL paused frame (qualifier eyedropper samples it). */
    @Volatile var source: Bitmap? = null
        private set

    /** Uploads the decoded paused frame (returns false if GL is unavailable). */
    suspend fun setFrame(frame: Bitmap): Boolean = withContext(dispatcher) {
        try {
            ensureGl()
            val t0 = System.nanoTime()
            if (srcTex == 0) srcTex = genTexture()
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, srcTex)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, frame, 0)
            if (frame.width != width || frame.height != height) {
                width = frame.width
                height = frame.height
                if (outTex == 0) outTex = genTexture()
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, outTex)
                GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
                if (fbo == 0) fbo = IntArray(1).also { GLES20.glGenFramebuffers(1, it, 0) }[0]
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
                GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, outTex, 0)
                buffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
            }
            source = frame
            Log.d(TAG, "frame uploaded ${width}x$height in ${(System.nanoTime() - t0) / 1_000_000}ms")
            true
        } catch (error: Throwable) {
            Log.w(TAG, "setFrame failed", error)
            false
        }
    }

    /** Renders the uploaded frame with [adjust]/[grade]; null on failure. */
    suspend fun render(adjust: ColorAdjust, grade: ColorGrade, bypass: Boolean): Bitmap? = withContext(dispatcher) {
        try {
            val p = pass ?: return@withContext null
            val buf = buffer ?: return@withContext null
            val t0 = System.nanoTime()
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
            GLES20.glViewport(0, 0, width, height)
            p.draw(srcTex, adjust, grade, bypass, aspect = width.toFloat() / height, flipY = true, showMask = ColorLive.showMask)
            buf.rewind()
            GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
            buf.rewind()
            val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            out.copyPixelsFromBuffer(buf)
            Log.d(TAG, "render ${(System.nanoTime() - t0) / 1_000_000}ms bypass=$bypass neutral=${adjust.isNeutral && grade.isIdentity}")
            out
        } catch (error: Throwable) {
            Log.w(TAG, "render failed", error)
            null
        }
    }

    private var thumbSrc = 0
    private var thumbOut = 0
    private var thumbFbo = 0
    private var thumbW = 0
    private var thumbH = 0

    /**
     * Filter thumbnails: renders [frame] (small, ~160px) once per entry of [looks] with the same
     * [ColorPass] as export/overlay, on separate textures so the paused overlay frame is untouched.
     */
    suspend fun thumbnails(frame: Bitmap, looks: List<ColorAdjust>, grade: ColorGrade): List<Bitmap>? = withContext(dispatcher) {
        try {
            ensureGl()
            val p = pass ?: return@withContext null
            if (thumbSrc == 0) thumbSrc = genTexture()
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, thumbSrc)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, frame, 0)
            if (frame.width != thumbW || frame.height != thumbH) {
                thumbW = frame.width
                thumbH = frame.height
                if (thumbOut == 0) thumbOut = genTexture()
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, thumbOut)
                GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, thumbW, thumbH, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
                if (thumbFbo == 0) thumbFbo = IntArray(1).also { GLES20.glGenFramebuffers(1, it, 0) }[0]
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, thumbFbo)
                GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, thumbOut, 0)
            }
            val buf = ByteBuffer.allocateDirect(thumbW * thumbH * 4).order(ByteOrder.nativeOrder())
            val t0 = System.nanoTime()
            val out = looks.map { look ->
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, thumbFbo)
                GLES20.glViewport(0, 0, thumbW, thumbH)
                p.draw(thumbSrc, look, grade, false, aspect = thumbW.toFloat() / thumbH, flipY = true)
                buf.rewind()
                GLES20.glReadPixels(0, 0, thumbW, thumbH, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
                buf.rewind()
                Bitmap.createBitmap(thumbW, thumbH, Bitmap.Config.ARGB_8888).also { it.copyPixelsFromBuffer(buf) }
            }
            Log.d(TAG, "filter thumbnails ${out.size}x ${thumbW}x$thumbH in ${(System.nanoTime() - t0) / 1_000_000}ms")
            out
        } catch (error: Throwable) {
            Log.w(TAG, "thumbnails failed", error)
            null
        }
    }

    private fun genTexture(): Int {
        val id = IntArray(1).also { GLES20.glGenTextures(1, it, 0) }[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return id
    }

    private fun ensureGl() {
        if (context != EGL14.EGL_NO_CONTEXT) return
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val v = IntArray(2)
        check(EGL14.eglInitialize(display, v, 0, v, 1)) { "eglInitialize" }
        val attrs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val n = IntArray(1)
        check(EGL14.eglChooseConfig(display, attrs, 0, configs, 0, 1, n, 0) && n[0] > 0) { "eglChooseConfig" }
        context = EGL14.eglCreateContext(
            display, configs[0], EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0
        )
        surface = EGL14.eglCreatePbufferSurface(display, configs[0], intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
        check(EGL14.eglMakeCurrent(display, surface, surface, context)) { "eglMakeCurrent" }
        pass = ColorPass()
        Log.i(TAG, "GL ready: ${GLES20.glGetString(GLES20.GL_RENDERER)}")
    }
}
