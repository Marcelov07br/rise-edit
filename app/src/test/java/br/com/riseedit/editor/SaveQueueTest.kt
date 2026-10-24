package br.com.riseedit.editor

import br.com.riseedit.core.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

class SaveQueueTest {
    private fun p(n: String) = Project(name = n)

    @Test fun debouncedLatestWins() {
        val written = Collections.synchronizedList(mutableListOf<String>())
        val q = SaveQueue({ written += it.name }, debounceMs = 200)
        q.submit(p("a")); q.submit(p("b")); q.submit(p("c"))
        Thread.sleep(500)
        assertEquals(listOf("c"), written)
        assertFalse(q.hasPending)
    }

    @Test fun flushWritesImmediatelyAndWaits() {
        val written = Collections.synchronizedList(mutableListOf<String>())
        val q = SaveQueue({ Thread.sleep(50); written += it.name }, debounceMs = 10_000)
        q.submit(p("x"))
        assertTrue(q.flush())
        assertEquals(listOf("x"), written)
        assertTrue(q.flush()) // nothing pending: no second write
        assertEquals(1, written.size)
    }

    @Test fun serializedNoOverlap() {
        var active = 0; var maxActive = 0
        val q = SaveQueue({ synchronized(this) { active++; maxActive = maxOf(maxActive, active) }; Thread.sleep(20); synchronized(this) { active-- } }, debounceMs = 0)
        repeat(20) { q.submit(p("$it")); Thread.sleep(3) }
        q.flush()
        assertEquals(1, maxActive)
    }

    @Test fun failedWriteStaysPendingAndRetries() {
        var fail = true
        val written = mutableListOf<String>()
        val errors = mutableListOf<Throwable>()
        val q = SaveQueue({ if (fail) error("disk full") else written += it.name }, { errors += it }, debounceMs = 10_000)
        q.submit(p("e"))
        assertFalse(q.flush())
        assertTrue(q.hasPending)
        assertEquals(1, errors.size)
        fail = false
        assertTrue(q.flush())
        assertEquals(listOf("e"), written)
    }
}
