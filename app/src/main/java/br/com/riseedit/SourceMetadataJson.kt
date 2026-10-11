package br.com.riseedit

import br.com.riseedit.core.*
import org.json.JSONObject

internal object SourceMetadataJson {
    fun encode(info: SourceInfo): JSONObject = JSONObject()
        .put("mime", info.mime).put("width", info.width).put("height", info.height)
        .put("profile", info.profile).put("level", info.level).put("depth", info.bitDepth.name)
        .put("chroma", info.chroma.name).put("hdr", info.isHdr).put("fps", info.frameRate).put("rotation", info.rotationDegrees)
        .put("audioMime", info.audioMime ?: JSONObject.NULL).put("channels", info.audioChannels).put("sampleRate", info.audioSampleRate)

    fun decode(j: JSONObject): SourceInfo = SourceInfo(
        mime = j.getString("mime"), width = j.optInt("width"), height = j.optInt("height"),
        profile = j.optInt("profile"), level = j.optInt("level"),
        bitDepth = ColorDepth.entries.firstOrNull { it.name == j.optString("depth") } ?: ColorDepth.UNKNOWN,
        chroma = ChromaFormat.entries.firstOrNull { it.name == j.optString("chroma") } ?: ChromaFormat.UNKNOWN,
        isHdr = j.optBoolean("hdr"), frameRate = j.optDouble("fps", 0.0).toFloat(),
        audioMime = if (j.isNull("audioMime")) null else j.optString("audioMime").ifBlank { null },
        audioChannels = j.optInt("channels"), audioSampleRate = j.optInt("sampleRate"), rotationDegrees = j.optInt("rotation")
    )
}
