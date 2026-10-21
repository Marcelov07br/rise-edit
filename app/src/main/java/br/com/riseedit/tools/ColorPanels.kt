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
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.FileDownload
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
import androidx.compose.foundation.horizontalScroll
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
    val target = colorTarget(state) ?: return
    val clip = target.clip
    val src = target.sourceMs(state.positionMs)
    // Shown values are the evaluated ones at the playhead (keyframes applied).
    val (a, shownGrade) = clip.colorAt(src)
    var tab by remember { mutableStateOf(AdjustTab.BASIC) }
    // Curves and wheels need room: expand the sheet automatically (handle still toggles).
    androidx.compose.runtime.LaunchedEffect(tab) {
        br.com.riseedit.ToolSheetDetent.expanded = tab == AdjustTab.CURVES || tab == AdjustTab.WHEELS || tab == AdjustTab.QUALIFIER
    }
    // On-preview tools: eyedropper (Seleção) and vignette center (Vinheta); cleared on tab/panel exit.
    val latestTarget by androidx.compose.runtime.rememberUpdatedState(target)
    val latestSrc by androidx.compose.runtime.rememberUpdatedState(src)
    fun editLatest(f: (ColorAdjust) -> ColorAdjust) {
        val c = vm.latestColorClip(latestTarget) ?: return
        val shown = c.colorAt(latestSrc).first
        vm.commitColor(latestTarget, br.com.riseedit.core.color.ColorKeyEdits.editAdjust(c, latestSrc, f(shown)))
    }
    DisposableEffect(tab, target.clip.id) {
        if (tab == AdjustTab.VIGNETTE) {
            ColorTools.vignetteCenter = androidx.compose.ui.geometry.Offset(a.vignette.cx, a.vignette.cy)
            ColorTools.onVignetteCenter = { u, v -> editLatest { it.copy(vignette = it.vignette.copy(cx = u, cy = v)) } }
        }
        onDispose {
            ColorTools.vignetteCenter = null
            ColorTools.onVignetteCenter = null
            ColorTools.eyedropper = null
            if (ColorTools.showMask) ColorTools.setMask(false, vm.player)
        }
    }
    val times = clip.colorKeys.keys(tab.group()).map { it.timeMs }.filter(target::inRange)
    fun jump(t: Long?) = t?.let { { vm.seek(target.projectMs(it)) } }
    val layer = target.layer
    if (layer != null) LayerHeader(layer) { vm.adjustmentLayer(layer.copy(opacity = it)) }
    AdjustContent(
        a, tab, { tab = it },
        change = { vm.commitColor(target, br.com.riseedit.core.color.ColorKeyEdits.editAdjust(clip, src, it)) },
        local = LocalColorUi(
            picking = ColorTools.eyedropper != null,
            onEyedropper = {
                ColorTools.eyedropper = if (ColorTools.eyedropper != null) null else { argb: Int ->
                    val rgb = floatArrayOf((argb shr 16 and 0xFF) / 255f, (argb shr 8 and 0xFF) / 255f, (argb and 0xFF) / 255f)
                    android.util.Log.i("RiseColor", "qualifier pick rgb=${rgb.joinToString { "%.3f".format(it) }}")
                    editLatest { it.copy(qualifier = br.com.riseedit.core.color.Qualifier.pick(it.qualifier, rgb)) }
                    ColorTools.eyedropper = null
                }
            },
            showMask = ColorTools.showMask,
            onMask = { ColorTools.setMask(!ColorTools.showMask, vm.player) }
        ),
        actions = AdjustActions(
            reset = vm::resetColor, copy = vm::copyAttributes, paste = vm::pasteAttributes,
            canPaste = vm.hasClipboard, applyAll = if (layer == null) vm::applyColorToAll else null,
            canReset = !a.isNeutral || !clip.colorKeys.isEmpty,
            savePreset = { savePresetOpen = true }, exportCube = { cubeOpen = true },
            scopes = { br.com.riseedit.color.Scopes.visible = !br.com.riseedit.color.Scopes.visible },
            match = if (layer == null && state.project.clips.size > 1) { { matchOpen = true } } else null
        ),
        compare = { CompareHoldButton(vm, enabled = !a.isNeutral || !clip.grade.isIdentity) },
        grade = { GradePanel(vm, state, target) },
        gradeActive = !shownGrade.isIdentity,
        keys = ColorKeyUi(
            hasKeyHere = { clip.colorKeys.hasKeyAt(it.group(), src) },
            animated = { clip.colorKeys.keys(it.group()).isNotEmpty() },
            toggle = {
                val g = it.group()
                android.util.Log.i("RiseColor", "key toggle ${g.id} at ${src}ms (had=${clip.colorKeys.hasKeyAt(g, src)})")
                vm.commitColor(target, br.com.riseedit.core.color.ColorKeyEdits.toggle(clip, g, src))
            },
            prev = jump(times.lastOrNull { it < src - 20 }),
            next = jump(times.firstOrNull { it > src + 20 })
        )
    )
    if (savePresetOpen) SavePresetDialog(a, onDismiss = { savePresetOpen = false }) { name, groups ->
        savePresetOpen = false
        UserPresetStore.add(vm.appContext, name, a, groups)
        vm.message("Preset \"$name\" salvo em Filtros › Meus.")
    }
    if (matchOpen) ColorMatchDialog(vm, state, onDismiss = { matchOpen = false })
    if (cubeOpen) ExportCubeDialog(vm, clip, a, shownGrade, onDismiss = { cubeOpen = false })
}

