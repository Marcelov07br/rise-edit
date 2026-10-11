package br.com.riseedit

import android.content.Context
import android.util.AtomicFile
import br.com.riseedit.core.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

class ProjectStore(context: Context) {
    private val root = context.filesDir
    private val projects = File(root, "projects").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)
    private fun projectFile(id: String): AtomicFile {
        require(id.matches(Regex("[a-zA-Z0-9-]{1,80}")))
        return AtomicFile(File(projects, "$id.json"))
    }
    fun list(): List<Project> {
        migrate()
        return projects.listFiles().orEmpty().filter { it.extension == "json" }.mapNotNull {
            runCatching { decode(JSONObject(AtomicFile(it).openRead().bufferedReader().use { r -> r.readText() })) }.getOrNull()
        }.sortedByDescending { it.modifiedAt }
    }
    private fun migrate() {
        val legacy = AtomicFile(File(root, "project.json"))
        if (legacy.baseFile.exists() && !prefs.getBoolean("migrated", false)) {
            val p = decode(JSONObject(legacy.openRead().bufferedReader().use { it.readText() })).copy(id = "legacy-project", name = "Meu primeiro projeto")
            if (!projectFile(p.id).baseFile.exists()) save(p)
            prefs.edit().putBoolean("migrated", true).apply()
        }
    }
    fun load(id: String): Project = decode(JSONObject(projectFile(id).openRead().bufferedReader().use { it.readText() }))
    fun load(): Project { migrate(); return prefs.getString("last", null)?.let { runCatching { load(it) }.getOrNull() } ?: Project() }
    fun save(project: Project) { write(projectFile(project.id), encode(project).toString()); prefs.edit().putString("last", project.id).apply() }
    fun delete(id: String) { projectFile(id).delete(); if (prefs.getString("last", null) == id) prefs.edit().remove("last").apply() }
    private fun clip(c: Clip): JSONObject = JSONObject().put("id", c.id).put("uri", c.uri).put("name", c.name)
        .put("duration", c.sourceDurationMs).put("start", c.startMs).put("end", c.endMs).put("volume", c.volume).put("fade", c.fadeMs)
        .put("speed", JSONArray(c.speedPoints.map { JSONArray(listOf(it.sourceMs, it.speed)) }))
        .put("adjust", JSONArray(listOf(c.adjustments.brightness, c.adjustments.contrast, c.adjustments.saturation, c.adjustments.warmth)))
        .put("keys", JSONArray(c.keyframes.map { JSONArray(listOf(it.sourceMs, it.x, it.y, it.scale, it.rotation, it.opacity)) }))
        .put("sourceInfo", c.sourceInfo?.let(SourceMetadataJson::encode) ?: JSONObject.NULL)
        .put("proxyUri", c.proxyUri ?: JSONObject.NULL).put("proxyRequired", c.proxyRequired)
        .put("stabilization", JSONObject().put("enabled", c.stabilization.enabled).put("goal", c.stabilization.goal.name)
            .put("strength", c.stabilization.strength).put("smooth", c.stabilization.smoothSeconds).put("zoom", c.stabilization.maxZoom))
    private fun readClip(c: JSONObject): Clip {
        val s = c.getJSONObject("stabilization"); val a = c.optJSONArray("adjust")
        return Clip(id = c.getString("id"), uri = c.getString("uri"), name = c.getString("name"), sourceDurationMs = c.getLong("duration"), startMs = c.getLong("start"), endMs = c.getLong("end"),
            stabilization = Stabilization(s.getBoolean("enabled"), MotionGoal.valueOf(s.getString("goal")), s.getDouble("strength"), s.getDouble("smooth"), s.getDouble("zoom")),
            volume = c.optDouble("volume", 1.0).toFloat(), fadeMs = c.optLong("fade"), speedPoints = rows(c.optJSONArray("speed")).map { SpeedPoint(it.getLong(0), it.getDouble(1)) },
            adjustments = if (a == null) Adjustments() else Adjustments(a.getDouble(0).toFloat(), a.getDouble(1).toFloat(), a.getDouble(2).toFloat(), a.getDouble(3).toFloat()),
            keyframes = rows(c.optJSONArray("keys")).map { TransformKeyframe(it.getLong(0), it.getDouble(1).toFloat(), it.getDouble(2).toFloat(), it.getDouble(3).toFloat(), it.getDouble(4).toFloat(), it.getDouble(5).toFloat()) },
            sourceInfo = c.optJSONObject("sourceInfo")?.let(SourceMetadataJson::decode),
            proxyUri = if (c.isNull("proxyUri")) null else c.optString("proxyUri").ifBlank { null },
            proxyRequired = c.optBoolean("proxyRequired"))
    }
    private fun encode(p: Project) = JSONObject().put("schema", 3).put("id", p.id).put("name", p.name).put("modified", p.modifiedAt).put("favorite", p.favorite)
        .put("clips", JSONArray(p.clips.map(::clip))).put("ratio", p.aspectRatio).put("export", JSONArray(listOf(p.export.height, p.export.fps, p.export.bitrate)))
        .put("audio", JSONArray(p.audio.map { a -> JSONObject().put("id", a.id).put("uri", a.uri).put("name", a.name).put("duration", a.sourceDurationMs)
            .put("offset", a.offsetMs).put("start", a.startMs).put("end", a.endMs).put("track", a.track).put("volume", a.volume).put("fade", a.fadeMs) }))
        .put("texts", JSONArray(p.texts.map { t -> JSONObject().put("id", t.id).put("text", t.text).put("start", t.startMs).put("end", t.endMs)
            .put("x", t.x).put("y", t.y).put("size", t.size).put("color", t.color).put("animated", t.animated) }))
        .put("overlays", JSONArray(p.overlays.map { o -> JSONObject().put("id", o.id).put("clip", clip(o.clip)).put("offset", o.offsetMs)
            .put("track", o.track).put("x", o.x).put("y", o.y).put("scale", o.scale).put("opacity", o.opacity) }))
    private fun decode(j: JSONObject): Project {
        require(j.getInt("schema") in 1..3) { "Formato de projeto não reconhecido" }
        val e = j.optJSONArray("export")
        return Project(clips = objects(j.getJSONArray("clips")).map(::readClip), id = j.optString("id", "legacy-project"), name = j.optString("name", "Meu projeto"),
            modifiedAt = j.optLong("modified", System.currentTimeMillis()), favorite = j.optBoolean("favorite"), aspectRatio = j.optDouble("ratio", 0.0).toFloat(),
            export = if (e == null) ExportSettings() else ExportSettings(e.getInt(0), e.getInt(1), e.getInt(2)),
            audio = objects(j.optJSONArray("audio")).map { a -> AudioClip(a.getString("id"), a.getString("uri"), a.getString("name"), a.getLong("duration"), a.getLong("offset"), a.getLong("start"), a.getLong("end"), a.getInt("track"), a.getDouble("volume").toFloat(), a.getLong("fade")) },
            texts = objects(j.optJSONArray("texts")).map { t -> TextLayer(t.getString("id"), t.getString("text"), t.getLong("start"), t.getLong("end"), t.getDouble("x").toFloat(), t.getDouble("y").toFloat(), t.getDouble("size").toFloat(), t.getInt("color"), t.getBoolean("animated")) },
            overlays = objects(j.optJSONArray("overlays")).map { o -> VideoOverlay(o.getString("id"), readClip(o.getJSONObject("clip")), o.getLong("offset"), o.getInt("track"), o.getDouble("x").toFloat(), o.getDouble("y").toFloat(), o.getDouble("scale").toFloat(), o.getDouble("opacity").toFloat()) })
    }
    private fun objects(a: JSONArray?) = (0 until (a?.length() ?: 0)).map { a!!.getJSONObject(it) }
    private fun rows(a: JSONArray?) = (0 until (a?.length() ?: 0)).map { a!!.getJSONArray(it) }
    private fun analysisFile(uri: String) = AtomicFile(File(root, "motion-v2-${MessageDigest.getInstance("SHA-256").digest(uri.toByteArray()).joinToString("") { "%02x".format(it) }}.json"))
    fun loadAnalysis(uri: String): MotionAnalysis? = runCatching {
        val file = analysisFile(uri); if (!file.baseFile.exists()) return null
        val j = JSONObject(file.openRead().bufferedReader().use { it.readText() })
        MotionAnalysis(j.getInt("width"), j.getInt("height"), rows(j.getJSONArray("samples")).map { MotionSample(it.getLong(0), Pose(it.getDouble(1), it.getDouble(2), it.getDouble(3)), it.getInt(4)) }, j.getInt("unreliable"))
    }.getOrNull()
    fun saveAnalysis(uri: String, a: MotionAnalysis) = write(analysisFile(uri), JSONObject().put("width", a.width).put("height", a.height).put("unreliable", a.unreliableFrames)
        .put("samples", JSONArray(a.samples.map { JSONArray(listOf(it.timeMs, it.pose.x, it.pose.y, it.pose.angle, it.segment)) })).toString())
    private fun write(file: AtomicFile, text: String) {
        val stream = file.startWrite()
        try { stream.write(text.toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
        catch (e: Exception) { file.failWrite(stream); throw e }
    }
}
