package br.com.riseedit.core

/** Static framing of a video clip: 90° steps, flips, Fit vs Fill. Independent of keyframed rotation. */
enum class ScaleMode {
    /** Letterbox / pillarbox so the whole frame is visible. */
    FIT,

    /** Crop to cover the output frame (CapCut "Preencher"). */
    FILL
}

data class Orientation(
    /** Clockwise quarter-turns (0..3). */
    val turns: Int = 0,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val scaleMode: ScaleMode = ScaleMode.FILL
) {
    init {
        require(turns in 0..3)
    }

    val rotationDegrees: Float get() = turns * 90f

    fun rotate90() = copy(turns = (turns + 1) % 4)

    fun flipH() = copy(flipHorizontal = !flipHorizontal)

    fun flipV() = copy(flipVertical = !flipVertical)

    fun cycleScale() = copy(
        scaleMode = if (scaleMode == ScaleMode.FILL) ScaleMode.FIT else ScaleMode.FILL
    )
}
