package br.com.riseedit

import br.com.riseedit.core.OverlayBlend
import br.com.riseedit.core.OverlayMask
import br.com.riseedit.core.TextStyle
import br.com.riseedit.core.VideoOverlay
import org.json.JSONObject

/** Schema 5: text styles and overlay looks. Missing objects (older files) load defaults. */
internal object StyleJson {
    fun text(s: TextStyle): JSONObject = JSONObject()
        .put("font", s.font).put("bold", s.bold).put("italic", s.italic).put("align", s.align.name)
        .put("letter", s.letterSpacing.toDouble()).put("line", s.lineHeight.toDouble())
        .put("opacity", s.opacity.toDouble())
        .put("strokeColor", s.strokeColor).put("stroke", s.strokeWidth.toDouble())
        .put("glowColor", s.glowColor).put("glow", s.glow.toDouble())
        .put("bgColor", s.backgroundColor).put("bg", s.background.toDouble())
        .put("bgRadius", s.backgroundRadius.toDouble())
        .put("shadowColor", s.shadowColor).put("shadow", s.shadow.toDouble())
        .put(
            "shadowBlur",
            s.shadowBlur.toDouble()
        ).put("shadowDistance", s.shadowDistance.toDouble())
        .put("in", s.animationIn.name).put("out", s.animationOut.name).put("animMs", s.animationMs)
        .put("word", s.wordAnimation.name).put("unit", s.wordUnit.name).put("wordMs", s.wordMs)
        .put("grad", s.gradientColor).put("wordBox", s.wordBox)
        .put("flow", s.flow.name).put("font2", s.secondaryFont).put("wght", s.weight).put("wght2", s.secondaryWeight)
        .put("caps", s.caps.name).put("maxWords", s.maxWords).put("blurScale", s.blurScale.toDouble())

    fun readText(j: JSONObject?, animated: Boolean): TextStyle {
        if (j == null) return TextStyle.legacy(animated)
        val d = TextStyle()
        fun f(name: String, default: Float) = j.optDouble(name, default.toDouble()).toFloat()
        return TextStyle(
            font = j.optString("font", d.font),
            bold = j.optBoolean("bold", d.bold),
            italic = j.optBoolean("italic", d.italic),
            align = enumOr(j.optString("align"), d.align),
            letterSpacing = f("letter", d.letterSpacing),
            lineHeight = f("line", d.lineHeight),
            opacity = f("opacity", d.opacity),
            strokeColor = j.optInt("strokeColor", d.strokeColor),
            strokeWidth = f("stroke", d.strokeWidth),
            glowColor = j.optInt("glowColor", d.glowColor),
            glow = f("glow", d.glow),
            backgroundColor = j.optInt("bgColor", d.backgroundColor),
            background = f("bg", d.background),
            backgroundRadius = f("bgRadius", d.backgroundRadius),
            shadowColor = j.optInt("shadowColor", d.shadowColor),
            shadow = f("shadow", d.shadow),
            shadowBlur = f("shadowBlur", d.shadowBlur),
            shadowDistance = f("shadowDistance", d.shadowDistance),
            animationIn = enumOr(j.optString("in"), d.animationIn),
            animationOut = enumOr(j.optString("out"), d.animationOut),
            animationMs = j.optLong("animMs", d.animationMs),
            wordAnimation = enumOr(j.optString("word"), d.wordAnimation),
            wordUnit = enumOr(j.optString("unit"), d.wordUnit),
            wordMs = j.optLong("wordMs", d.wordMs).coerceIn(40, 2000),
            gradientColor = j.optInt("grad", d.gradientColor),
            wordBox = j.optBoolean("wordBox", d.wordBox),
            flow = enumOr(j.optString("flow"), d.flow),
            secondaryFont = j.optString("font2", d.secondaryFont),
            weight = j.optInt("wght", d.weight),
            secondaryWeight = j.optInt("wght2", d.secondaryWeight),
            caps = enumOr(j.optString("caps"), d.caps),
            maxWords = j.optInt("maxWords", d.maxWords).coerceIn(0, 12),
            blurScale = f("blurScale", d.blurScale).coerceIn(0f, 2f)
        )
    }

    fun look(o: VideoOverlay): JSONObject = JSONObject()
        .put("blend", o.blend.name).put("mask", o.mask.name)
        .put("feather", o.maskFeather.toDouble()).put("invert", o.maskInvert)

    fun withLook(o: VideoOverlay, j: JSONObject?): VideoOverlay = if (j == null) {
        o
    } else {
        o.copy(
            blend = enumOr(j.optString("blend"), OverlayBlend.NORMAL),
            mask = enumOr(j.optString("mask"), OverlayMask.NONE),
            maskFeather = j.optDouble("feather", 0.2).toFloat(),
            maskInvert = j.optBoolean("invert", false)
        )
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default
}
