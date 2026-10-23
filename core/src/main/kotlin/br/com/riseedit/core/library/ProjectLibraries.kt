package br.com.riseedit.core.library

import java.util.UUID

/** 0.22.1: a user-named folder of projects. */
data class LibraryFolder(val id: String, val name: String)

/**
 * Bibliotecas de projetos (0.22.1). Pure state: folders plus project→folder assignment.
 * Every project belongs to exactly one folder; anything unassigned or pointing at a
 * missing folder falls back to [DEFAULT_ID] ("Padrão"), created on demand.
 */
data class ProjectLibraries(
    val folders: List<LibraryFolder> = emptyList(),
    val assignment: Map<String, String> = emptyMap()
) {
    fun folderOf(projectId: String): String =
        assignment[projectId]?.takeIf { id -> folders.any { it.id == id } } ?: DEFAULT_ID

    fun count(folderId: String, projectIds: Collection<String>): Int =
        projectIds.count { folderOf(it) == folderId }

    /** Migration/repair: Padrão exists whenever any project needs it; all projects assigned. */
    fun normalized(projectIds: Collection<String>): ProjectLibraries {
        val needsDefault = projectIds.any { folderOf(it) == DEFAULT_ID }
        val f = if (needsDefault && folders.none { it.id == DEFAULT_ID }) {
            listOf(LibraryFolder(DEFAULT_ID, DEFAULT_NAME)) + folders
        } else {
            folders
        }
        val fixed = copy(folders = f)
        return fixed.copy(assignment = projectIds.associateWith { fixed.folderOf(it) })
    }

    fun create(name: String, id: String = UUID.randomUUID().toString()): ProjectLibraries =
        copy(folders = folders + LibraryFolder(id, uniqueName(cleanName(name, "Nova biblioteca"))))

    fun rename(folderId: String, name: String): ProjectLibraries {
        val n = name.trim().take(MAX_NAME)
        if (n.isBlank()) return this
        return copy(folders = folders.map { if (it.id == folderId) it.copy(name = n) else it })
    }

    /** Empty folders delete directly; with projects the caller confirms and they move to Padrão. */
    fun needsConfirmToDelete(folderId: String, projectIds: Collection<String>) =
        count(folderId, projectIds) > 0

    fun canDelete(folderId: String) = folderId != DEFAULT_ID && folders.any { it.id == folderId }

    fun delete(folderId: String, projectIds: Collection<String>): ProjectLibraries {
        if (!canDelete(folderId)) return this
        val moved = assignment.mapValues { (_, v) -> if (v == folderId) DEFAULT_ID else v }
        return copy(folders = folders.filterNot { it.id == folderId }, assignment = moved)
            .normalized(projectIds)
    }

    fun move(projectId: String, folderId: String): ProjectLibraries =
        if (folders.none { it.id == folderId }) this else copy(assignment = assignment + (projectId to folderId))

    /** A new project lands in [folderId] (or Padrão, created if this is the first project). */
    fun assignNew(projectId: String, folderId: String?): ProjectLibraries {
        val target = folderId?.takeIf { id -> folders.any { it.id == id } }
        return if (target != null) move(projectId, target) else copy(assignment = assignment - projectId).normalized(assignment.keys + projectId)
    }

    fun forget(projectId: String) = copy(assignment = assignment - projectId)

    private fun uniqueName(base: String): String {
        if (folders.none { it.name.equals(base, true) }) return base
        var i = 2
        while (folders.any { it.name.equals("$base ($i)", true) }) i++
        return "$base ($i)"
    }

    companion object {
        const val DEFAULT_ID = "padrao"
        const val DEFAULT_NAME = "Padrão"
        const val MAX_NAME = 40
        private fun cleanName(n: String, fallback: String) = n.trim().take(MAX_NAME).ifBlank { fallback }

        /** Search + Recentes/Favoritos inside the selected folder. */
        fun <T> filter(
            items: List<T>,
            libraries: ProjectLibraries,
            folderId: String?,
            query: String,
            favoritesOnly: Boolean,
            id: (T) -> String,
            name: (T) -> String,
            favorite: (T) -> Boolean
        ): List<T> = items.filter {
            (folderId == null || libraries.folderOf(id(it)) == folderId) &&
                (!favoritesOnly || favorite(it)) && name(it).contains(query.trim(), true)
        }

        /** v1 JSON-ish map form used by the app store (kept here so it's unit-tested). */
        fun encode(l: ProjectLibraries): Map<String, Any> = mapOf(
            "v" to 1,
            "folders" to l.folders.map { mapOf("id" to it.id, "name" to it.name) },
            "assign" to l.assignment
        )

        @Suppress("UNCHECKED_CAST")
        fun decode(m: Map<String, Any?>?): ProjectLibraries {
            if (m == null) return ProjectLibraries()
            val f = (m["folders"] as? List<Map<String, Any?>>).orEmpty().mapNotNull {
                val id = it["id"] as? String ?: return@mapNotNull null
                LibraryFolder(id, (it["name"] as? String).orEmpty().ifBlank { DEFAULT_NAME })
            }.distinctBy { it.id }
            val a = (m["assign"] as? Map<String, Any?>).orEmpty().mapNotNull { (k, v) ->
                (v as? String)?.let { k to it }
            }.toMap()
            return ProjectLibraries(f, a)
        }
    }
}
