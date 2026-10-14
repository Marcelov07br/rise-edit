package br.com.riseedit

import br.com.riseedit.core.keys.Easing
import br.com.riseedit.core.keys.Key
import br.com.riseedit.core.keys.ParameterTrack
import br.com.riseedit.core.keys.TimeDomain
import br.com.riseedit.core.keys.TrackValue
import br.com.riseedit.core.keys.TransformTracks
import org.json.JSONArray
import org.json.JSONObject

/** Schema 7+ per-parameter keyframe tracks. */
internal object TracksJson {
    fun encode(tracks: TransformTracks?): Any = if (tracks == null) {
        JSONObject.NULL
    } else {
        JSONObject()
            .put("domain", tracks.x.domain.name)
            .put("x", row(tracks.x))
            .put("y", row(tracks.y))
            .put("scale", row(tracks.scale))
            .put("rotation", row(tracks.rotation))
            .put("opacity", row(tracks.opacity))
    }

    fun decode(obj: JSONObject?): TransformTracks? {
        if (obj == null) return null
        val domain = TimeDomain.valueOf(obj.optString("domain", TimeDomain.LOCAL_OUTPUT.name))
        return TransformTracks(
            track(domain, obj.optJSONArray("x"), TrackValue.Scalar),
            track(domain, obj.optJSONArray("y"), TrackValue.Scalar),
            track(domain, obj.optJSONArray("scale"), TrackValue.Scalar),
            track(domain, obj.optJSONArray("rotation"), TrackValue.Angle),
            track(domain, obj.optJSONArray("opacity"), TrackValue.Scalar)
        )
    }

    private fun row(track: ParameterTrack<Float>) = JSONArray(
        track.keys.map { key ->
            JSONArray().put(key.timeMs).put(key.value.toDouble()).put(easing(key.easing))
        }
    )

    private fun track(
        domain: TimeDomain,
        array: JSONArray?,
        values: TrackValue<Float>
    ): ParameterTrack<Float> {
        val keys = (0 until (array?.length() ?: 0)).map { i ->
            val row = array!!.getJSONArray(i)
            Key(row.getLong(0), row.getDouble(1).toFloat(), parseEasing(row.opt(2)))
        }
        return ParameterTrack(domain, keys, values)
    }

    private fun easing(e: Easing): Any = when (e) {
        Easing.Hold -> "Hold"
        Easing.Linear -> "Linear"
        Easing.Smooth -> "Smooth"
        is Easing.CubicBezier -> JSONArray(listOf("CubicBezier", e.x1, e.y1, e.x2, e.y2))
    }

    private fun parseEasing(raw: Any?): Easing = when (raw) {
        null, JSONObject.NULL -> Easing.Smooth
        is String -> when (raw) {
            "Hold" -> Easing.Hold
            "Linear" -> Easing.Linear
            "Smooth" -> Easing.Smooth
            else -> Easing.Smooth
        }
        is JSONArray -> if (raw.length() >= 5 && raw.getString(0) == "CubicBezier") {
            Easing.CubicBezier(
                raw.getDouble(1),
                raw.getDouble(2),
                raw.getDouble(3),
                raw.getDouble(4)
            )
        } else {
            Easing.Smooth
        }
        else -> Easing.Smooth
    }
}
