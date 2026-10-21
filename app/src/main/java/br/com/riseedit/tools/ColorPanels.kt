package br.com.riseedit.tools

import androidx.compose.runtime.Composable
import br.com.riseedit.ChoiceRow
import br.com.riseedit.EditorState
import br.com.riseedit.EditorViewModel
import br.com.riseedit.core.ColorAdjust
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import br.com.riseedit.color.ColorLive
import br.com.riseedit.redrawColor
import br.com.riseedit.riseSpring
import br.com.riseedit.designsystem.TileSlider
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState

private data class AdjustRow(
    val label: String,
    val range: ClosedFloatingPointRange<Float>,
    val get: (ColorAdjust) -> Float,
    val set: (ColorAdjust, Float) -> ColorAdjust
)

/** Display order follows the shader order (ColorAdjustMath). */
private val adjustRows = listOf(
    AdjustRow("Exposição (EV)", -2f..2f, { it.exposure }) { a, v -> a.copy(exposure = v) },
    AdjustRow("Brilho", -0.5f..0.5f, { it.brightness }) { a, v -> a.copy(brightness = v) },
    AdjustRow("Temperatura", -1f..1f, { it.warmth }) { a, v -> a.copy(warmth = v) },
    AdjustRow("Tint", -1f..1f, { it.tint }) { a, v -> a.copy(tint = v) },
    AdjustRow("Contraste", -0.8f..0.8f, { it.contrast }) { a, v -> a.copy(contrast = v) },
    AdjustRow("Sombras", -1f..1f, { it.shadows }) { a, v -> a.copy(shadows = v) },
    AdjustRow("Realces", -1f..1f, { it.highlights }) { a, v -> a.copy(highlights = v) },
    AdjustRow("Saturação", -1f..1f, { it.saturation }) { a, v -> a.copy(saturation = v) },
    AdjustRow("Vibrance", -1f..1f, { it.vibrance }) { a, v -> a.copy(vibrance = v) }
)

@Composable
internal fun AdjustPanel(vm: EditorViewModel, state: EditorState) {
    val clip = state.project.clips.getOrNull(state.selected) ?: return
    val a = clip.adjustments
    var tab by remember { mutableStateOf(AdjustTab.BASIC) }
    // Curves and wheels need room: expand the sheet automatically (handle still toggles).
    androidx.compose.runtime.LaunchedEffect(tab) {
        br.com.riseedit.ToolSheetDetent.expanded = tab == AdjustTab.CURVES || tab == AdjustTab.WHEELS
    }
    AdjustContent(
        a, tab, { tab = it },
        change = { vm.clipSettings(clip.copy(adjustments = it)) },
        actions = AdjustActions(
            reset = vm::resetColor, copy = vm::copyAttributes, paste = vm::pasteAttributes,
            canPaste = vm.hasClipboard, applyAll = vm::applyColorToAll,
            canReset = !a.isNeutral
        ),
        compare = { CompareHoldButton(vm, enabled = !a.isNeutral || !clip.grade.isIdentity) },
        grade = { GradePanel(vm, state) },
        gradeActive = !clip.grade.isIdentity
    )
}

internal enum class AdjustTab(val label: String) { BASIC("Básico"), CURVES("Curvas"), HSL("HSL"), WHEELS("Rodas"), GRADE("LUT/log") }

internal class AdjustActions(
    val reset: () -> Unit = {},
    val copy: () -> Unit = {},
    val paste: () -> Unit = {},
    val canPaste: Boolean = false,
    val applyAll: () -> Unit = {},
    val canReset: Boolean = true
)

