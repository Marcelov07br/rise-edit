package br.com.riseedit.core.timeline

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** Contiguous source pieces with binary lookup and cumulative target positions. */
data class PiecewiseMap(val pieces: FrozenList<TimeMap>, val cumulativeRounding: Boolean = false) :
    TimeMap {
    constructor(
        pieces: List<TimeMap>,
        cumulativeRounding: Boolean = false
    ) : this(FrozenList(pieces), cumulativeRounding)

    init {
        require(pieces.isNotEmpty()) { "At least one piece is required" }
        require(pieces.all { it.targetRange.startUs == 0L })
        require(
            pieces.zipWithNext().all { (a, b) ->
                a.sourceRange.endUs == b.sourceRange.startUs
            }
        ) {
            "Source pieces must be contiguous and ordered"
        }
    }

    private val starts = buildStarts()
    override val sourceRange =
        TimeRange(pieces.first().sourceRange.startUs, pieces.last().sourceRange.endUs)
    override val targetRange = TimeRange(0, starts.last())

    override fun sourceToTarget(sourceUs: Long): Long {
        val bounded = sourceUs.coerceIn(sourceRange.startUs, sourceRange.endUs)
        if (bounded == sourceRange.endUs) return targetRange.endUs
        val index = locate(bounded) { pieces[it].sourceRange.startUs }
        val local = pieces[index].sourceToTarget(bounded).coerceAtMost(
            starts[index + 1] - starts[index]
        )
        return starts[index] + local
    }

    override fun targetToSource(targetUs: Long): Long {
        val bounded = targetUs.coerceIn(0, targetRange.endUs)
        if (bounded == targetRange.endUs) return sourceRange.endUs
        val index = locate(bounded) { starts[it] }
        return pieces[index].targetToSource(bounded - starts[index])
    }

    override fun speedAt(targetUs: Long): Double {
        val bounded = targetUs.coerceIn(0, targetRange.endUs)
        val index = locate(bounded) { starts[it] }
        return pieces[index].speedAt(bounded - starts[index])
    }

    /** The adapter rounds cumulative boundaries to avoid drift over thousands of speed steps. */
    private fun buildStarts(): LongArray {
        var exact = BigDecimal.ZERO
        return LongArray(pieces.size + 1).apply {
            pieces.indices.forEach { index ->
                val piece = pieces[index]
                if (cumulativeRounding) {
                    require(piece is ConstantSpeedMap) {
                        "Cumulative rounding requires constant-speed pieces"
                    }
                    exact = exact.add(
                        BigDecimal.valueOf(piece.sourceRange.durationUs)
                            .divide(BigDecimal.valueOf(piece.speed), MathContext.DECIMAL128)
                    )
                    set(index + 1, exact.setScale(0, RoundingMode.HALF_UP).longValueExact())
                } else {
                    set(index + 1, Math.addExact(get(index), piece.targetRange.durationUs))
                }
                require(get(index + 1) > get(index)) {
                    "Rounded pieces must retain positive duration"
                }
            }
        }
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
