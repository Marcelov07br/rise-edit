package br.com.riseedit.core

import java.util.UUID

/** One media file the project points at but that is no longer readable. */
data class MissingMedia(val uri: String, val itemIds: List<String>, val name: String)

/** Pure recovery model: find missing media and reconnect it by URI across clips, audio and overlays. */
object ProjectRecovery {
    fun missing(project: Project, readable: (String) -> Boolean): List<MissingMedia> {
        val uses = mutableMapOf<String, MutableList<String>>()
        val names = mutableMapOf<String, String>()
        fun note(uri: String, id: String, name: String) {
            if (readable(uri)) return
            uses.getOrPut(uri) { mutableListOf() } += id
            names.putIfAbsent(uri, name)
        }
        project.clips.forEach { note(it.uri, it.id, it.name) }
        project.audio.forEach { note(it.uri, it.id, it.name) }
        project.overlays.forEach { note(it.clip.uri, it.id, it.clip.name) }
        return uses.map { (uri, ids) -> MissingMedia(uri, ids, names.getValue(uri)) }
    }

    /** Points every item that used [oldUri] at [newUri] and drops stale proxies (they must be rebuilt). */
    fun reconnect(project: Project, oldUri: String, newUri: String): Project {
        fun clip(c: Clip) = if (c.uri !=
            oldUri
        ) {
            c
        } else {
            c.copy(uri = newUri, proxyUri = null, proxyRequired = false)
        }
        return project.copy(
            clips = project.clips.map(::clip),
            audio = project.audio.map { if (it.uri == oldUri) it.copy(uri = newUri) else it },
            overlays = project.overlays.map { it.copy(clip = clip(it.clip)) }
        )
    }

    /** A new id for a restored copy so the original project file stays untouched. */
    fun restoredCopy(project: Project, name: String = project.name + " (recuperado)") =
        project.copy(id = UUID.randomUUID().toString(), name = name)
}
