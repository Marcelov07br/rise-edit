@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package br.com.riseedit

import android.graphics.Matrix
import androidx.media3.common.C
import androidx.media3.common.audio.*
import androidx.media3.effect.*
import br.com.riseedit.core.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

class ClipSpeed(private val clip: Clip) : SpeedProvider {
    override fun getSpeed(timeUs: Long) = clip.speedAt(clip.startMs + timeUs / 1000).toFloat()
    override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = clip.speedPoints.firstOrNull {
        it.sourceMs > clip.startMs + timeUs / 1000 &&
            it.sourceMs < clip.endMs
    }
        ?.let { (it.sourceMs - clip.startMs) * 1000 } ?: C.TIME_UNSET
}

class ClipTransform(private val clip: Clip, private val offset: Long) : MatrixTransformation {
    private val matrix = Matrix()
    override fun getMatrix(presentationTimeUs: Long): Matrix {
        val source = clip.sourceAt((presentationTimeUs / 1000 - offset).coerceAtLeast(0))
        val k = Timeline.keyframe(clip, source)
        val o = clip.orientation
        matrix.reset()
        val sx = k.scale * (if (o.flipHorizontal) -1f else 1f)
        val sy = k.scale * (if (o.flipVertical) -1f else 1f)
        matrix.postScale(sx, sy)
        matrix.postRotate(k.rotation + o.rotationDegrees)
        matrix.postTranslate(k.x, k.y)
        return matrix
    }
}

/** Fade through black; does not overlap or shorten neighboring clips. */
class ClipColor(private val clip: Clip, private val offset: Long) : RgbMatrix {
    override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray {
        val time = (presentationTimeUs / 1000 - offset).coerceIn(0, clip.durationMs)
        val fade = min(clip.fadeMs, clip.durationMs / 2)
        val gain = if (fade <=
            0
        ) {
            1f
        } else {
            min(
                1.0,
                min(time.toDouble() / fade, (clip.durationMs - time).toDouble() / fade)
            ).toFloat()
        }
        val alpha = Timeline.keyframe(clip, clip.sourceAt(time)).opacity
        return floatArrayOf(
            gain, 0f, 0f, 0f, 0f, gain, 0f, 0f, 0f, 0f,
            gain, 0f, 0f, 0f, 0f, alpha
        )
    }
}

class AudioEnvelope(
    private val volume: Float,
    private val durationMs: Long,
    private val fadeMs: Long
) : BaseAudioProcessor() {
    private var samples = 0L
    private var seekUs = 0L
    override fun onConfigure(format: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (format.encoding != C.ENCODING_PCM_16BIT &&
            format.encoding != C.ENCODING_PCM_FLOAT
        ) {
            throw AudioProcessor.UnhandledAudioFormatException(format)
        }
        return format
    }
    override fun onFlush(metadata: AudioProcessor.StreamMetadata) {
        samples = 0
        seekUs =
            metadata.positionOffsetUs.coerceAtLeast(0)
    }
    override fun onFlush() {
        samples = 0
        seekUs = 0
    }
    override fun queueInput(input: ByteBuffer) {
        input.order(ByteOrder.nativeOrder())
        val out = replaceOutputBuffer(input.remaining())
        val isFloat = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        val bytes = if (isFloat) 4 else 2
        while (input.remaining() >= bytes) {
            val ms =
                seekUs / 1000.0 +
                    samples / inputAudioFormat.channelCount.toDouble() * 1000 /
                    inputAudioFormat.sampleRate
            val fade = min(fadeMs, durationMs / 2).toDouble()
            val envelope = if (fade <=
                0
            ) {
                1.0
            } else {
                min(1.0, min(ms / fade, (durationMs - ms) / fade)).coerceAtLeast(0.0)
            }
            val gain = volume * envelope
            if (isFloat) {
                out.putFloat((input.float * gain).toFloat().coerceIn(-1f, 1f))
            } else {
                out.putShort((input.short * gain).roundToInt().coerceIn(-32768, 32767).toShort())
            }
            samples++
        }
        out.flip()
    }
}
