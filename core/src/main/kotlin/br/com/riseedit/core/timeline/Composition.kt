package br.com.riseedit.core.timeline

/** Structural timeline model, independent of rendering effects and Android players. */
data class Composition(val tracks: FrozenList<Track>, val mainTrackId: String) {
    constructor(tracks: List<Track>, mainTrackId: String) : this(FrozenList(tracks), mainTrackId)

    private val mainTrack: Track
        get() = requireNotNull(
            tracks.singleOrNull {
                it.id == mainTrackId
            }
        ) { "Main track must exist uniquely" }

    /** Main-track content length, not the maximum end of auxiliary tracks. */
    val durationUs: Long get() = mainTrack.durationUs

    fun validate() {
        val ids = tracks.map { it.id } + tracks.flatMap { it.segments }.map { it.id }
        require(
            ids.all {
                it.isNotBlank()
            } &&
                ids.distinct().size == ids.size
        ) { "All IDs must be unique and nonblank" }
        val names = tracks.map { it.kind to it.name }
        require(names.distinct().size == names.size) {
            "Track names must be unique within each kind"
        }
        require(tracks.all { it.startUs >= 0 }) { "Track starts must be nonnegative" }
        require(
            tracks.flatMap {
                it.segments
            }.all { it.durationUs > 0 }
        ) { "Segments must have positive duration" }
        require(mainTrack.kind == TrackKind.VIDEO) { "Main track must be video" }
    }

    /**
     * Structural hash only: IDs/names are excluded, order and main-track identity matter.
     * Effects/text are absent from v1, and Int hashes may collide. This is not sufficient
     * to skip a full Project preview rebuild or to establish render equality.
     */
    fun renderFingerprint(): Int {
        validate()
        val content = tracks.map { track ->
            listOf(
                track.kind,
                track.startUs,
                track.segments.map {
                    listOf(it.source, mapKey(it.timeMap))
                }
            )
        }
        return listOf(tracks.indexOfFirst { it.id == mainTrackId }, content).hashCode()
    }

    private fun mapKey(map: TimeMap): Any = when (map) {
        is ConstantSpeedMap -> listOf("constant", map.sourceRange, map.targetRange, map.speed)
        is PiecewiseMap -> listOf(
            "piecewise",
            map.cumulativeRounding,
            map.targetRange,
            map.pieces.map(::mapKey)
        )
        else -> throw IllegalArgumentException(
            "Structural fingerprint requires a supported immutable time map"
        )
    }
}
