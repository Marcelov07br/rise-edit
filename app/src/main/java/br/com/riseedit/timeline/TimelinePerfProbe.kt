package br.com.riseedit.timeline

/** Perf 0.22.2: cheap counters read by the JVM perf tests (plain Int increments). */
internal object TimelinePerfProbe {
    /** TimelineClip invocations (including off-screen ones that return early). */
    var clipCalls = 0
    /** TimelineClip invocations that emitted a clip node (on screen). */
    var clipEmitted = 0
    /** timelineEntries() rebuilds. */
    var entryBuilds = 0
    fun reset() { clipCalls = 0; clipEmitted = 0; entryBuilds = 0 }
}
