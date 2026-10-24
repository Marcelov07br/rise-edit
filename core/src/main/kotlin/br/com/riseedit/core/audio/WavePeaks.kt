package br.com.riseedit.core.audio

/**
 * 0.23.0: waveform peaks per pixel bucket, computed once per (samples, trim, zoom level) and
 * reused by every draw — the Canvas no longer walks/filters all samples on each frame.
 * [samples] cover the whole source (0..sourceDurationMs); the result has [buckets] peaks (max per
 * bucket) for the trimmed window startMs..endMs. Sparse samples repeat so the wave stays continuous.
 */
object WavePeaks {
    /** Zoom level quantization: bucket count rounded up to a power of two, capped. */
    fun level(widthPx: Float, pxPerBucket: Float = 3f, max: Int = 4096): Int {
        val want = (widthPx / pxPerBucket).toInt().coerceIn(1, max)
        var l = 1
        while (l < want) l = l shl 1
        return l.coerceAtMost(max)
    }

    fun peaks(samples: FloatArray, sourceDurationMs: Long, startMs: Long, endMs: Long, buckets: Int): FloatArray {
        val out = FloatArray(maxOf(buckets, 0))
        if (samples.isEmpty() || sourceDurationMs <= 0 || endMs <= startMs || buckets <= 0) return out
        val n = samples.size
        val span = (endMs - startMs).toDouble()
        for (b in 0 until buckets) {
            val t0 = startMs + span * b / buckets
            val t1 = startMs + span * (b + 1) / buckets
            val i0 = (t0 / sourceDurationMs * n).toInt().coerceIn(0, n - 1)
            val i1 = (t1 / sourceDurationMs * n).toInt().coerceIn(i0 + 1, n)
            var m = 0f
            for (i in i0 until i1) if (samples[i] > m) m = samples[i]
            out[b] = m
        }
        return out
    }
}
