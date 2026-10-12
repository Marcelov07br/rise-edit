package br.com.riseedit.core.timeline

/** A source interval retimed independently of its placement on a track. */
data class Segment(val id: String, val source: SegmentSource, val timeMap: TimeMap) {
    init {
        require(id.isNotBlank()) { "Segment ID must not be blank" }
        require(timeMap.targetRange.startUs == 0L) { "Segment time starts at zero" }
        when (source) {
            is SegmentSource.Media -> require(timeMap.sourceRange.endUs <= source.originalDurationUs) {
                "Trim exceeds the original media duration"
            }
            is SegmentSource.Still -> Unit
            SegmentSource.Gap -> require(timeMap is ConstantSpeedMap && timeMap.speed == 1.0) {
                "Gaps require an identity constant-speed map"
            }
        }
    }

    val durationUs: Long get() = timeMap.targetRange.durationUs

    companion object {
        fun gap(id: String, durationUs: Long): Segment = Segment(
            id,
            SegmentSource.Gap,
            ConstantSpeedMap(TimeRange(0, durationUs), 1.0),
        )
    }
}
