package br.com.riseedit.core.timeline

/** Media identity is independent of trims, speed and timeline placement. */
sealed interface SegmentSource {
    data class Media(val uri: String, val originalDurationUs: Long) : SegmentSource {
        init {
            require(uri.isNotBlank()) { "Media URI must not be blank" }
            require(originalDurationUs > 0) { "Original duration must be positive" }
        }
    }

    data class Still(val uri: String) : SegmentSource {
        init {
            require(uri.isNotBlank()) { "Still URI must not be blank" }
        }
    }

    data object Gap : SegmentSource
}
