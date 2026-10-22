package br.com.riseedit.preview

/**
 * 0.19.3: live scrub without a seek backlog. At most one seek is in flight; requests that arrive
 * meanwhile only overwrite [latest], and when the frame lands (or [timeoutMs] passes, so a slow
 * decoder never stalls the drag) the newest position is sought next. Pure: clock and seek injected.
 */
class ScrubSeeker(
    private val seek: (Long) -> Unit,
    private val now: () -> Long,
    private val timeoutMs: Long = 400
) {
    var inFlight: Long? = null
        private set
    private var latest: Long? = null
    private var sentAt = 0L

    /** True while the picture on screen is older than the last requested position. */
    val waitingForFrame: Boolean get() = inFlight != null || latest != null

    fun request(positionMs: Long) {
        if (inFlight == null ||
            now() - sentAt >= timeoutMs
        ) {
            send(positionMs)
        } else {
            latest = positionMs
        }
    }

    /** A frame was rendered (or the player became ready) for the in-flight seek. */
    fun frameRendered() {
        inFlight = null
        latest?.let { send(it) }
    }

    /** Polled from the UI tick: unblocks a seek whose frame never arrived. */
    fun poll() {
        if (inFlight != null && now() - sentAt >= timeoutMs) {
            inFlight = null
            latest?.let { send(it) }
        }
    }

    fun reset() {
        inFlight = null
        latest = null
    }

    private fun send(positionMs: Long) {
        latest = null
        inFlight = positionMs
        sentAt = now()
        seek(positionMs)
    }
}
