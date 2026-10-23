package br.com.riseedit

import android.content.Context
import android.util.AtomicFile
import br.com.riseedit.core.library.LibraryFolder
import br.com.riseedit.core.library.ProjectLibraries
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** 0.22.1: bibliotecas persisted next to the projects (filesDir/libraries.json, v1). */
class LibraryStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "libraries.json"))

    fun load(): ProjectLibraries = runCatching {
        if (!file.baseFile.exists()) return ProjectLibraries()
        val j = JSONObject(file.openRead().bufferedReader().use { it.readText() })
        val f = j.optJSONArray("folders") ?: JSONArray()
        val a = j.optJSONObject("assign") ?: JSONObject()
        ProjectLibraries.decode(
            mapOf(
                "folders" to (0 until f.length()).map { i ->
                    f.getJSONObject(i).let { mapOf("id" to it.optString("id"), "name" to it.optString("name")) }
                },
                "assign" to a.keys().asSequence().associateWith { a.optString(it) }
            )
        )
    }.getOrDefault(ProjectLibraries())

    fun save(l: ProjectLibraries) {
        val j = JSONObject().put("v", 1)
            .put("folders", JSONArray(l.folders.map { f: LibraryFolder -> JSONObject().put("id", f.id).put("name", f.name) }))
            .put("assign", JSONObject(l.assignment))
        val s = file.startWrite()
        try {
            s.write(j.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(s)
        } catch (e: Exception) {
            file.failWrite(s)
            throw e
        }
    }
}
