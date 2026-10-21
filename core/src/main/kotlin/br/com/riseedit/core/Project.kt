package br.com.riseedit.core

import java.util.UUID
import kotlin.math.roundToLong

enum class MotionGoal { SMOOTH, FIXED }

data class Stabilization(
    val enabled: Boolean = false,
    val goal: MotionGoal = MotionGoal.SMOOTH,
    val strength: Double = 0.75,
    val smoothSeconds: Double = 0.6,
    val maxZoom: Double = 1.10
) {
    init {
        require(strength in 0.0..1.0)
        require(smoothSeconds in 0.1..2.0)
        require(maxZoom in 1.0..1.25)
    }
}

data class Clip(
    val id: String = UUID.randomUUID().toString(),
    val uri: String,
    val name: String,
    val sourceDurationMs: Long,
    val startMs: Long = 0,
    val endMs: Long = sourceDurationMs,
    val stabilization: Stabilization = Stabilization(),
    val speedPoints: List<SpeedPoint> = emptyList(),
    val volume: Float = 1f,
    val fadeMs: Long = 0,
    val adjustments: ColorAdjust = ColorAdjust(),
    val keyframes: List<TransformKeyframe> = emptyList(),
    val sourceInfo: SourceInfo? = null,
    val proxyUri: String? = null,
    val proxyRequired: Boolean = false,
    /** When true, the clip's own audio is silent (volume is kept for Separar áudio undo). */
    val muted: Boolean = false,
    val orientation: Orientation = Orientation(),
    /** Per-parameter tracks; null means evaluate [keyframes] via legacy migration. */
    val tracks: br.com.riseedit.core.keys.TransformTracks? = null,
    val grade: br.com.riseedit.core.color.ColorGrade = br.com.riseedit.core.color.ColorGrade(),
    /** Color animation (source time); empty = static [adjustments]/[grade]. */
    val colorKeys: br.com.riseedit.core.color.ColorKeyframes =
        br.com.riseedit.core.color.ColorKeyframes(),
    /**
     * Masks (0.17), key times in SOURCE time. Main clip: the take color applies only inside.
     * Overlay clip ([VideoOverlay.clip]): the masks drive the overlay alpha.
     */
    val masks: br.com.riseedit.core.mask.MaskSet = br.com.riseedit.core.mask.MaskSet()
) {
    /**
     * Adjust + grade at [sourceMs] (keyframes applied, masks evaluated into [ColorAdjust.masks]);
     * identical for preview and export.
     */
    fun colorAt(sourceMs: Long): Pair<ColorAdjust, br.com.riseedit.core.color.ColorGrade> {
        val (a, g) = colorKeys.at(sourceMs, adjustments, grade)
        return (
            if (masks.isEmpty) {
                a.copy(
                    masks = emptyList()
                )
            } else {
                a.copy(masks = masks.at(sourceMs))
            }
            ) to
            g
    }

    init {
        require(
            sourceDurationMs > 0 && startMs >= 0 && endMs <= sourceDurationMs && endMs > startMs
        )
        require(volume in 0f..2f && fadeMs >= 0)
        require(speedPoints.zipWithNext().all { (a, b) -> a.sourceMs < b.sourceMs })
    }
    val sourceLengthMs get() = endMs - startMs
    fun effectiveUri(preferProxy: Boolean = false): String =
        proxyUri?.takeIf { (preferProxy || proxyRequired) && it.isNotBlank() } ?: uri
    fun speedAt(sourceMs: Long) = speedPoints.lastOrNull { it.sourceMs <= sourceMs }?.speed ?: 1.0
    private fun intervals() = (
        listOf(startMs) +
            speedPoints.map { it.sourceMs }.filter { it in (startMs + 1) until endMs } +
            endMs
        ).distinct().sorted()
    val durationMs get() = intervals().zipWithNext().sumOf { (a, b) ->
        (b - a) / speedAt(a)
    }.roundToLong().coerceAtLeast(1)
    fun sourceAt(outputMs: Long): Long {
        var remaining = outputMs.toDouble().coerceIn(0.0, durationMs.toDouble())
        for ((a, b) in intervals().zipWithNext()) {
            val duration = (b - a) / speedAt(a)
            if (remaining <=
                duration
            ) {
                return (a + remaining * speedAt(a)).roundToLong().coerceIn(startMs, endMs)
            }
            remaining -= duration
        }
        return endMs
    }
    fun trim(start: Long, end: Long) = copy(startMs = start, endMs = end)
    fun split(relativeMs: Long): List<Clip> {
        require(relativeMs in 1 until durationMs)
        val cut = sourceAt(relativeMs)
        require(cut in (startMs + 1) until endMs)
        return listOf(copy(endMs = cut), copy(id = UUID.randomUUID().toString(), startMs = cut))
    }
}

data class SpeedPoint(val sourceMs: Long, val speed: Double) {
    init {
        require(
            sourceMs >= 0 && speed in 0.25..4.0
        )
    }
}
data class TransformKeyframe(
    val sourceMs: Long,
    val x: Float = 0f,
    val y: Float = 0f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val opacity: Float = 1f
)
data class AudioClip(
    val id: String = UUID.randomUUID().toString(),
    val uri: String,
    val name: String,
    val sourceDurationMs: Long,
    val offsetMs: Long = 0,
    val startMs: Long = 0,
    val endMs: Long = sourceDurationMs,
    val track: Int = 0,
    val volume: Float = 0.7f,
    val fadeMs: Long = 500,
    val muted: Boolean = false,
    /** Main clip this audio was detached from; trims stay in sync when set. */
    val linkedClipId: String? = null,
    /** J-cut: linked audio starts this long before its clip. */
    val leadMs: Long = 0,
    /** L-cut: linked audio keeps playing this long after its clip. */
    val tailMs: Long = 0
) {
    init {
        require(
            offsetMs >= 0 &&
                track in 0..2 &&
                startMs >= 0 &&
                endMs > startMs &&
                endMs <= sourceDurationMs
        )
    }
    val durationMs get() = endMs - startMs
}

