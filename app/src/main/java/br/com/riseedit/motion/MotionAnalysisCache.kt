package br.com.riseedit.motion

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import br.com.riseedit.core.MotionAnalysis
import br.com.riseedit.core.MotionSample
import br.com.riseedit.core.Pose
import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/** On-disk analysis cache keyed by source uri + size + modification time + algorithm version. */
class MotionAnalysisCache(private val context: Context) {
    private val root = File(context.filesDir, "motion-cache").apply { mkdirs() }

    fun load(uri: String): MotionAnalysis? = runCatching {
        val file = file(uri)
        if (!file.baseFile.exists()) return null
        decode(JSONObject(file.openRead().bufferedReader().use { it.readText() }))
    }.getOrNull()

    fun save(uri: String, analysis: MotionAnalysis) {
        val file = file(uri)
        val stream = file.startWrite()
        try {
            stream.write(encode(analysis).toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (e: java.io.IOException) {
            file.failWrite(stream)
            throw e
        }
    }

    private fun file(uri: String) = AtomicFile(File(root, "${sha256(key(uri))}.json"))

    private fun key(uri: String): String {
        val parsed = Uri.parse(uri)
        val (size, modified) =
            if (parsed.scheme == "file") {
                val f = File(requireNotNull(parsed.path))
                f.length() to f.lastModified()
            } else {
                runCatching { contentStat(parsed) }.getOrDefault(-1L to -1L)
            }
        return "$VERSION|$uri|$size|$modified"
    }

    private fun contentStat(uri: Uri): Pair<Long, Long> =
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@use -1L to -1L
            fun long(vararg names: String) = names.map {
                c.getColumnIndex(it)
            }.firstOrNull { it >= 0 && !c.isNull(it) }?.let { c.getLong(it) }
                ?: -1L
            long("_size") to long("last_modified", "date_modified")
        } ?: (-1L to -1L)

    private fun sha256(text: String) =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") {
            "%02x".format(it)
        }

    companion object {
        private const val VERSION = "motion-v3"

        fun encode(a: MotionAnalysis): JSONObject = JSONObject().put(
            "width",
            a.width
        ).put("height", a.height).put("unreliable", a.unreliableFrames)
            .put(
                "samples",
                JSONArray(
                    a.samples.map {
                        JSONArray(
                            listOf(it.timeMs, it.pose.x, it.pose.y, it.pose.angle, it.segment)
                        )
                    }
                )
            )

        fun decode(j: JSONObject): MotionAnalysis {
            val rows = j.getJSONArray("samples")
            val samples =
                (0 until rows.length()).map {
                    val r = rows.getJSONArray(it)
                    MotionSample(
                        r.getLong(0),
                        Pose(r.getDouble(1), r.getDouble(2), r.getDouble(3)),
                        r.getInt(4)
                    )
                }
            return MotionAnalysis(
                j.getInt("width"),
                j.getInt("height"),
                samples,
                j.getInt("unreliable")
            )
        }
    }
}
