package br.com.riseedit.core

/**
 * The single selected item. Tools act only on this target: there is no implicit "edit the last
 * overlay". [None] means the main timeline context (the selected clip index).
 */
sealed interface Selection {
    data object None : Selection
    data class Clip(override val id: String) : Selection
    data class Audio(override val id: String) : Selection
    data class Overlay(override val id: String) : Selection
    data class Text(override val id: String) : Selection

    val id: String? get() = when (this) {
        None -> null
        is Clip -> id
        is Audio -> id
        is Overlay -> id
        is Text -> id
    }

    companion object {
        /** Rebuilds a selection from the id persisted in UI state, preferring the real owner. */
        fun of(project: Project, id: String?): Selection = when {
            id == null -> None
            project.clips.any { it.id == id } -> Clip(id)
            project.audio.any { it.id == id } -> Audio(id)
            project.overlays.any { it.id == id } -> Overlay(id)
            project.texts.any { it.id == id } -> Text(id)
            else -> None
        }
    }
}

/** Selection only survives when its target still exists; otherwise it becomes [Selection.None]. */
fun Selection.revalidated(project: Project): Selection =
    if (this is Selection.None || project.contains(id)) this else Selection.None

private fun Project.contains(id: String?) = clips.any { it.id == id } ||
    audio.any { it.id == id } ||
    overlays.any { it.id == id } ||
    texts.any { it.id == id }
