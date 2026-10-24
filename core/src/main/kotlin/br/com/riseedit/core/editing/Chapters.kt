package br.com.riseedit.core.editing

import br.com.riseedit.core.MARKER_DEFAULT_COLOR
import br.com.riseedit.core.Marker
import br.com.riseedit.core.Project

/** 0.24.0: marker edits used by the M key and the Marcadores panel. */
object MarkerEdits {
    /** Marker colors (Premiere-like set). */
    val COLORS = listOf(
        MARKER_DEFAULT_COLOR, 0xFF34C759.toInt(), 0xFFFFCC00.toInt(), 0xFFFF9500.toInt(),
        0xFFFF3B30.toInt(), 0xFFAF52DE.toInt(), 0xFFFF2D92.toInt(), 0xFFFFFFFF.toInt()
    )

    /** Premiere "M": adds at the playhead; an existing marker within [Markers.SNAP_MS] is reused. */
    fun add(project: Project, timeMs: Long): Pair<Project, Marker> {
        val t = timeMs.coerceIn(0, maxOf(0, project.durationMs))
        project.markers.firstOrNull { kotlin.math.abs(it.timeMs - t) <= Markers.SNAP_MS }?.let { return project to it }
        val m = Marker(timeMs = t, label = "Capítulo ${project.markers.size + 1}")
        return project.copy(markers = (project.markers + m).sortedBy { it.timeMs }) to m
    }

    fun update(project: Project, marker: Marker) = project.copy(
        markers = project.markers.map { if (it.id == marker.id) marker.copy(timeMs = marker.timeMs.coerceAtLeast(0)) else it }
            .sortedBy { it.timeMs }
    )

    fun remove(project: Project, id: String) = project.copy(markers = project.markers.filterNot { it.id == id })
}

/**
 * YouTube chapters from markers ("00:00 Título" per line). YouTube's rules: the first timestamp
 * is 00:00, at least 3 chapters, each at least 10 s long (support.google.com/youtube/answer/9884579).
 */
object YouTubeChapters {
    const val MIN_CHAPTERS = 3
    const val MIN_LENGTH_MS = 10_000L

    data class Chapter(val startMs: Long, val title: String)
    data class Result(val chapters: List<Chapter>, val problems: List<String>, val notes: List<String>) {
        val ok get() = problems.isEmpty()
        val text get() = chapters.joinToString("\n") { "${stamp(it.startMs, longForm)} ${it.title}" }
        private val longForm get() = (chapters.lastOrNull()?.startMs ?: 0) >= 3_600_000
    }

    fun build(markers: List<Marker>, durationMs: Long): Result {
        val notes = mutableListOf<String>()
        val sorted = markers.filter { it.timeMs < durationMs || durationMs <= 0 }.sortedBy { it.timeMs }
        val chapters = sorted.mapIndexed { i, m -> Chapter(m.timeMs / 1000 * 1000, m.label.trim().ifEmpty { "Capítulo ${i + 1}" }) }
            .distinctBy { it.startMs }.toMutableList()
        if (chapters.isNotEmpty() && chapters.first().startMs > 0) {
            chapters.add(0, Chapter(0, "Introdução"))
            notes += "O primeiro capítulo precisa começar em 00:00: adicionei \"00:00 Introdução\"."
        } else if (chapters.isEmpty() && durationMs > 0) {
            notes += "Nenhum marcador: aperte M na timeline para marcar o início de cada capítulo."
        }
        val problems = mutableListOf<String>()
        if (chapters.size < MIN_CHAPTERS) {
            problems += "O YouTube exige pelo menos $MIN_CHAPTERS capítulos (há ${chapters.size})."
        }
        chapters.forEachIndexed { i, c ->
            val end = chapters.getOrNull(i + 1)?.startMs ?: durationMs
            if (end - c.startMs < MIN_LENGTH_MS) {
                problems += "\"${c.title}\" (${stamp(c.startMs, false)}) tem ${(end - c.startMs) / 1000} s; o mínimo é 10 s."
            }
        }
        return Result(chapters, problems, notes)
    }

    /** "00:00", "12:34" or "1:02:03" when [long]. */
    fun stamp(ms: Long, long: Boolean): String {
        val s = ms / 1000
        return if (long || s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60)
        else "%02d:%02d".format(s / 60, s % 60)
    }
}
