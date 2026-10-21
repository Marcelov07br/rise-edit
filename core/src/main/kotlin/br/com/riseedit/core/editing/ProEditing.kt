package br.com.riseedit.core.editing

import br.com.riseedit.core.AudioClip
import br.com.riseedit.core.Clip
import br.com.riseedit.core.ColorAdjust
import br.com.riseedit.core.Marker
import br.com.riseedit.core.Orientation
import br.com.riseedit.core.Project
import br.com.riseedit.core.Stabilization
import br.com.riseedit.core.color.ColorGrade

/** Removes a main clip and pulls every later layer and marker left by its duration. */
object RippleDelete {
    fun clip(project: Project, index: Int): Project {
        val clip = project.clips.getOrNull(index) ?: return project
        val start = project.clips.take(index).sumOf { it.durationMs }
        val end = start + clip.durationMs
        val gap = clip.durationMs
        fun shift(t: Long) = when {
            t >= end -> t - gap
            t > start -> start
            else -> t
        }
        val audio = project.audio.filter { it.linkedClipId != clip.id }.map { a ->
            if (a.offsetMs >= end) a.copy(offsetMs = a.offsetMs - gap) else a
        }
        val texts = project.texts.mapNotNull { t ->
            when {
                t.startMs >= end -> t.copy(startMs = t.startMs - gap, endMs = t.endMs - gap)
                t.startMs >= start && t.endMs <= end -> null
                t.endMs <= start -> t
                else -> {
                    val s = shift(t.startMs)
                    val e = shift(t.endMs).coerceAtLeast(s + 1)
                    t.copy(startMs = s, endMs = e)
                }
            }
        }
        val overlays = project.overlays.map { o ->
            if (o.offsetMs >= end) o.copy(offsetMs = o.offsetMs - gap) else o
        }
        val markers = project.markers.filter { it.timeMs !in start until end }
            .map { if (it.timeMs >= end) it.copy(timeMs = it.timeMs - gap) else it }
        return SeparateAudio.syncAll(
            project.copy(
                clips = project.clips.filterIndexed { i, _ -> i != index },
                audio = audio,
                texts = texts,
                overlays = overlays,
                markers = markers
            )
        )
    }
}

/** Timeline markers: add at the playhead (toggles an existing one nearby), jump between them. */
object Markers {
    const val SNAP_MS = 150L

    fun toggle(project: Project, timeMs: Long): Project {
        val near = project.markers.firstOrNull { kotlin.math.abs(it.timeMs - timeMs) <= SNAP_MS }
        val markers = if (near != null) {
            project.markers - near
        } else {
            (project.markers + Marker(timeMs = timeMs.coerceAtLeast(0))).sortedBy { it.timeMs }
        }
        return project.copy(markers = markers)
    }

    fun next(project: Project, timeMs: Long): Long? =
        project.markers.map { it.timeMs }.filter { it > timeMs + SNAP_MS / 3 }.minOrNull()

    fun previous(project: Project, timeMs: Long): Long? =
        project.markers.map { it.timeMs }.filter { it < timeMs - SNAP_MS / 3 }.maxOrNull()
}

/**
 * J/L cuts on separated (linked) audio: the audio starts [AudioClip.leadMs] before its clip
 * (J) and/or keeps playing [AudioClip.tailMs] after it (L). Bounded by the source and by 0.
 */
object JLCut {
    fun set(project: Project, audioId: String, leadMs: Long, tailMs: Long): Project {
        val a = project.audio.firstOrNull { it.id == audioId && it.linkedClipId != null }
            ?: return project
        val updated = project.copy(
            audio = project.audio.map {
                if (it.id == a.id) {
                    it.copy(leadMs = leadMs.coerceAtLeast(0), tailMs = tailMs.coerceAtLeast(0))
                } else {
                    it
                }
            }
        )
        return SeparateAudio.sync(updated, a.linkedClipId!!)
    }
}

/**
 * Copy/paste of look and treatment between clips. Timing (trim, speed) is never pasted.
 * [groups] says which parts travel; see [AttributeGroup] / [AttributeCopy].
 */