/** Audio layers that reach the mix (preview and export): not muted and starting inside the project. */
fun Project.audibleAudio(): List<AudioClip> = audio.filter { !it.muted && it.offsetMs < durationMs }
data class VideoOverlay(
    val id: String = UUID.randomUUID().toString(),
    val clip: Clip,
    val offsetMs: Long = 0,
    val track: Int = 0,
    val x: Float = 0.55f,
    val y: Float = 0.55f,
    val scale: Float = 0.35f,
    val opacity: Float = 1f,
    val rotation: Float = 0f,
    val keyframes: List<TransformKeyframe> = emptyList(),
    val blend: OverlayBlend = OverlayBlend.NORMAL,
    val mask: OverlayMask = OverlayMask.NONE,
    val maskFeather: Float = 0.2f,
    val maskInvert: Boolean = false,
    val tracks: br.com.riseedit.core.keys.TransformTracks? = null
) {
    init {
        require(offsetMs >= 0 && track in 0..1)
    }

    /** Transform at [localMs] since the overlay starts; keyframe times are overlay-local. */
    fun transformAt(localMs: Long) = TransformPath.at(
        keyframes,
        localMs,
        TransformKeyframe(localMs, x, y, scale, rotation, opacity),
        tracks = tracks
    )
}
data class TextLayer(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val startMs: Long = 0,
    val endMs: Long = 3000,
    val x: Float = 0f,
    val y: Float = -0.65f,
    val size: Float = 1f,
    val color: Int = -1,
    val animated: Boolean = true,
    val rotation: Float = 0f,
    val keyframes: List<TransformKeyframe> = emptyList(),
    val style: TextStyle = TextStyle(),
    val tracks: br.com.riseedit.core.keys.TransformTracks? = null
) {
    init {
        require(endMs > startMs && startMs >= 0)
    }

    /** Transform at [localMs] since the text starts; scale multiplies [size]. */
    fun transformAt(localMs: Long) = TransformPath.at(
        keyframes,
        localMs,
        TransformKeyframe(localMs, x, y, size, rotation, 1f),
        tracks = tracks
    )
}
enum class VideoCodec(val label: String) { H264("H.264"), HEVC("HEVC") }

/** Export target. 2160 = 4K; bitrate is free (1–150 Mbps); fps is a cap, never upsampled. */
data class ExportSettings(
    val height: Int = 720,
    val fps: Int = 30,
    val bitrate: Int = 8_000_000,
    val codec: VideoCodec = VideoCodec.H264
) {
    init {
        require(
            height in HEIGHTS &&
                fps in listOf(24, 30, 60) &&
                bitrate in MIN_BITRATE..MAX_BITRATE
        )
    }

    companion object {
        val HEIGHTS = listOf(480, 720, 1080, 1440, 2160)
        const val MIN_BITRATE = 1_000_000
        const val MAX_BITRATE = 150_000_000
    }
}

data class Marker(
    val id: String = UUID.randomUUID().toString(),
    val timeMs: Long,
    val label: String = ""
)
data class Project(
    val clips: List<Clip> = emptyList(),
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Novo projeto",
    val modifiedAt: Long = System.currentTimeMillis(),
    val favorite: Boolean = false,
    val audio: List<AudioClip> = emptyList(),
    val texts: List<TextLayer> = emptyList(),
    val overlays: List<VideoOverlay> = emptyList(),
    val aspectRatio: Float = 0f,
    val export: ExportSettings = ExportSettings(),
    val markers: List<Marker> = emptyList(),
    /** Camadas de ajuste (0.16), applied after composition; see [AdjustmentStack]. */
    val adjustmentLayers: List<AdjustmentLayer> = emptyList()
) {
    val durationMs get() = clips.sumOf { it.durationMs }
    fun replace(index: Int, clip: Clip) =
        copy(clips = clips.toMutableList().apply { set(index, clip) })

    /** Splits a clip; linked (separated) audio is split with it and each half stays linked. */
    fun split(index: Int, relativeMs: Long): Project {
        val original = clips[index]
        val parts = original.split(relativeMs)
        val newClips = clips.toMutableList().apply {
            removeAt(index)
            addAll(index, parts)
        }
        val newAudio = audio.flatMap { a ->
            if (a.linkedClipId != original.id) return@flatMap listOf(a)
            val cut = parts[1].startMs.coerceIn(a.startMs + 1, a.endMs - 1)
            listOf(
                a.copy(endMs = cut),
                a.copy(
                    id = UUID.randomUUID().toString(),
                    startMs = cut,
                    offsetMs = a.offsetMs + parts[0].durationMs,
                    linkedClipId = parts[1].id
                )
            )
        }
        return copy(clips = newClips, audio = newAudio)
    }
    fun move(index: Int, delta: Int): Project {
        val target = index + delta
        if (target !in clips.indices) return this
        return copy(clips = clips.toMutableList().apply { add(target, removeAt(index)) })
    }
}
