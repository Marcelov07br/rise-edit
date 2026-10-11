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
    private val file = AtomicFile(File(root, "project.json"))
    fun load(): Project {
        if (!file.baseFile.exists()) return Project()
        val json = JSONObject(file.openRead().bufferedReader().use { it.readText() })
        require(json.getInt("schema") == 1) { "Formato de projeto não reconhecido" }
        val clips = json.getJSONArray("clips")
        return Project((0 until clips.length()).map { index ->
            val c = clips.getJSONObject(index); val s = c.getJSONObject("stabilization")
            Clip(c.getString("id"), c.getString("uri"), c.getString("name"), c.getLong("duration"), c.getLong("start"), c.getLong("end"),
                Stabilization(s.getBoolean("enabled"), MotionGoal.valueOf(s.getString("goal")), s.getDouble("strength"), s.getDouble("smooth"), s.getDouble("zoom")))
        })
    }
    fun save(project: Project) {
        val clips = JSONArray()
        project.clips.forEach { c ->
            val s = c.stabilization
            clips.put(JSONObject().put("id", c.id).put("uri", c.uri).put("name", c.name).put("duration", c.sourceDurationMs)
                .put("start", c.startMs).put("end", c.endMs).put("stabilization", JSONObject().put("enabled", s.enabled)
                    .put("goal", s.goal.name).put("strength", s.strength).put("smooth", s.smoothSeconds).put("zoom", s.maxZoom)))
        }
        write(file, JSONObject().put("schema", 1).put("clips", clips).toString())
    }
    private fun analysisFile(uri: String) = AtomicFile(File(root, "motion-v1-${MessageDigest.getInstance("SHA-256").digest(uri.toByteArray()).joinToString("") { "%02x".format(it) }}.json"))
    fun loadAnalysis(uri: String): MotionAnalysis? = runCatching {
        val file = analysisFile(uri)
        if (!file.baseFile.exists()) return null
        val json = JSONObject(file.openRead().bufferedReader().use { it.readText() })
        val samples = json.getJSONArray("samples")
        MotionAnalysis(json.getInt("width"), json.getInt("height"), (0 until samples.length()).map {
            val s = samples.getJSONArray(it)
            MotionSample(s.getLong(0), Pose(s.getDouble(1), s.getDouble(2), s.getDouble(3)), s.getInt(4))
        }, json.getInt("unreliable"))
    }.getOrNull()
    fun saveAnalysis(uri: String, analysis: MotionAnalysis) {
        val samples = JSONArray()
        analysis.samples.forEach { samples.put(JSONArray(listOf(it.timeMs, it.pose.x, it.pose.y, it.pose.angle, it.segment))) }
        write(analysisFile(uri), JSONObject().put("width", analysis.width).put("height", analysis.height)
            .put("unreliable", analysis.unreliableFrames).put("samples", samples).toString())
    }
    private fun write(file: AtomicFile, text: String) {
        val stream = file.startWrite()
        try { stream.write(text.toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
        catch (e: Exception) { file.failWrite(stream); throw e }
    }
}
