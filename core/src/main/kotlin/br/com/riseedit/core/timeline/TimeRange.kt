package br.com.riseedit.core.timeline

/** Half-open media interval. Conversions may explicitly accept its final boundary. */
data class TimeRange(val startUs: Long, val endUs: Long) : Comparable<TimeRange> {
    init {
        require(startUs >= 0) { "Start must be nonnegative" }
        require(endUs > startUs) { "Range must have positive duration" }
    }

    val durationUs: Long get() = endUs - startUs

    operator fun contains(timeUs: Long): Boolean = timeUs >= startUs && timeUs < endUs

    fun overlaps(other: TimeRange): Boolean = startUs < other.endUs && other.startUs < endUs

    fun intersect(other: TimeRange): TimeRange? {
        val start = maxOf(startUs, other.startUs)
        val end = minOf(endUs, other.endUs)
        return if (start < end) TimeRange(start, end) else null
    }

    fun shifted(deltaUs: Long): TimeRange = TimeRange(
        Math.addExact(startUs, deltaUs),
        Math.addExact(endUs, deltaUs)
    )

    override fun compareTo(other: TimeRange): Int = compareValuesBy(this, other, {
        it.startUs
    }, { it.endUs })

    companion object {
        fun fromMs(startMs: Long, endMs: Long): TimeRange = TimeRange(
            Math.multiplyExact(startMs, 1000),
            Math.multiplyExact(endMs, 1000)
        )
    }
}
