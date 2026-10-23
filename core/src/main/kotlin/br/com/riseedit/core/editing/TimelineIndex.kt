package br.com.riseedit.core.editing

/**
 * Perf (0.22.2): visible-range lookup for a timeline lane in O(log n) instead of walking
 * every item each frame. Items are sorted by start; [maxEnd] is the running max of ends so
 * lanes whose items overlap (overlays, audio) are handled too.
 */
class TimelineIndex private constructor(
    private val starts: LongArray,
    private val maxEnd: LongArray
) {
    val size get() = starts.size

    /** Indices (into the sorted order used at build time) overlapping [fromMs, toMs). */
    fun visible(fromMs: Long, toMs: Long): IntRange {
        if (size == 0 || toMs <= fromMs) return IntRange.EMPTY
        // first i with maxEnd[i] > fromMs
        var lo = 0
        var hi = size
        while (lo < hi) { val m = (lo + hi) ushr 1; if (maxEnd[m] > fromMs) hi = m else lo = m + 1 }
        val first = lo
        // first i with start >= toMs
        lo = first; hi = size
        while (lo < hi) { val m = (lo + hi) ushr 1; if (starts[m] >= toMs) hi = m else lo = m + 1 }
        return first until lo
    }

    fun startOf(i: Int) = starts[i]

    companion object {
        /** Main track: items laid end to end, given their durations. */
        fun of(durationsMs: List<Long>): TimelineIndex {
            val s = LongArray(durationsMs.size)
            val e = LongArray(durationsMs.size)
            var off = 0L
            durationsMs.forEachIndexed { i, d -> s[i] = off; off += d; e[i] = off }
            return TimelineIndex(s, e)
        }

        /** Free lane: (start, end) pairs already sorted by start. */
        fun ofSpans(startsMs: LongArray, endsMs: LongArray): TimelineIndex {
            require(startsMs.size == endsMs.size)
            val m = LongArray(endsMs.size)
            var run = Long.MIN_VALUE
            for (i in endsMs.indices) { run = maxOf(run, endsMs[i]); m[i] = run }
            return TimelineIndex(startsMs.copyOf(), m)
        }
    }
}
