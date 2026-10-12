package br.com.riseedit.core.timeline

/** Contiguous source pieces with binary lookup and cumulative target positions. */
data class PiecewiseMap(val pieces: FrozenList<TimeMap>) : TimeMap {
    constructor(pieces: List<TimeMap>) : this(FrozenList(pieces))

    init {
        require(pieces.isNotEmpty()) { "At least one piece is required" }
        require(pieces.all { it.targetRange.startUs == 0L })
        require(pieces.zipWithNext().all { (a, b) -> a.sourceRange.endUs == b.sourceRange.startUs }) {
            "Source pieces must be contiguous and ordered"
        }
    }

    private val starts = LongArray(pieces.size + 1).apply {
        pieces.indices.forEach { set(it + 1, Math.addExact(get(it), pieces[it].targetRange.durationUs)) }
    }
    override val sourceRange = TimeRange(pieces.first().sourceRange.startUs, pieces.last().sourceRange.endUs)
    override val targetRange = TimeRange(0, starts.last())

    override fun sourceToTarget(sourceUs: Long): Long {
        val bounded = sourceUs.coerceIn(sourceRange.startUs, sourceRange.endUs)
        val index = locate(bounded) { pieces[it].sourceRange.startUs }
        return starts[index] + pieces[index].sourceToTarget(bounded)
    }

    override fun targetToSource(targetUs: Long): Long {
        val bounded = targetUs.coerceIn(0, targetRange.endUs)
        val index = locate(bounded) { starts[it] }
        return pieces[index].targetToSource(bounded - starts[index])
    }

    override fun speedAt(targetUs: Long): Double {
        val bounded = targetUs.coerceIn(0, targetRange.endUs)
        val index = locate(bounded) { starts[it] }
        return pieces[index].speedAt(bounded - starts[index])
    }

    private inline fun locate(value: Long, start: (Int) -> Long): Int {
        var low = 0
        var high = pieces.lastIndex
        while (low < high) {
            val middle = low + (high - low + 1) / 2
            if (start(middle) <= value) low = middle else high = middle - 1
        }
        return low
    }
}