/** Header (reset/copy/paste/apply to all) + tab content. Stateless, used by Paparazzi too. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun AdjustContent(
    a: ColorAdjust,
    tab: AdjustTab,
    onTab: (AdjustTab) -> Unit,
    change: (ColorAdjust) -> Unit,
    actions: AdjustActions,
    curveChannel: Int = 0,
    band: Int = 0,
    compare: (@Composable () -> Unit)? = null,
    grade: (@Composable () -> Unit)? = null,
    gradeActive: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Level 2: icon-only actions in the top corner; reset set apart (gap + divider).
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            br.com.riseedit.designsystem.ActionBar(
                listOf(
                    br.com.riseedit.designsystem.PanelAction(Icons.Rounded.ContentCopy, "Copiar ajustes", onClick = actions.copy),
                    br.com.riseedit.designsystem.PanelAction(Icons.Rounded.ContentPaste, "Colar ajustes", actions.canPaste, onClick = actions.paste),
                    br.com.riseedit.designsystem.PanelAction(Icons.Rounded.LibraryAdd, "Aplicar a todos os clipes", onClick = actions.applyAll),
                    br.com.riseedit.designsystem.PanelAction(Icons.Rounded.RestartAlt, "Resetar todos", actions.canReset && !a.isNeutral, danger = true, onClick = actions.reset)
                ),
                extra = compare
            )
        }
        // Level 1: tools as a segmented pill.
        val tabs = AdjustTab.entries.filter { it != AdjustTab.GRADE || grade != null }
        br.com.riseedit.designsystem.SegmentedTabs(tabs, tab, label = { it.label }, marked = {
            when (it) {
                AdjustTab.BASIC -> !a.basic.isNeutral
                AdjustTab.CURVES -> !a.curves.isIdentity
                AdjustTab.HSL -> !a.hsl.isNeutral
                AdjustTab.WHEELS -> !a.wheels.isNeutral
                AdjustTab.GRADE -> gradeActive
            }
        }, onSelect = onTab)
        when (tab) {
            AdjustTab.BASIC -> AdjustGrid(a, change)
            AdjustTab.CURVES -> CurvesTab(a, change, curveChannel)
            AdjustTab.HSL -> HslTab(a, change, band)
            AdjustTab.WHEELS -> WheelsTab(a, change)
            AdjustTab.GRADE -> grade?.invoke()
        }
    }
}

private val curveNames = listOf("Master", "R", "G", "B")
private val curveColors = listOf(
    androidx.compose.ui.graphics.Color.White, androidx.compose.ui.graphics.Color(0xFFFF5A5A),
    androidx.compose.ui.graphics.Color(0xFF5AE07A), androidx.compose.ui.graphics.Color(0xFF5AA0FF)
)

@Composable
private fun CurvesTab(a: ColorAdjust, change: (ColorAdjust) -> Unit, initial: Int) {
    var ch by remember { mutableStateOf(initial) }
    br.com.riseedit.designsystem.GraphicSurface {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                br.com.riseedit.designsystem.ChannelChips((0..3).toList(), ch, { curveNames[it] }, { curveColors[it] }) { ch = it }
            }
            br.com.riseedit.designsystem.PanelIconButton(Icons.Rounded.RestartAlt, "Resetar curva ${curveNames[ch]}") {
                change(a.copy(curves = a.curves.withChannel(ch, br.com.riseedit.core.ColorCurves.IDENTITY)))
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            val w = maxWidth // full width, square (CurveEditor keeps 1:1)
            br.com.riseedit.designsystem.CurveEditor(
                a.curves.channel(ch), curveColors[ch], Modifier.width(w)
            ) { change(a.copy(curves = a.curves.withChannel(ch, it))) }
        }
    }
    Text(
        "Toque para adicionar ponto, arraste para mover, toque duplo remove.",
        color = br.com.riseedit.RiseMuted, fontSize = 12.sp
    )
}

private val hslKinds = listOf("Matiz", "Saturação", "Luminância")

@Composable
private fun HslTab(a: ColorAdjust, change: (ColorAdjust) -> Unit, initial: Int) {
    var band by remember { mutableStateOf(initial) }
    br.com.riseedit.designsystem.ChannelChips(
        (0 until br.com.riseedit.core.HslBands.BANDS).toList(), band, { br.com.riseedit.core.HslBands.NAMES[it] },
        { androidx.compose.ui.graphics.Color.hsv(br.com.riseedit.core.HslBands.CENTERS[it], 0.75f, 0.95f) }
    ) { band = it }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cols = (maxWidth / 170.dp).toInt().coerceIn(2, 3)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            (0..2).chunked(cols).forEach { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    line.forEach { k ->
                        val v = a.hsl.values[band * 3 + k]
                        TileSlider(hslKinds[k], v, -1f..1f, Modifier.weight(1f)) {
                            change(a.copy(hsl = a.hsl.with(band, k, it)))
                        }
                    }
                    repeat(cols - line.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun WheelsTab(a: ColorAdjust, change: (ColorAdjust) -> Unit) {
    val w = a.wheels
    val items = listOf(
        Triple("Lift", w.lift) { n: br.com.riseedit.core.Wheel -> w.copy(lift = n) },
        Triple("Gamma", w.gamma) { n: br.com.riseedit.core.Wheel -> w.copy(gamma = n) },
        Triple("Gain", w.gain) { n: br.com.riseedit.core.Wheel -> w.copy(gain = n) }
    )
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cols = (maxWidth / 150.dp).toInt().coerceIn(2, 3)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items.chunked(cols).forEach { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    line.forEach { (name, wheel, set) ->
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            br.com.riseedit.designsystem.GraphicSurface {
                                br.com.riseedit.designsystem.ChannelChips(listOf(name), name, { it }) {}
                                br.com.riseedit.designsystem.ColorWheelPad(wheel.x, wheel.y) { x, y ->
                                    change(a.copy(wheels = set(wheel.copy(x = x, y = y))))
                                }
                            }
                            TileSlider("Luminância", wheel.luma, -1f..1f) {
                                change(a.copy(wheels = set(wheel.copy(luma = it))))
                            }
                        }
                    }
                    repeat(cols - line.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
    Text("Toque duplo na roda volta ao centro.", color = br.com.riseedit.RiseMuted, fontSize = 12.sp)
}

/** Copy/paste checklist with select all / clear; dependencies are kept consistent by core. */
@Composable
internal fun AttributeSelectDialog(
    dialog: br.com.riseedit.AttributeDialog,
    onDismiss: () -> Unit,
    onConfirm: (Set<br.com.riseedit.core.editing.AttributeGroup>, Boolean) -> Unit
) {
    var sel by remember(dialog) { mutableStateOf(dialog.initial) }
    var all by remember(dialog) { mutableStateOf(false) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        title = { Text(if (dialog.paste) "Colar atributos" else "Copiar atributos") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.TextButton(onClick = {
                        sel = br.com.riseedit.core.editing.AttributeCopy.all(dialog.shown)
                    }) { Text("Selecionar todos") }
                    androidx.compose.material3.TextButton(onClick = { sel = emptySet() }) { Text("Desmarcar") }
                }
                dialog.shown.groupBy { it.section }.forEach { (section, groups) ->
                    Text(section, color = br.com.riseedit.RiseMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    groups.forEach { g ->
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            androidx.compose.material3.Checkbox(g in sel, {
                                sel = br.com.riseedit.core.editing.AttributeCopy.toggle(sel, g, it, dialog.shown)
                            })
                            Column {
                                Text(g.label)
                                if (g.dependencies.isNotEmpty()) {
                                    Text("requer " + g.dependencies.joinToString { it.label }, color = br.com.riseedit.RiseMuted, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
                if (dialog.paste) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        androidx.compose.material3.Checkbox(all, { all = it })
                        Text("Colar em todos os clipes")
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { onConfirm(sel, all) }, enabled = sel.isNotEmpty()) {
                Text(if (dialog.paste) "Colar" else "Copiar")
            }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/** Responsive grid of [TileSlider]s: 2 columns on phones, up to 4 on wide layouts. */
@Composable
internal fun AdjustGrid(a: ColorAdjust, change: (ColorAdjust) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cols = (maxWidth / 170.dp).toInt().coerceIn(2, 4)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            adjustRows.chunked(cols).forEach { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    line.forEach { row ->
                        TileSlider(row.label, row.get(a), row.range, Modifier.weight(1f)) { change(row.set(a, it)) }
                    }
                    repeat(cols - line.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** Hold to see the original (adjust + grade bypassed in preview only). */
@Composable
private fun CompareHoldButton(vm: EditorViewModel, enabled: Boolean) {
    var held by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (held) 0.96f else 1f, riseSpring(), label = "compare")
    val haptic = LocalHapticFeedback.current
    DisposableEffect(Unit) { onDispose { ColorLive.holdOriginal = false } }
    br.com.riseedit.designsystem.PanelIconButton(
        Icons.Rounded.Compare, if (held) "Original" else "Segure para comparar",
        enabled = enabled,
        tint = if (held) br.com.riseedit.RiseMuted else br.com.riseedit.RiseWhite,
        modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(onPress = {
                    held = true
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    ColorLive.holdOriginal = true
                    redrawPreview(vm.player)
                    tryAwaitRelease()
                    held = false
                    ColorLive.holdOriginal = false
                    redrawPreview(vm.player)
                })
            }
    ) {}
}

private fun redrawPreview(player: androidx.media3.common.Player?) {
    if (player is androidx.media3.exoplayer.ExoPlayer) {
        redrawColor(player)
    } else if (player != null && !player.isPlaying) {
        player.seekTo(player.currentPosition)
    }
}

private val fades = listOf(0L to "Nenhuma", 300L to "Suave", 600L to "Média", 1000L to "Longa")

/** Entry/exit fade of the selected clip (fade to black and back). */
@Composable
internal fun ClipAnimationPanel(vm: EditorViewModel, state: EditorState) {
    val clip = state.project.clips.getOrNull(state.selected) ?: return
    val current = fades.minBy { kotlin.math.abs(it.first - clip.fadeMs) }.first
    ChoiceRow(fades.map { it.first }, current, { vm.clipSettings(clip.copy(fadeMs = it)) }) { ms ->
        fades.first { it.first == ms }.second
    }
}
