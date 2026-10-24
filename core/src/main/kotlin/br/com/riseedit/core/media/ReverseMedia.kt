package br.com.riseedit.core.media

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** 0.23.0: pure parts of Reverse/Freeze (testable on the JVM). */
object ReverseMedia {
    /**
     * Source window covered by a reversed clip of [count] frames of [stepMs] ending at [endMs]
     * (frame i shows source endMs-1-i·step). Audio uses exactly this window so A/V stay in sync.
     */
    fun window(startMs: Long, endMs: Long, stepMs: Long): LongRange {
        val count = maxOf(1, ((endMs - startMs) / stepMs).toInt())
        return (endMs - count * stepMs).coerceAtLeast(0)..endMs
    }

    /** Reverses interleaved 16-bit PCM by whole sample frames (channels stay in order). */
    fun reverseFrames(pcm: ShortArray, channels: Int): ShortArray {
        require(channels >= 1)
        val frames = pcm.size / channels
        val out = ShortArray(frames * channels)
        for (f in 0 until frames) {
            val src = (frames - 1 - f) * channels
            for (c in 0 until channels) out[f * channels + c] = pcm[src + c]
        }
        return out
    }

    /** Interleaved samples for [fromMs, toMs) of a stream that starts at [firstSampleUs]. */
    fun slice(pcm: ShortArray, channels: Int, rate: Int, firstSampleUs: Long, fromMs: Long, toMs: Long): ShortArray {
        val frames = pcm.size / channels
        fun idx(ms: Long) = ((ms * 1000 - firstSampleUs) * rate / 1_000_000L).toInt().coerceIn(0, frames)
        val a = idx(fromMs); val b = idx(toMs)
        return pcm.copyOfRange(a * channels, b * channels)
    }

    /** Canonical 44-byte PCM WAV (16-bit little endian). */
    fun wav(pcm: ShortArray, rate: Int, channels: Int): ByteArray {
        val data = pcm.size * 2
        val b = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + data).put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(channels.toShort())
            .putInt(rate).putInt(rate * channels * 2).putShort((channels * 2).toShort()).putShort(16)
        b.put("data".toByteArray()).putInt(data)
        pcm.forEach { b.putShort(it) }
        return b.array()
    }
}

/**
 * 0.23.0: container rotation metadata (MediaMetadataRetriever / MediaFormat KEY_ROTATION).
 * MediaMetadataRetriever.getFrameAtTime returns the *stored* (unrotated) picture, so freeze and
 * reverse frames must be turned by [degrees] clockwise to show upright like the player does.
 */
object FrameRotation {
    fun normalize(degrees: Int): Int = ((degrees % 360) + 360) % 360 / 90 * 90
    fun swapsAxes(degrees: Int) = normalize(degrees) % 180 == 90
    fun size(width: Int, height: Int, degrees: Int): Pair<Int, Int> =
        if (swapsAxes(degrees)) height to width else width to height

    /**
     * Degrees (clockwise) still to apply to a retriever frame. Most builds return frames already
     * upright, but some decoders/ROMs ignore the rotation and hand back the stored picture —
     * detectable for 90/270 because the bitmap keeps the stored (unswapped) aspect.
     * 180 cannot be detected from the size and is trusted to the retriever.
     */
    fun pending(bitmapW: Int, bitmapH: Int, storedW: Int, storedH: Int, degrees: Int): Int {
        if (!swapsAxes(degrees) || storedW == storedH || storedW <= 0 || storedH <= 0) return 0
        val bitmapLandscape = bitmapW > bitmapH
        val storedLandscape = storedW > storedH
        return if (bitmapLandscape == storedLandscape) normalize(degrees) else 0
    }

    /** Where stored pixel (x,y) lands after the clockwise rotation (w,h = stored size). */
    fun map(x: Int, y: Int, width: Int, height: Int, degrees: Int): Pair<Int, Int> = when (normalize(degrees)) {
        90 -> (height - 1 - y) to x
        180 -> (width - 1 - x) to (height - 1 - y)
        270 -> y to (width - 1 - x)
        else -> x to y
    }
}
