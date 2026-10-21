package br.com.riseedit.core

import kotlin.math.roundToInt

/** Honest export limits derived from the project's sources. */
object ExportPlan {
    /** Highest real frame rate among the main clips (0 when unknown). */
    fun sourceFps(project: Project): Float =
        project.clips.mapNotNull { it.sourceInfo?.frameRate?.takeIf { f -> f > 0f } }.maxOrNull()
            ?: 0f

    /** Frame-rate caps the user may pick: never above what any source actually has. */
    fun allowedFps(project: Project): List<Int> {
        val source = sourceFps(project)
        if (source <= 0f) return listOf(24, 25, 30)
        return ExportSettings.FPS.filter { it <= source.roundToInt() + 1 }.ifEmpty { listOf(24) }
    }

    /** Output frame rate: the cap, or the source if slower (frames are only dropped). */
    fun effectiveFps(project: Project): Int {
        val allowed = allowedFps(project)
        return if (project.export.fps in allowed) project.export.fps else allowed.max()
    }

    /** Real output rate: [effectiveFps], made fractional when the project is NTSC. */
    fun effectiveFrameRate(project: Project): Double =
        ExportSettings.frameRate(effectiveFps(project), project.export.ntsc)

    /** Suggested bitrate (bits/s) for a quality level 0..2 (Leve/Boa/Alta). */
    fun suggestedBitrate(height: Int, fps: Int, codec: VideoCodec, level: Int): Int {
        val base = when {
            height >= 2160 -> 40_000_000
            height >= 1440 -> 24_000_000
            height >= 1080 -> 12_000_000
            height >= 720 -> 8_000_000
            else -> 4_000_000
        }
        val motion = if (fps >= 50) 1.5 else 1.0
        val efficiency = if (codec == VideoCodec.HEVC) 0.6 else 1.0
        val quality = listOf(0.5, 1.0, 2.0)[level.coerceIn(0, 2)]
        return (base * motion * efficiency * quality).toInt()
            .coerceIn(ExportSettings.MIN_BITRATE, ExportSettings.MAX_BITRATE)
    }

    /** Taller output than every source only upscales; flagged so the UI can warn. */
    fun upscales(project: Project): Boolean {
        val tallest = project.clips.mapNotNull { c ->
            c.sourceInfo?.let { minOf(it.width, it.height).takeIf { s -> s > 0 } }
        }.maxOrNull() ?: return false
        return project.export.height > tallest
    }
}
