package br.com.riseedit.tools

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.riseedit.EditorState
import br.com.riseedit.EditorViewModel
import br.com.riseedit.RiseMuted
import br.com.riseedit.core.BackgroundKind
import br.com.riseedit.core.Clip
import br.com.riseedit.core.FormatRatio
import br.com.riseedit.core.FrameBackground
import br.com.riseedit.core.ScaleMode
import br.com.riseedit.core.Timeline
import br.com.riseedit.core.TransformKeyframe
import br.com.riseedit.designsystem.ActionBar
import br.com.riseedit.designsystem.GraphicSurface
import br.com.riseedit.designsystem.PanelAction
import br.com.riseedit.designsystem.PanelIconButton
import br.com.riseedit.designsystem.RiseDropdown
import br.com.riseedit.designsystem.SegmentedTabs
import kotlin.math.roundToInt

/** 0.24.0: same tab / icon-bar / tile philosophy as the color zone. */
internal enum class FormatTab(val label: String) {
    RATIO("Proporção"),
    FRAME("Enquadrar"),
    BACKGROUND("Fundo")
}

/** Callbacks of the Formato zone (stateless content → Paparazzi without a ViewModel). */
internal class FormatActions(
    val ratio: (Float) -> Unit = {},
    val clip: (Clip) -> Unit = {},
    val transform: (TransformKeyframe) -> Unit = {},
    val background: (FrameBackground) -> Unit = {},
    /** Source time of the playhead in the selected clip (keyframe evaluation). */
    val sourceMs: Long = 0,
    /** Source frame ratio (w/h) of the selected clip; drives the mini frame editor. */
    val sourceRatio: Float = 16f / 9f,
    /** 0.24.0 item 2: automatic reframe (null = not wired). */
    val reframe: (() -> Unit)? = null,
    /** Reframe analysis progress 0..1 (null = idle). */
    val reframeProgress: Float? = null
)

@Composable
internal fun FormatPanel(vm: EditorViewModel, state: EditorState) {
    val clip = state.project.clips.getOrNull(state.selected)
    val info = clip?.sourceInfo
    val ratio = if (info != null && info.width > 0 && info.height > 0) {
        if (info.rotationDegrees in listOf(90, 270)) info.height.toFloat() / info.width
        else info.width.toFloat() / info.height
    } else 16f / 9f
    FormatContent(
        state,
        FormatActions(
            ratio = { vm.projectSettings(it, state.project.export) },
            clip = vm::clipSettings,
            transform = vm::transformSelected,
            background = vm::background,
            sourceMs = vm.previewSourceMs,
            sourceRatio = ratio,
            reframe = { vm.autoReframe(ratio) },
            reframeProgress = state.progress.takeIf {
                state.busy == br.com.riseedit.editor.ReframeCoordinator.BUSY
            }
        )
    )
}

@Composable
internal fun FormatContent(
    state: EditorState,
    vm: FormatActions,
    initialTab: FormatTab = FormatTab.RATIO,
    infoOpenInitially: Boolean = false,
    dropdownOpenInitially: Boolean = false
) {
    val p = state.project
    val clip = p.clips.getOrNull(state.selected) ?: return
    val enabled = state.busy == null
    var tab by remember { mutableStateOf(initialTab) }
    val key = Timeline.keyframe(clip, vm.sourceMs)
    val framed = clip.keyframes.isNotEmpty() || clip.orientation.scaleMode != ScaleMode.FILL
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            ActionBar(
                listOf(
                    PanelAction(
                        Icons.Rounded.Fullscreen,
                        "Preencher (cortar)",
                        enabled,
                        primary = true,
                        checked = clip.orientation.scaleMode == ScaleMode.FILL
                    ) { vm.clip(clip.copy(orientation = clip.orientation.copy(scaleMode = ScaleMode.FILL))) },
                    PanelAction(
                        Icons.Rounded.FitScreen,
                        "Ajustar (barras)",
                        enabled,
                        primary = true,
                        checked = clip.orientation.scaleMode == ScaleMode.FIT
                    ) { vm.clip(clip.copy(orientation = clip.orientation.copy(scaleMode = ScaleMode.FIT))) },
                    PanelAction(
                        Icons.Rounded.CenterFocusStrong,
                        "Centralizar",
                        enabled && (key.x != 0f || key.y != 0f)
                    ) { vm.transform(key.copy(x = 0f, y = 0f)) },
                    PanelAction(
                        Icons.Rounded.RestartAlt,
                        "Resetar formato",
                        enabled && (framed || p.aspectRatio != 0f || !p.background.isBlack),
                        danger = true
                    ) {
                        vm.clip(clip.copy(keyframes = emptyList(), tracks = null,
                            orientation = clip.orientation.copy(scaleMode = ScaleMode.FILL)))
                        vm.ratio(0f)
                        vm.background(FrameBackground())
                    }
                )
            )
        }
        SegmentedTabs(
            FormatTab.entries.toList(),
            tab,
            label = { it.label },
            marked = {
                when (it) {
                    FormatTab.RATIO -> p.aspectRatio != 0f
                    FormatTab.FRAME -> clip.keyframes.isNotEmpty()
                    FormatTab.BACKGROUND -> !p.background.isBlack
                }
            },
            onSelect = { tab = it }
        )
        when (tab) {
            FormatTab.RATIO -> RatioTab(p.aspectRatio, enabled, vm.ratio)
            FormatTab.FRAME -> FrameTab(state, clip, key, p.aspectRatio, vm, enabled, infoOpenInitially)
            FormatTab.BACKGROUND -> BackgroundTab(
                p.background,
                clip.orientation.scaleMode,
                p.aspectRatio,
                enabled,
                dropdownOpenInitially,
                infoOpenInitially,
                vm.background
            )
        }
    }
}

