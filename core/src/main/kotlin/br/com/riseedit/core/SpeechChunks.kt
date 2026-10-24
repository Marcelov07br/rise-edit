package br.com.riseedit.core

import kotlin.math.sqrt

/**
 * 0.25.0: energy VAD + chunk planning for faster on-device captions.
 *
 * Silence is cut out: each [SpeechChunk] is a list of voiced [pieces] (sample ranges of the
 * clip audio) that are glued with [GLUE_MS] of silence and transcribed in one whisper call
 * (≤ [MAX_CHUNK_MS], whisper's 30 s window). Results map back with [SpeechChunk.toSourceMs].
 * Chunks are transcribed in order, so captions appear chunk by chunk.
 */
data class SpeechChunk(val pieces: List<LongRange>) {
    /** Glued length in samples (pieces + glue between them). */
    fun samples(rate: Int): Long =
        pieces.sumOf { it.last - it.first + 1 } + (pieces.size - 1).coerceAtLeast(0) * glue(rate)

    /** Glued-audio ms → source ms (since the clip audio starts). */
    fun toSourceMs(gluedMs: Long, rate: Int): Long {
        var at = 0L
        val glue = glue(rate)
        for ((i, p) in pieces.withIndex()) {
            val len = p.last - p.first + 1
            val s = gluedMs * rate / 1000
            if (s < at + len || i == pieces.lastIndex) {
                val inside = (s - at).coerceIn(0, len)
                return (p.first + inside) * 1000 / rate
            }
            at += len
            if (s < at + glue) return (p.last + 1) * 1000 / rate
            at += glue
        }
        return 0
    }

    companion object {
        const val GLUE_MS = 200L
        fun glue(rate: Int) = GLUE_MS * rate / 1000
    }
}

object SpeechChunks {
    const val FRAME_MS = 30
    const val MAX_CHUNK_MS = 28_000L
    private const val MIN_THRESHOLD = 0.006f
    private const val NOISE_FACTOR = 3f
    private const val BRIDGE_MS = 450L
    private const val MIN_VOICE_MS = 120L
    private const val PAD_MS = 250L

    /** RMS of each [FRAME_MS] frame. */
    fun energy(pcm: FloatArray, rate: Int = 16_000): FloatArray {
        val frame = rate * FRAME_MS / 1000
        val n = pcm.size / frame
        return FloatArray(n) { f ->
            var sum = 0.0
            val base = f * frame
            for (i in 0 until frame) {
                val v = pcm[base + i]
                sum += v * v
            }
            sqrt(sum / frame).toFloat()
        }
    }

    /** Voiced sample ranges: adaptive threshold over the noise floor, gaps bridged, padded. */
    fun voiced(pcm: FloatArray, rate: Int = 16_000): List<LongRange> {
        val e = energy(pcm, rate)
        if (e.isEmpty()) return emptyList()
        val sorted = e.sorted()
        val noise = sorted[(sorted.size * 0.15f).toInt().coerceAtMost(sorted.lastIndex)]
        val peak = sorted[(sorted.size * 0.98f).toInt().coerceAtMost(sorted.lastIndex)]
        // Loud constant noise (music bed): never set the bar above half of the speech peaks.
        val threshold = maxOf(MIN_THRESHOLD, minOf(noise * NOISE_FACTOR, peak * 0.5f))
        val frames = mutableListOf<IntRange>()
        var start = -1
        e.forEachIndexed { i, v ->
            if (v >= threshold && start < 0) start = i
            if (v < threshold && start >= 0) {
                frames += start until i
                start = -1
            }
        }
        if (start >= 0) frames += start until e.size
        val bridge = (BRIDGE_MS / FRAME_MS).toInt()
        val merged = mutableListOf<IntRange>()
        for (r in frames) {
            val last = merged.lastOrNull()
            if (last != null && r.first - last.last - 1 <= bridge) {
                merged[merged.lastIndex] = last.first..r.last
            } else {
                merged += r
            }
        }
        val minFrames = (MIN_VOICE_MS / FRAME_MS).toInt()
        val frame = rate * FRAME_MS / 1000L
        val pad = PAD_MS * rate / 1000
        val total = pcm.size.toLong()
        val padded = merged.filter { it.last - it.first + 1 >= minFrames }.map {
            (it.first * frame - pad).coerceAtLeast(0)..((it.last + 1) * frame + pad - 1).coerceAtMost(total - 1)
        }
        val out = mutableListOf<LongRange>()
        for (r in padded) {
            val last = out.lastOrNull()
            if (last != null && r.first <= last.last + 1) out[out.lastIndex] = last.first..maxOf(last.last, r.last)
            else out += r
        }
        return out
    }

    /** Groups voiced ranges into ≤ [maxMs] glued chunks; long ranges are split. */
    fun plan(voiced: List<LongRange>, rate: Int = 16_000, maxMs: Long = MAX_CHUNK_MS): List<SpeechChunk> {
        val max = maxMs * rate / 1000
        val glue = SpeechChunk.glue(rate)
        val pieces = voiced.flatMap { r ->
            generateSequence(r.first) { it + max }.takeWhile { it <= r.last }
                .map { it..minOf(r.last, it + max - 1) }.toList()
        }
        val chunks = mutableListOf<SpeechChunk>()
        var current = mutableListOf<LongRange>()
        var size = 0L
        for (p in pieces) {
            val len = p.last - p.first + 1
            val added = if (current.isEmpty()) len else len + glue
            if (current.isNotEmpty() && size + added > max) {
                chunks += SpeechChunk(current)
                current = mutableListOf()
                size = 0
            }
            size += if (current.isEmpty()) len else len + glue
            current += p
        }
        if (current.isNotEmpty()) chunks += SpeechChunk(current)
        return chunks
    }

    /** Glued float audio of [chunk]. */
    fun glued(pcm: FloatArray, chunk: SpeechChunk, rate: Int = 16_000): FloatArray {
        val out = FloatArray(chunk.samples(rate).toInt())
        var at = 0
        val glue = SpeechChunk.glue(rate).toInt()
        chunk.pieces.forEachIndexed { i, p ->
            if (i > 0) at += glue
            val len = (p.last - p.first + 1).toInt()
            System.arraycopy(pcm, p.first.toInt(), out, at, len)
            at += len
        }
        return out
    }

    /** Segments from glued audio → source ms; words follow the same mapping. */
    fun remap(segments: List<SpeechSegment>, chunk: SpeechChunk, rate: Int = 16_000) = segments.map { s ->
        val a = chunk.toSourceMs(s.startMs, rate)
        SpeechSegment(
            a,
            maxOf(a + 1, chunk.toSourceMs(s.endMs, rate)),
            s.text,
            s.words.map { w ->
                val wa = chunk.toSourceMs(w.startMs, rate)
                SpeechWord(wa, maxOf(wa + 1, chunk.toSourceMs(w.endMs, rate)), w.text)
            }
        )
    }
}

/** 0.25.0: whisper threads = the SoC's big cores (little cores slow ggml's thread barrier). */
object CpuTopology {
    /** [maxFreqsKhz] = cpuinfo_max_freq of every core (unknown = empty → fallback). */
    fun bigCores(maxFreqsKhz: List<Long>, fallback: Int): Int {
        val known = maxFreqsKhz.filter { it > 0 }
        if (known.isEmpty()) return fallback.coerceIn(2, 8)
        val top = known.max()
        return known.count { it >= top * 0.7 }.coerceIn(2, 8)
    }
}