data class ClipAttributes(
    val adjustments: ColorAdjust,
    val grade: ColorGrade,
    val orientation: Orientation,
    val stabilization: Stabilization,
    val volume: Float,
    val fadeMs: Long,
    val groups: Set<AttributeGroup> = AttributeGroup.entries.toSet(),
    /** Masks travel as one group ([AttributeGroup.MASKS]), keys included; pasted with fresh ids. */
    val masks: br.com.riseedit.core.mask.MaskSet = br.com.riseedit.core.mask.MaskSet()
) {
    // detekt: one guarded assignment per AttributeGroup.
    @Suppress("CyclomaticComplexMethod")
    fun applyTo(clip: Clip, only: Set<AttributeGroup> = groups): Clip {
        val use = AttributeCopy.close(only intersect groups)
        var a = clip.adjustments
        if (AttributeGroup.COLOR_BASIC in use) a = a.withBasic(adjustments)
        if (AttributeGroup.COLOR_CURVES in use) a = a.copy(curves = adjustments.curves)
        if (AttributeGroup.COLOR_HSL in use) a = a.copy(hsl = adjustments.hsl)
        if (AttributeGroup.COLOR_WHEELS in use) a = a.copy(wheels = adjustments.wheels)
        if (AttributeGroup.COLOR_QUALIFIER in use) a = a.copy(qualifier = adjustments.qualifier)
        if (AttributeGroup.COLOR_VIGNETTE in use) a = a.copy(vignette = adjustments.vignette)
        var g = clip.grade
        if (AttributeGroup.LOG_PROFILE in use) g = g.copy(log = grade.log)
        if (AttributeGroup.TONE_CURVE in use) g = g.copy(curve = grade.curve)
        if (AttributeGroup.LUT in use) {
            g =
                g.copy(
                    lutUri = grade.lutUri,
                    lutName = grade.lutName,
                    lutIntensity = grade.lutIntensity
                )
        }
        if (AttributeGroup.GRADE_ORDER in use) g = g.copy(lutBeforeAdjust = grade.lutBeforeAdjust)
        return clip.copy(
            adjustments = a,
            grade = g,
            orientation = if (AttributeGroup.ORIENTATION in use) orientation else clip.orientation,
            stabilization = if (AttributeGroup.STABILIZATION in
                use
            ) {
                stabilization
            } else {
                clip.stabilization
            },
            volume = if (AttributeGroup.VOLUME in use) volume else clip.volume,
            fadeMs = if (AttributeGroup.FADE in use) fadeMs else clip.fadeMs,
            masks = if (AttributeGroup.MASKS in use) masks.withNewIds() else clip.masks
        )
    }

    /** Groups that hold something in this snapshot (the paste dialog lists only these). */
    val available: List<AttributeGroup> get() = AttributeCopy.available(this).filter {
        it in groups
    }

    /** External files the selection needs (e.g. the user's .cube for [AttributeGroup.LUT]). */
    fun resources(only: Set<AttributeGroup> = groups): List<String> =
        if (AttributeGroup.LUT in only &&
            grade.lutUri != null
        ) {
            listOf(grade.lutUri)
        } else {
            emptyList()
        }

    companion object {
        fun of(clip: Clip, groups: Set<AttributeGroup> = AttributeGroup.entries.toSet()) =
            ClipAttributes(
                clip.adjustments,
                clip.grade,
                clip.orientation,
                clip.stabilization,
                clip.volume,
                clip.fadeMs,
                AttributeCopy.close(groups),
                clip.masks
            )
    }
}