@Composable
private fun RatioTab(current: Float, enabled: Boolean, set: (Float) -> Unit) {
    val selected = FormatRatio.of(current)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cols = (maxWidth / 104.dp).toInt().coerceIn(3, FormatRatio.entries.size)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FormatRatio.entries.chunked(cols).forEach { line ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        line.forEach { r ->
                            RatioCard(r, r == selected, enabled, Modifier.weight(1f)) {
                                set(if (r == FormatRatio.FREE) current.takeIf { it > 0f } ?: 1.5f else r.ratio)
                            }
                        }
                        repeat(cols - line.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        if (selected == FormatRatio.FREE) {
            TileGrid(
                listOf(
                    Tile(
                        "Largura ÷ altura",
                        current,
                        FormatRatio.FREE_RANGE,
                        1.5f,
                        format = { "%.2f : 1".format(it) }
                    ) { set(it) }
                ),
                enabled
            )
        }
    }
}

/** Card with a mini drawing of the ratio (accent outline when selected). */
@Composable
private fun RatioCard(
    r: FormatRatio,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    val border = if (selected) accent else Color(0x33FFFFFF)
    Column(
        modifier.clip(RoundedCornerShape(14.dp))
            .background(if (selected) accent.copy(alpha = 0.14f) else Color(0xFF1C1C1E))
            .border(if (selected) 2.dp else 1.dp, border, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp)
            .semantics { contentDescription = "Proporção ${r.label}" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Canvas(Modifier.size(44.dp)) {
            val stroke = Stroke(
                width = 2.dp.toPx(),
                pathEffect = if (r == FormatRatio.FREE) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null
            )
            val ratio = when (r) {
                FormatRatio.ORIGINAL -> 16f / 9f
                FormatRatio.FREE -> 1.3f
                else -> r.ratio
            }
            val max = size.minDimension * 0.92f
            val w = if (ratio >= 1f) max else max * ratio
            val h = if (ratio >= 1f) max / ratio else max
            val tl = Offset((size.width - w) / 2, (size.height - h) / 2)
            val color = if (selected) accent else Color.White
            drawRoundRect(color.copy(alpha = 0.18f), tl, Size(w, h), androidx.compose.ui.geometry.CornerRadius(4f))
            drawRoundRect(color, tl, Size(w, h), androidx.compose.ui.geometry.CornerRadius(4f), style = stroke)
            if (r == FormatRatio.ORIGINAL) {
                // film perforations
                repeat(4) { i ->
                    val x = tl.x + w * (0.15f + i * 0.23f)
                    drawCircle(color, 1.6.dp.toPx(), Offset(x, tl.y + 4.dp.toPx()))
                    drawCircle(color, 1.6.dp.toPx(), Offset(x, tl.y + h - 4.dp.toPx()))
                }
            }
        }
        Text(
            r.label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) accent else Color.White
        )
    }
}

@Composable
private fun FrameTab(
    state: EditorState,
    clip: Clip,
    key: TransformKeyframe,
    projectRatio: Float,
    vm: FormatActions,
    enabled: Boolean,
    infoOpenInitially: Boolean
) {
    var info by remember { mutableStateOf(infoOpenInitially) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        GraphicSurface {
            FrameEditor(
                sourceRatio = vm.sourceRatio,
                outputRatio = projectRatio.takeIf { it > 0f } ?: vm.sourceRatio,
                fill = clip.orientation.scaleMode == ScaleMode.FILL,
                key = key,
                enabled = enabled,
                change = vm.transform
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (clip.keyframes.size > 1) "${clip.keyframes.size} keyframes de enquadramento"
                    else "Arraste e pince o quadro",
                    color = RiseMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                PanelIconButton(Icons.Rounded.Info, "Sobre o enquadramento") { info = !info }
            }
        }
        vm.reframe?.let { reframe ->
            val busy = vm.reframeProgress
            Button(
                onClick = reframe,
                enabled = state.busy == null && vm.sourceRatio > (projectRatio.takeIf { it > 0f } ?: vm.sourceRatio) + 0.01f,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                androidx.compose.material3.Icon(Icons.Rounded.AutoAwesome, null)
                Spacer(Modifier.width(8.dp))
                Text(if (busy != null) "Analisando… ${(busy * 100).toInt()}%" else "Reenquadrar automaticamente")
            }
        }
        AnimatedVisibility(info) {
            GraphicSurface {
                Text(
                    "Com keyframes, cada ajuste vira um ponto na posição da agulha. " +
                        "O reenquadramento automático cria esses pontos seguindo os rostos (ML Kit, no aparelho, sem rede).",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        TileGrid(
            listOf(
                Tile("Zoom", key.scale, 0.25f..4f, 1f, format = { "%.2f×".format(it) }) {
                    vm.transform(key.copy(scale = it))
                },
                Tile("Rotação", key.rotation, -180f..180f, 0f, format = { "${it.roundToInt()}°" }) {
                    vm.transform(key.copy(rotation = it))
                },
                Tile("Posição X", key.x, -2f..2f, 0f) { vm.transform(key.copy(x = it)) },
                Tile("Posição Y", key.y, -2f..2f, 0f) { vm.transform(key.copy(y = it)) }
            ),
            enabled
        )
    }
}

/**
 * Mini editor: the whole source frame with the output window (project ratio) on top; outside
 * the window is dimmed. Drag the window = position, pinch = zoom, twist = rotation. Same NDC
 * convention as ClipTransform (x = 1 → half the source width; window center = 0.5 − x/2/scale).
 */
@Composable
internal fun FrameEditor(
    sourceRatio: Float,
    outputRatio: Float,
    fill: Boolean,
    key: TransformKeyframe,
    enabled: Boolean,
    change: (TransformKeyframe) -> Unit
) {
    val current by rememberUpdatedState(key)
    val accent = MaterialTheme.colorScheme.primary
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.then(if (sourceRatio >= 1f) Modifier.fillMaxWidth() else Modifier.height(240.dp))
                .aspectRatio(sourceRatio)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF2C2C2E))
                .pointerInput(enabled, sourceRatio, outputRatio, fill) {
                    if (!enabled) return@pointerInput
                    detectTransformGestures { _, pan, zoom, rotation ->
                        val k = current
                        change(
                            k.copy(
                                x = (k.x - 2f * k.scale * pan.x / size.width).coerceIn(-2f, 2f),
                                y = (k.y + 2f * k.scale * pan.y / size.height).coerceIn(-2f, 2f),
                                scale = (k.scale * zoom).coerceIn(0.25f, 4f),
                                rotation = ((k.rotation + rotation + 540f) % 360f) - 180f
                            )
                        )
                    }
                }
                .semantics { contentDescription = "Quadro: arraste para mover, pince para zoom" }
        ) {
            // stand-in for the source picture: thirds grid
            for (i in 1..2) {
                drawLine(Color(0x44FFFFFF), Offset(size.width * i / 3, 0f), Offset(size.width * i / 3, size.height), 1f)
                drawLine(Color(0x44FFFFFF), Offset(0f, size.height * i / 3), Offset(size.width, size.height * i / 3), 1f)
            }
            val win = FrameWindow.of(sourceRatio, outputRatio, fill, key)
            val w = win[2] * size.width
            val h = win[3] * size.height
            val cx = win[0] * size.width
            val cy = win[1] * size.height
            val tl = Offset(cx - w / 2, cy - h / 2)
            rotate(-key.rotation, Offset(cx, cy)) {
                // dim outside the window (four big rects around it)
                val dim = Color(0x99000000)
                val big = size.maxDimension * 3
                drawRect(dim, Offset(tl.x - big, tl.y - big), Size(big * 2 + w, big))
                drawRect(dim, Offset(tl.x - big, tl.y + h), Size(big * 2 + w, big))
                drawRect(dim, Offset(tl.x - big, tl.y), Size(big, h))
                drawRect(dim, Offset(tl.x + w, tl.y), Size(big, h))
                drawRect(accent, tl, Size(w, h), style = Stroke(2.dp.toPx()))
                val c = 10.dp.toPx()
                listOf(tl, Offset(tl.x + w, tl.y), Offset(tl.x, tl.y + h), Offset(tl.x + w, tl.y + h)).forEach {
                    drawCircle(accent, c / 2, it)
                }
                drawCircle(accent, 4.dp.toPx(), Offset(cx, cy))
            }
        }
    }
}

