package br.com.riseedit.core

/** 0.19: "Criar projeto" choices and defaults (also the Settings "Projeto padrão" section). */
enum class Aspect(val label: String, val ratio: Float) {
    WIDE("16:9", 16f / 9f),
    VERTICAL("9:16", 9f / 16f),
    SQUARE("1:1", 1f),
    PORTRAIT("4:5", 4f / 5f),
    CLASSIC("4:3", 4f / 3f),
    CINEMA("21:9", 21f / 9f),
    SOURCE("Original", 0f);

    companion object {
        fun of(ratio: Float) =
            entries.firstOrNull { kotlin.math.abs(it.ratio - ratio) < 0.01f } ?: SOURCE
    }
}

enum class Resolution(val label: String, val height: Int) {
    HD("720p", 720),
    FHD("1080p", 1080),
    QHD("1440p", 1440),
    UHD("4K", 2160);

    companion object {
        fun of(height: Int) = entries.firstOrNull { it.height == height } ?: FHD
    }
}

enum class TvStandard(val label: String) { NTSC("NTSC"), PAL("PAL") }

/** One frame-rate choice: [fps] nominal + [ntsc] fractional flag. */
data class FrameRateChoice(val fps: Int, val ntsc: Boolean) {
    val rate get() = ExportSettings.frameRate(fps, ntsc)
    val label: String get() = when {
        ntsc && fps == 24 -> "23,976"
        ntsc && fps == 30 -> "29,97"
        ntsc && fps == 60 -> "59,94"
        else -> "$fps"
    } + " fps"
}

object ProjectFormat {
    /** The fps dropdown follows the standard (integers 24/30/60 kept on NTSC too). */
    fun rates(standard: TvStandard): List<FrameRateChoice> = when (standard) {
        TvStandard.NTSC -> listOf(
            FrameRateChoice(24, true),
            FrameRateChoice(24, false),
            FrameRateChoice(30, true),
            FrameRateChoice(30, false),
            FrameRateChoice(60, true),
            FrameRateChoice(60, false)
        )
        TvStandard.PAL -> listOf(
            FrameRateChoice(25, false),
            FrameRateChoice(50, false),
            FrameRateChoice(24, false)
        )
    }

    /** Keeps the chosen rate when valid for [standard], else the standard's usual one. */
    fun coerce(choice: FrameRateChoice, standard: TvStandard): FrameRateChoice =
        choice.takeIf { it in rates(standard) }
            ?: rates(standard)[if (standard == TvStandard.NTSC) 2 else 0]

    fun standardOf(fps: Int, ntsc: Boolean) = if (!ntsc &&
        fps in listOf(25, 50)
    ) {
        TvStandard.PAL
    } else {
        TvStandard.NTSC
    }

    fun newProject(
        name: String,
        aspect: Aspect,
        resolution: Resolution,
        rate: FrameRateChoice,
        base: ExportSettings = ExportSettings()
    ) = Project(
        name = name.trim().ifBlank { "Novo projeto" },
        aspectRatio = aspect.ratio,
        export = base.copy(
            height = resolution.height,
            fps = rate.fps,
            ntsc = rate.ntsc,
            bitrate = ExportPlan.suggestedBitrate(resolution.height, rate.fps, base.codec, 1)
        )
    )
}
