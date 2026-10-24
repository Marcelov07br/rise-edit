package br.com.riseedit.settings

import br.com.riseedit.Accent
import br.com.riseedit.core.Aspect
import br.com.riseedit.core.ExportPlan
import br.com.riseedit.core.ExportSettings
import br.com.riseedit.core.FrameRateChoice
import br.com.riseedit.core.ProjectFormat
import br.com.riseedit.core.Resolution
import br.com.riseedit.core.TvStandard
import br.com.riseedit.core.VideoCodec
import br.com.riseedit.preview.PreviewQuality

/** Timeline thumbnail density: target tile width in dp. */
enum class ThumbDensity(val label: String, val tileDp: Double) {
    LOW("Poucas", 72.0),
    NORMAL("Normal", 40.0),
    HIGH("Muitas", 26.0)
}

/** Bitrate preset fed to [ExportPlan.suggestedBitrate] (0 Leve, 1 Boa, 2 Alta). */
enum class Quality(val label: String) { LIGHT("Leve"), GOOD("Boa"), HIGH("Alta") }

/** 0.19: app preferences (DataStore). Only settings with a real effect live here. */
data class AppSettings(
    val accent: Accent = Accent.WHITE,
    val haptics: Boolean = true,
    val reduceMotion: Boolean = false,
    val aspect: Aspect = Aspect.WIDE,
    val resolution: Resolution = Resolution.FHD,
    val standard: TvStandard = TvStandard.NTSC,
    val rate: FrameRateChoice = FrameRateChoice(30, false),
    val codec: VideoCodec = VideoCodec.H264,
    val quality: Quality = Quality.GOOD,
    /** SAF tree URI; null = ask with the system "Salvar como" every export. */
    val exportFolder: String? = null,
    val previewQuality: PreviewQuality = PreviewQuality.Faithful,
    val autoProxy4k: Boolean = true,
    /** 0.24.0: preview proxies for heavy media. */
    val proxyResolution: br.com.riseedit.core.ProxyResolution = br.com.riseedit.core.ProxyResolution.P720,
    val proxyCodec: br.com.riseedit.core.ProxyCodec = br.com.riseedit.core.ProxyCodec.H264,
    val snap: Boolean = true,
    val ripple: Boolean = false,
    val thumbs: ThumbDensity = ThumbDensity.NORMAL,
    val imageDurationMs: Long = 3000
) {
    /** Export block of a new project. */
    fun exportDefaults() = ExportSettings(
        height = resolution.height,
        fps = rate.fps,
        ntsc = rate.ntsc,
        codec = codec,
        bitrate = ExportPlan.suggestedBitrate(resolution.height, rate.fps, codec, quality.ordinal)
    )

    fun toMap(): Map<String, String> = buildMap {
        put("accent", accent.name)
        put("haptics", haptics.toString())
        put("reduceMotion", reduceMotion.toString())
        put("aspect", aspect.name)
        put("resolution", resolution.name)
        put("standard", standard.name)
        put("fps", rate.fps.toString())
        put("ntsc", rate.ntsc.toString())
        put("codec", codec.name)
        put("quality", quality.name)
        exportFolder?.let { put("exportFolder", it) }
        put("preview", previewQuality.name)
        put("autoProxy4k", autoProxy4k.toString())
        put("proxyRes", proxyResolution.name)
        put("proxyCodec", proxyCodec.name)
        put("snap", snap.toString())
        put("ripple", ripple.toString())
        put("thumbs", thumbs.name)
        put("imageMs", imageDurationMs.toString())
    }

    companion object {
        val IMAGE_DURATIONS = listOf(1000L, 2000L, 3000L, 5000L, 8000L)

        private inline fun <reified E : Enum<E>> Map<String, String>.enum(key: String, def: E): E =
            get(key)?.let { v -> enumValues<E>().firstOrNull { it.name == v } } ?: def

        private fun Map<String, String>.bool(key: String, def: Boolean) =
            get(key)?.toBooleanStrictOrNull() ?: def

        /** Unknown/invalid values fall back to defaults (never crash on old or edited stores). */
        fun fromMap(m: Map<String, String>): AppSettings {
            val d = AppSettings()
            val standard = m.enum("standard", d.standard)
            val rate =
                FrameRateChoice(m["fps"]?.toIntOrNull() ?: d.rate.fps, m.bool("ntsc", d.rate.ntsc))
            return AppSettings(
                accent = m.enum("accent", d.accent),
                haptics = m.bool("haptics", d.haptics),
                reduceMotion = m.bool("reduceMotion", d.reduceMotion),
                aspect = m.enum("aspect", d.aspect),
                resolution = m.enum("resolution", d.resolution),
                standard = standard,
                rate = ProjectFormat.coerce(rate, standard),
                codec = m.enum("codec", d.codec),
                quality = m.enum("quality", d.quality),
                exportFolder = m["exportFolder"]?.takeIf { it.isNotBlank() },
                previewQuality = m.enum("preview", d.previewQuality),
                autoProxy4k = m.bool("autoProxy4k", d.autoProxy4k),
                proxyResolution = m.enum("proxyRes", d.proxyResolution),
                proxyCodec = m.enum("proxyCodec", d.proxyCodec),
                snap = m.bool("snap", d.snap),
                ripple = m.bool("ripple", d.ripple),
                thumbs = m.enum("thumbs", d.thumbs),
                imageDurationMs =
                m["imageMs"]?.toLongOrNull()?.takeIf { it in IMAGE_DURATIONS }
                    ?: d.imageDurationMs
            )
        }
    }
}