/** Output window over the source frame: (centerX, centerY from top, width, height) in 0..1. */
internal object FrameWindow {
    fun of(sourceRatio: Float, outputRatio: Float, fill: Boolean, k: TransformKeyframe): FloatArray {
        val s = k.scale.coerceAtLeast(0.01f)
        val rw = outputRatio / sourceRatio
        val rh = sourceRatio / outputRatio
        val fw = (if (fill) kotlin.math.min(1f, rw) else kotlin.math.max(1f, rw)) / s
        val fh = (if (fill) kotlin.math.min(1f, rh) else kotlin.math.max(1f, rh)) / s
        return floatArrayOf(0.5f - k.x / 2f / s, 0.5f + k.y / 2f / s, fw, fh)
    }
}

@Composable
private fun BackgroundTab(
    bg: FrameBackground,
    mode: ScaleMode,
    ratio: Float,
    enabled: Boolean,
    dropdownOpenInitially: Boolean,
    infoOpenInitially: Boolean,
    set: (FrameBackground) -> Unit
) {
    var info by remember { mutableStateOf(infoOpenInitially) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RiseDropdown(
                "Fundo",
                BackgroundKind.entries.toList(),
                bg.kind,
                Modifier.weight(1f),
                expandedInitially = dropdownOpenInitially,
                text = { it.label }
            ) { set(bg.copy(kind = it)) }
            Spacer(Modifier.width(10.dp))
            PanelIconButton(Icons.Rounded.Info, "Sobre o fundo") { info = !info }
        }
        AnimatedVisibility(info || mode == ScaleMode.FILL || ratio == 0f) {
            GraphicSurface {
                Text(
                    if (mode == ScaleMode.FILL || ratio == 0f) {
                        "O fundo aparece nas barras: escolha uma proporção e “Ajustar”."
                    } else {
                        "Preenche as barras do modo Ajustar, na prévia e no export."
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        when (bg.kind) {
            BackgroundKind.COLOR -> SwatchRow(bg.color, enabled) { set(bg.copy(color = it)) }
            BackgroundKind.BLUR -> TileGrid(
                listOf(
                    Tile("Desfoque", bg.blur, 0f..1f, 0.6f, format = { "${(it * 100).roundToInt()}%" }) {
                        set(bg.copy(blur = it))
                    },
                    Tile("Escurecer", bg.dim, 0f..1f, 0.25f, format = { "${(it * 100).roundToInt()}%" }) {
                        set(bg.copy(dim = it))
                    }
                ),
                enabled
            )
            BackgroundKind.BLACK -> Unit
        }
    }
}

private val SWATCHES = listOf(
    0xFF000000, 0xFFFFFFFF, 0xFF1C1C1E, 0xFFFF3B30, 0xFFFF9500,
    0xFFFFCC00, 0xFF34C759, 0xFF0A84FF, 0xFF5856D6, 0xFFFF2D55
).map { it.toInt() }

@Composable
private fun SwatchRow(selected: Int, enabled: Boolean, set: (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cols = (maxWidth / 48.dp).toInt().coerceIn(5, SWATCHES.size)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SWATCHES.chunked(cols).forEach { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    line.forEach { c ->
                        Box(
                            Modifier.size(38.dp).clip(CircleShape).background(Color(c))
                                .border(
                                    if (c == selected) 3.dp else 1.dp,
                                    if (c == selected) MaterialTheme.colorScheme.primary else Color(0x55FFFFFF),
                                    CircleShape
                                )
                                .clickable(enabled = enabled) { set(c) }
                                .semantics { contentDescription = "Cor #%06X".format(c and 0xFFFFFF) }
                        )
                    }
                }
            }
        }
    }
}