private var savePresetOpen by mutableStateOf(false)
private var cubeOpen by mutableStateOf(false)
private var matchOpen by mutableStateOf(false)

internal enum class AdjustTab(val label: String) {
    BASIC("Básico"), CURVES("Curvas"), HSL("HSL"), WHEELS("Rodas"), QUALIFIER("Seleção"), VIGNETTE("Vinheta"), GRADE("LUT/log")
}

internal class AdjustActions(
    val reset: () -> Unit = {},
    val copy: () -> Unit = {},
    val paste: () -> Unit = {},
    val canPaste: Boolean = false,
    val applyAll: (() -> Unit)? = {},
    val canReset: Boolean = true,
    val savePreset: (() -> Unit)? = null,
    val exportCube: (() -> Unit)? = null,
    val scopes: (() -> Unit)? = null,
    val match: (() -> Unit)? = null
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
    gradeActive: Boolean = false,
    keys: ColorKeyUi? = null,
    local: LocalColorUi = LocalColorUi()
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Level 2: icon-only actions in the top corner; reset set apart (gap + divider).
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            // Keyframe diamond of the open tab lives at the start (it belongs to the tab, not the clip).
            keys?.let { Row(horizontalArrangement = Arrangement.spacedBy(br.com.riseedit.designsystem.HierarchyTokens.ActionGap)) { KeyActions(it, tab) } }
            Spacer(Modifier.weight(1f).width(8.dp))
            // Visible: keyframe (left), compare, scopes; the rest collapses into "mais" when it doesn't fit.
            br.com.riseedit.designsystem.ActionBar(
                listOfNotNull(
                    actions.scopes?.let { br.com.riseedit.designsystem.PanelAction(Icons.Rounded.Insights, if (br.com.riseedit.color.Scopes.visible) "Ocultar scopes" else "Mostrar scopes", primary = true, onClick = it) },
                    br.com.riseedit.designsystem.PanelAction(Icons.Rounded.ContentCopy, "Copiar ajustes", onClick = actions.copy),
                    br.com.riseedit.designsystem.PanelAction(Icons.Rounded.ContentPaste, "Colar ajustes", actions.canPaste, onClick = actions.paste),
                    actions.applyAll?.let { br.com.riseedit.designsystem.PanelAction(Icons.Rounded.LibraryAdd, "Aplicar a todos os clipes", onClick = it) },
                    actions.match?.let { br.com.riseedit.designsystem.PanelAction(Icons.Rounded.AutoFixHigh, "Combinar cor com outro clipe", onClick = it) },
                    actions.savePreset?.let { br.com.riseedit.designsystem.PanelAction(Icons.Rounded.BookmarkAdd, "Salvar como preset", onClick = it) },
                    actions.exportCube?.let { br.com.riseedit.designsystem.PanelAction(Icons.Rounded.FileDownload, "Exportar LUT .cube", onClick = it) },
                    br.com.riseedit.designsystem.PanelAction(Icons.Rounded.RestartAlt, "Resetar todos", actions.canReset, danger = true, onClick = actions.reset)
                ),
                modifier = Modifier.weight(1f, fill = false),
                extraSlots = if (compare != null) 1 else 0,
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
                AdjustTab.QUALIFIER -> !a.qualifier.isNeutral
                AdjustTab.VIGNETTE -> !a.vignette.isNeutral
                AdjustTab.GRADE -> gradeActive
            }
        }, onSelect = onTab)
        when (tab) {
            AdjustTab.BASIC -> AdjustGrid(a, change)
            AdjustTab.CURVES -> CurvesTab(a, change, curveChannel)
            AdjustTab.HSL -> HslTab(a, change, band)
            AdjustTab.WHEELS -> WheelsTab(a, change)
            AdjustTab.QUALIFIER -> QualifierTab(a, change, local)
            AdjustTab.VIGNETTE -> VignetteTab(a, change)
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