/** Copyable parts of a clip. [dependsOn]: groups that must travel with this one. */
enum class AttributeGroup(
    val label: String,
    val section: String,
    val dependsOn: Set<String> = emptySet()
) {
    COLOR_BASIC("Ajustes básicos", "Cor"),
    COLOR_CURVES("Curvas", "Cor"),
    COLOR_HSL("HSL por cor", "Cor"),
    COLOR_WHEELS("Rodas lift/gamma/gain", "Cor"),
    COLOR_QUALIFIER("Seleção de cor (qualifier)", "Cor"),
    COLOR_VIGNETTE("Vinheta", "Cor"),
    MASKS("Máscaras (com keyframes)", "Máscara"),
    LOG_PROFILE("Perfil log", "Correção"),
    TONE_CURVE("Curva pronta", "Correção"),
    LUT("LUT do usuário (.cube)", "Correção"),

    /** Before/after only means something together with a grade. */
    GRADE_ORDER("Ordem LUT antes/depois", "Correção", setOf("LUT")),
    ORIENTATION("Orientação", "Clipe"),
    STABILIZATION("Estabilização", "Clipe"),
    VOLUME("Volume", "Clipe"),
    FADE("Fade", "Clipe");

    val dependencies: Set<AttributeGroup> get() = dependsOn.map { valueOf(it) }.toSet()
}

/** Pure selection logic for the copy/paste dialogs (dependency closure, select all, etc.). */
object AttributeCopy {
    /** Groups whose value differs from a fresh clip (what is worth listing). */
    // detekt: exhaustive when over AttributeGroup.
    @Suppress("CyclomaticComplexMethod")
    fun available(a: ClipAttributes): List<AttributeGroup> = AttributeGroup.entries.filter { g ->
        when (g) {
            AttributeGroup.COLOR_BASIC -> !a.adjustments.basic.isNeutral
            AttributeGroup.COLOR_CURVES -> !a.adjustments.curves.isIdentity
            AttributeGroup.COLOR_HSL -> !a.adjustments.hsl.isNeutral
            AttributeGroup.COLOR_WHEELS -> !a.adjustments.wheels.isNeutral
            AttributeGroup.COLOR_QUALIFIER -> a.adjustments.qualifier.active
            AttributeGroup.COLOR_VIGNETTE -> !a.adjustments.vignette.isNeutral
            AttributeGroup.MASKS -> a.masks.masks.isNotEmpty()
            AttributeGroup.LOG_PROFILE -> a.grade.log != ColorGrade().log
            AttributeGroup.TONE_CURVE -> a.grade.curve != ColorGrade().curve
            AttributeGroup.LUT -> a.grade.lutUri != null
            AttributeGroup.GRADE_ORDER -> a.grade.lutBeforeAdjust && a.grade.lutUri != null
            AttributeGroup.ORIENTATION -> a.orientation != Orientation()
            AttributeGroup.STABILIZATION -> a.stabilization != Stabilization()
            AttributeGroup.VOLUME -> a.volume != 1f
            AttributeGroup.FADE -> a.fadeMs != 0L
        }
    }

    /** Drops groups whose dependencies are not selected (repeats until stable). */
    fun close(sel: Set<AttributeGroup>): Set<AttributeGroup> {
        var cur = sel
        while (true) {
            val next = cur.filter { cur.containsAll(it.dependencies) }.toSet()
            if (next == cur) return cur
            cur = next
        }
    }

    /** Checking a group also checks what it depends on; unchecking removes its dependents. */
    fun toggle(
        sel: Set<AttributeGroup>,
        g: AttributeGroup,
        on: Boolean,
        shown: Collection<AttributeGroup>
    ): Set<AttributeGroup> {
        if (on) {
            val out = sel.toMutableSet()
            fun add(x: AttributeGroup) {
                if (out.add(x)) x.dependencies.forEach(::add)
            }
            add(g)
            return out.filter { it in shown }.toSet().let(::close)
        }
        return close(sel - g)
    }

    fun all(shown: Collection<AttributeGroup>) = close(shown.toSet())
    fun none() = emptySet<AttributeGroup>()
}

internal fun AudioClip.syncedTo(clip: Clip, clipOffset: Long): AudioClip {
    val end = (clip.endMs + tailMs).coerceIn(clip.startMs + 1, sourceDurationMs)
    val lead = leadMs.coerceAtMost(clipOffset).coerceAtMost(clip.startMs)
    val start = (clip.startMs - lead).coerceIn(0, end - 1)
    return copy(startMs = start, endMs = end, offsetMs = clipOffset - (clip.startMs - start))
}
