package br.com.riseedit.tools

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import br.com.riseedit.EditorState
import br.com.riseedit.core.Clip
import br.com.riseedit.core.color.ColorKeyGroup
import br.com.riseedit.designsystem.PanelIconButton

internal val KeyGold = Color(0xFFFFC94D)

/** Source time of [clip] under the playhead (clamped to the clip). */
internal fun clipSourceMs(state: EditorState, clip: Clip): Long {
    val i = state.project.clips.indexOfFirst { it.id == clip.id }.coerceAtLeast(0)
    val offset = state.project.clips.take(i).sumOf { it.durationMs }
    return clip.sourceAt((state.positionMs - offset).coerceIn(0, clip.durationMs))
}

/** Output position (ms) of a source time, by bisection (sourceAt is monotone). */
internal fun outputOfSource(clip: Clip, sourceMs: Long): Long {
    var low = 0L
    var high = clip.durationMs
    while (low < high) {
        val mid = (low + high) / 2
        if (clip.sourceAt(mid) < sourceMs) low = mid + 1 else high = mid
    }
    return low
}

/** Color key positions of the selected clip on the project timeline (MiniScrubBar diamonds). */
internal fun colorKeyMarks(state: EditorState): List<Long> {
    val clip = state.project.clips.getOrNull(state.selected) ?: return emptyList()
    if (clip.colorKeys.isEmpty) return emptyList()
    val offset = state.project.clips.take(state.selected).sumOf { it.durationMs }
    return clip.colorKeys.times().filter { it in clip.startMs..clip.endMs }.map {
        offset +
            outputOfSource(clip, it)
    }
}

internal fun AdjustTab.group() = when (this) {
    AdjustTab.BASIC -> ColorKeyGroup.BASIC
    AdjustTab.CURVES -> ColorKeyGroup.CURVES
    AdjustTab.HSL -> ColorKeyGroup.HSL
    AdjustTab.WHEELS -> ColorKeyGroup.WHEELS
    AdjustTab.QUALIFIER -> ColorKeyGroup.QUALIFIER
    AdjustTab.VIGNETTE -> ColorKeyGroup.VIGNETTE
    AdjustTab.GRADE -> ColorKeyGroup.LUT
}

/** Keyframe state of the open Ajustar tab (stateless; Paparazzi passes a fake). */
internal class ColorKeyUi(
    val hasKeyHere: (AdjustTab) -> Boolean,
    val animated: (AdjustTab) -> Boolean,
    val toggle: (AdjustTab) -> Unit,
    val prev: (() -> Unit)? = null,
    val next: (() -> Unit)? = null
)

/** Level-2 action: diamond (gold = key at the playhead, white = group animated, dim = none). */
@Composable
internal fun KeyActions(ui: ColorKeyUi, tab: AdjustTab) {
    val here = ui.hasKeyHere(tab)
    val anim = ui.animated(tab)
    if (anim) {
        PanelIconButton(
            Icons.Rounded.ChevronLeft,
            "Keyframe anterior",
            enabled =
            ui.prev != null
        ) { ui.prev?.invoke() }
    }
    PanelIconButton(
        KeyDiamond,
        if (here) "Remover keyframe (${tab.label})" else "Adicionar keyframe (${tab.label})",
        tint = when {
            here -> KeyGold
            anim -> Color.White
            else -> Color.White.copy(alpha = 0.55f)
        }
    ) { ui.toggle(tab) }
    if (anim) {
        PanelIconButton(
            Icons.Rounded.ChevronRight,
            "Próximo keyframe",
            enabled =
            ui.next != null
        ) { ui.next?.invoke() }
    }
}

/** Keyframe diamond (rotated square, same 24dp grid as Material Symbols Rounded; no gem icon exists). */
internal val KeyDiamond: androidx.compose.ui.graphics.vector.ImageVector by lazy {
    materialIcon("Rounded.KeyDiamond") {
        materialPath {
            moveTo(10.6f, 3.4f)
            curveTo(11.4f, 2.6f, 12.6f, 2.6f, 13.4f, 3.4f)
            lineTo(20.6f, 10.6f)
            curveTo(21.4f, 11.4f, 21.4f, 12.6f, 20.6f, 13.4f)
            lineTo(13.4f, 20.6f)
            curveTo(12.6f, 21.4f, 11.4f, 21.4f, 10.6f, 20.6f)
            lineTo(3.4f, 13.4f)
            curveTo(2.6f, 12.6f, 2.6f, 11.4f, 3.4f, 10.6f)
            close()
        }
    }
}
