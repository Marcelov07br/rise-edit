package br.com.riseedit.timeline

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import br.com.riseedit.core.AudioClip
import br.com.riseedit.core.Clip
import br.com.riseedit.core.VideoOverlay
import br.com.riseedit.core.editing.AudioTrimSession
import br.com.riseedit.core.editing.ClipTrimSession
import br.com.riseedit.core.editing.OverlayTrimSession
import br.com.riseedit.core.editing.Snapping
import br.com.riseedit.core.editing.TimelineViewport
import br.com.riseedit.core.editing.TrimEdge

/** Active edge-trim draft for main, overlay or audio. */
internal sealed class TrimDraft {
    abstract val edge: TrimEdge
    abstract val entryId: String

    data class Main(val session: ClipTrimSession, val draft: Clip) : TrimDraft() {
        override val edge get() = session.edge
        override val entryId get() = session.original.id
    }

    data class Overlay(val session: OverlayTrimSession, val draft: VideoOverlay) : TrimDraft() {
        override val edge get() = session.edge
        override val entryId get() = session.original.id
    }

    data class Audio(val session: AudioTrimSession, val draft: AudioClip) : TrimDraft() {
        override val edge get() = session.edge
        override val entryId get() = session.original.id
    }

    /** Camada de ajuste: edge drag in µs → [AdjustmentLayer.trimmed]. */
    data class Adjust(
        val original: br.com.riseedit.core.AdjustmentLayer,
        override val edge: TrimEdge,
        val draft: br.com.riseedit.core.AdjustmentLayer
    ) : TrimDraft() {
        override val entryId get() = original.id
        fun dragged(deltaUs: Double) = copy(draft = original.trimmed(edge, (deltaUs / 1000).toLong()))
    }
}

internal class TimelineUiState(density: Float) {
    var viewport by mutableStateOf(
        TimelineViewport(
            55.0 * density,
            -24.0 / 55 * 1_000_000,
            1.0,
            20.0 * density,
            4000.0 * density,
            24.0 * density
        )
    )
    var trimDraft by mutableStateOf<TrimDraft?>(null)

    /** Legacy alias used by main-clip duration hint. */
    val draft: Clip? get() = (trimDraft as? TrimDraft.Main)?.draft
    val trim: ClipTrimSession? get() = (trimDraft as? TrimDraft.Main)?.session

    var movingId by mutableStateOf<String?>(null)
    var movingUs by mutableStateOf(0.0)
    var snapping by mutableStateOf(true)

    /** Time the current drag is magnetized to, drawn as a guide line; null when free. */
    var snapUs by mutableStateOf<Double?>(null)

    /** Light haptic tick fired when a drag engages a new magnet. */
    var onSnap: () -> Unit = {}

    /** Opened when the user taps a keyframe diamond (low panel, not a popup). */
    var keyframePanel by mutableStateOf<KeyframeTarget?>(null)

    /** Snaps [valueUs] (or a span when [lengthUs] > 0) to [targets]; returns the snapped start. */
    fun magnet(
        valueUs: Double,
        targets: List<Double>,
        thresholdUs: Double,
        lengthUs: Double = 0.0
    ): Double {
        if (!snapping) {
            snapUs = null
            return valueUs
        }
        val result = if (lengthUs > 0) {
            Snapping.snapSpan(valueUs, lengthUs, targets, thresholdUs)
        } else {
            Snapping.snap(valueUs, targets, thresholdUs)
        }
        if (result.target != null && result.target != snapUs) onSnap()
        snapUs = result.target
        return result.value
    }

    /** Logical rows currently shown, top to bottom (see [trackNames]). */
    var lanes by mutableStateOf(BASE_LANES)

    /** Extra lanes the user opened with "+ Faixa" before putting anything on them. */
    var extraLanes by mutableStateOf(emptySet<Int>())

    fun clearGesture() {
        trimDraft = null
        movingId = null
        movingUs = 0.0
        snapUs = null
    }
}

/** Target of the keyframe mini panel. */
internal data class KeyframeTarget(
    val entryId: String,
    /** Source or local time of the key, matching [TimelineEntry.keyOffsetsUs] domain. */
    val timeMs: Long
)
