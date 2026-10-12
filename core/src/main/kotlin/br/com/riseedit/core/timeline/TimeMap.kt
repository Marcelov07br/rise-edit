package br.com.riseedit.core.timeline

/**
 * Positive, monotone mapping for one segment. Both conversion endpoints are clamped.
 * Integer output quantization prevents a universal one-microsecond source round trip:
 * at 4x, source 2us rounds to target 1us and back to source 4us.
 * Implementations must be immutable; targetRange starts at zero.
 */
interface TimeMap {
    val sourceRange: TimeRange
    val targetRange: TimeRange
    fun sourceToTarget(sourceUs: Long): Long
    fun targetToSource(targetUs: Long): Long
    fun speedAt(targetUs: Long): Double
}
