package br.com.riseedit.media

import br.com.riseedit.preview.ScrubSeeker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrubSeekerTest {
    private var clock = 0L
    private val sent = mutableListOf<Long>()
    private val seeker = ScrubSeeker({ sent += it }, { clock }, timeoutMs = 400)

    @Test fun oneInFlightAndLatestWins() {
        seeker.request(100)
        seeker.request(200)
        seeker.request(300)
        assertEquals(listOf(100L), sent)
        seeker.frameRendered()
        assertEquals(listOf(100L, 300L), sent)
        seeker.frameRendered()
        assertEquals(listOf(100L, 300L), sent)
        assertFalse(seeker.waitingForFrame)
    }

    @Test fun timeoutUnblocksSlowDecoder() {
        seeker.request(100)
        clock = 100
        seeker.request(500)
        seeker.poll()
        assertEquals(listOf(100L), sent)
        clock = 450
        seeker.poll()
        assertEquals(listOf(100L, 500L), sent)
        assertTrue(seeker.waitingForFrame)
    }

    @Test fun requestAfterTimeoutSendsDirectly() {
        seeker.request(100)
        clock = 401
        seeker.request(700)
        assertEquals(listOf(100L, 700L), sent)
    }

    @Test fun resetDropsPending() {
        seeker.request(1)
        seeker.request(2)
        seeker.reset()
        seeker.frameRendered()
        assertEquals(listOf(1L), sent)
    }
}
