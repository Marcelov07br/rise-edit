package br.com.riseedit.core.timeline

enum class TrackKind { VIDEO, AUDIO }

/** Sequential segments on a global timeline; gaps are explicit segments. */
data class Track(
    val id: String,
    val name: String,
    val kind: TrackKind,
    val startUs: Long,
    val segments: FrozenList<Segment>
) {
    constructor(id: String, name: String, kind: TrackKind, startUs: Long, segments: List<Segment>) :
        this(id, name, kind, startUs, FrozenList(segments))

    init {
        require(startUs >= 0) { "Track start must be nonnegative" }
        require(
            segments.map {
                it.id
            }.distinct().size == segments.size
        ) { "Segment IDs must be unique on a track" }
    }

    private val starts = LongArray(segments.size + 1).apply {
        segments.indices.forEach { set(it + 1, Math.addExact(get(it), segments[it].durationUs)) }
    }
    val durationUs: Long get() = starts.last()
    val endUs: Long = Math.addExact(startUs, durationUs)

    data class Located(val index: Int, val localUs: Long)

    fun segmentStartUs(index: Int): Long {
        require(index in segments.indices) { "Unknown segment index" }
        return startUs + starts[index]
    }

    fun segmentAt(timelineUs: Long): Located? {
        if (timelineUs < startUs || timelineUs >= endUs) return null
        val local = timelineUs - startUs
        var low = 0
        var high = segments.lastIndex
        while (low < high) {
            val middle = low + (high - low + 1) / 2
            if (starts[middle] <= local) low = middle else high = middle - 1
        }
        return Located(low, local - starts[low])
    }

    /** Outside the track and inside a gap there is no source media position. */
    fun timelineToSource(timelineUs: Long): Long? {
        val located = segmentAt(timelineUs) ?: return null
        val segment = segments[located.index]
        if (segment.source == SegmentSource.Gap) return null
        return segment.timeMap.targetToSource(located.localUs)
    }

    /** Conversion endpoints are allowed even though lookup intervals are half-open. */
    fun sourceToTimeline(segmentId: String, sourceUs: Long): Long? {
        val index = segments.indexOfFirst { it.id == segmentId }
        if (index < 0 || segments[index].source == SegmentSource.Gap) return null
        return segmentStartUs(index) + segments[index].timeMap.sourceToTarget(sourceUs)
    }
}
