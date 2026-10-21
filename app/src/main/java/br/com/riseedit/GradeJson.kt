package br.com.riseedit

import br.com.riseedit.core.color.ColorGrade
import br.com.riseedit.core.color.LogProfile
import br.com.riseedit.core.color.ToneCurve
import org.json.JSONObject

internal object GradeJson {
    fun encode(g: ColorGrade): JSONObject = JSONObject()
        .put("log", g.log.name)
        .put("curve", g.curve.name)
        .put("lut", g.lutUri ?: JSONObject.NULL)
        .put("lutName", g.lutName ?: JSONObject.NULL)
        .put("lutAmount", g.lutIntensity.toDouble())
        .put("lutFirst", g.lutBeforeAdjust)

    /** JSONObject/JSONArray → Map/List, for core decoders. */
    fun plain(o: JSONObject): Map<String, Any?> = o.keys().asSequence().associateWith { plainValue(o.opt(it)) }
    private fun plainValue(v: Any?): Any? = when (v) {
        is JSONObject -> plain(v)
        is org.json.JSONArray -> List(v.length()) { plainValue(v.opt(it)) }
        JSONObject.NULL -> null
        else -> v
    }

    fun decode(o: JSONObject?): ColorGrade {
        if (o == null) return ColorGrade()
        return ColorGrade(
            log = runCatching {
                LogProfile.valueOf(o.optString("log"))
            }.getOrDefault(LogProfile.NONE),
            curve = runCatching {
                ToneCurve.valueOf(o.optString("curve"))
            }.getOrDefault(ToneCurve.LINEAR),
            lutUri = if (o.isNull("lut")) null else o.optString("lut").ifBlank { null },
            lutName = if (o.isNull("lutName")) null else o.optString("lutName").ifBlank { null },
            lutIntensity = o.optDouble("lutAmount", 1.0).toFloat().coerceIn(0f, 1f),
            lutBeforeAdjust = o.optBoolean("lutFirst", false)
        )
    }
}
