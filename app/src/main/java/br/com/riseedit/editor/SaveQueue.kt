package br.com.riseedit.editor

import br.com.riseedit.core.Project
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * 0.23.0: background, serialized, debounced project save.
 *
 * - [submit] is cheap (main thread): it only replaces the pending project and (re)arms the timer.
 * - One writer thread: writes never overlap or reorder; the newest submitted project always wins.
 * - [flush] writes the pending project now and blocks until it is on disk (onStop / leaving / loading).
 * - The write itself stays atomic (ProjectStore → AtomicFile). A failed write keeps the project
 *   pending so the next submit/flush retries it — an edit is never dropped silently.
 */
internal class SaveQueue(
    private val write: (Project) -> Unit,
    private val onError: (Throwable) -> Unit = {},
    private val debounceMs: Long = 400,
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "rise-save").apply { isDaemon = true }
    }
) {
    private val lock = Any()
    private var pending: Project? = null
    private var timer: ScheduledFuture<*>? = null

    val hasPending: Boolean get() = synchronized(lock) { pending != null }

    fun submit(project: Project) {
        synchronized(lock) {
            pending = project
            timer?.cancel(false)
            timer = executor.schedule({ drain() }, debounceMs, TimeUnit.MILLISECONDS)
        }
    }

    /** Writes whatever is pending and waits for it (including a write already in progress). */
    fun flush(): Boolean {
        synchronized(lock) { timer?.cancel(false) }
        return executor.submit<Boolean> { drain() }.get()
    }

    /** Runs only on the writer thread. */
    private fun drain(): Boolean {
        val project = synchronized(lock) { pending } ?: return true
        return try {
            write(project)
            synchronized(lock) { if (pending === project) pending = null }
            true
        } catch (error: Throwable) {
            onError(error)
            false
        }
    }

    fun close() {
        flush()
        executor.shutdown()
    }
}
