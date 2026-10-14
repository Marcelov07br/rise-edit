package br.com.riseedit.timeline

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import br.com.riseedit.core.Clip
import br.com.riseedit.core.editing.ClipTrimSession
import br.com.riseedit.core.editing.TimelineViewport

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
    var trim by mutableStateOf<ClipTrimSession?>(null)
    var draft by mutableStateOf<Clip?>(null)
    var movingId by mutableStateOf<String?>(null)
    var movingUs by mutableStateOf(0.0)
    var snapping by mutableStateOf(true)

    /** Logical rows currently shown, top to bottom (see [trackNames]). */
    var lanes by mutableStateOf(BASE_LANES)

    /** Extra lanes the user opened with "+ Faixa" before putting anything on them. */
    var extraLanes by mutableStateOf(emptySet<Int>())

    fun clearGesture() {
        trim = null
        draft = null
        movingId = null
        movingUs = 0.0
    }
}
