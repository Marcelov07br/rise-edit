package br.com.riseedit.core.perf

import java.lang.management.ManagementFactory

/** Tiny JVM harness: warmup, then median wall time and allocated bytes per run (this thread). */
object PerfBench {
    data class Result(val name: String, val medianMs: Double, val allocKb: Long) {
        override fun toString() = "%-34s %9.3f ms %10d KB".format(name, medianMs, allocKb)
    }
    private val mx = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
    val results = mutableListOf<Result>()

    fun <T> run(name: String, warmup: Int = 5, runs: Int = 15, block: () -> T): Result {
        repeat(warmup) { block() }
        val times = DoubleArray(runs)
        val allocs = LongArray(runs)
        val id = Thread.currentThread().id
        for (i in 0 until runs) {
            val a0 = mx.getThreadAllocatedBytes(id)
            val t0 = System.nanoTime()
            block()
            times[i] = (System.nanoTime() - t0) / 1e6
            allocs[i] = mx.getThreadAllocatedBytes(id) - a0
        }
        times.sort(); allocs.sort()
        return Result(name, times[runs / 2], allocs[runs / 2] / 1024).also { results += it; println("PERF $it") }
    }
}
