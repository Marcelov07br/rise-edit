package br.com.riseedit.tools

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.ControlPointDuplicate
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FirstPage
import androidx.compose.material.icons.rounded.LastPage
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.riseedit.EditorState
import br.com.riseedit.EditorViewModel
import br.com.riseedit.MediaAssets
import br.com.riseedit.RiseMuted
import br.com.riseedit.RiseWhite
import br.com.riseedit.core.Clip
import br.com.riseedit.core.editing.ClipEdits
import br.com.riseedit.core.editing.TrimEdge
import br.com.riseedit.designsystem.ActionBar
import br.com.riseedit.designsystem.GraphicSurface
import br.com.riseedit.designsystem.PanelAction
import br.com.riseedit.designsystem.PanelIconButton
import br.com.riseedit.designsystem.RiseDropdown
import br.com.riseedit.designsystem.SegmentedTabs
import br.com.riseedit.riseSpring
import br.com.riseedit.time
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow

/** Tabs of the "Corte" tool (0.18). */
enum class CutTab(val label: String) { CUT("Corte"), SPEED("Velocidade"), FADE("Fade") }

/** Everything the cut panel can do; [TrimPanel] wires it to the view model, shots to no-ops. */
class CutActions(
    val split: () -> Unit = {},
    val duplicate: () -> Unit = {},
    val cutLeft: () -> Unit = {},
    val cutRight: () -> Unit = {},
    val moveLeft: (() -> Unit)? = null,
    val moveRight: (() -> Unit)? = null,
    val delete: () -> Unit = {},
    val ripple: Boolean = false,
    val toggleRipple: () -> Unit = {},
    val trim: (Long, Long) -> Unit = { _, _ -> },
    val nudge: (TrimEdge, Int) -> Unit = { _, _ -> },
    val speed: (Double, Boolean) -> Unit = { _, _ -> },
    val fade: (Long) -> Unit = {},
    val reset: (CutTab) -> Unit = {}
)

internal val speedPresets = listOf(0.1, 0.25, 0.5, 1.0, 1.5, 2.0, 3.0, 5.0, 10.0)
internal fun speedText(s: Double) =
    String.format(java.util.Locale.US, "%.2f", s).trimEnd('0').trimEnd('.') + "×"

@Composable
internal fun TrimPanel(vm: EditorViewModel, state: EditorState, initial: CutTab = CutTab.CUT) {
    val p = state.project
    val clip = p.clips.getOrNull(state.selected) ?: return
    val start = p.clips.take(state.selected).sumOf { it.durationMs }
    val enabled = state.busy == null
    var tab by rememberSaveable(initial) { mutableStateOf(initial) }
    val context = LocalContext.current
    CutPanel(
        clip = clip,
        cursorMs = state.positionMs - start,
        tab = tab,
        onTab = { tab = it },
        enabled = enabled,
        actions = CutActions(
            split = vm::split,
            duplicate = vm::duplicate,
            cutLeft = { vm.cutToCursor(left = true) },
            cutRight = { vm.cutToCursor(left = false) },
            moveLeft = { vm.move(-1) }.takeIf { state.selected > 0 },
            moveRight = { vm.move(1) }.takeIf { state.selected < p.clips.lastIndex },
            delete = vm::deleteSelected,
            ripple = vm.rippleMode,
            toggleRipple = { vm.rippleMode = !vm.rippleMode },
            trim = { s, e -> vm.trimClip(clip, clip.trim(s, e)) },
            nudge = vm::nudge,
            speed = vm::speed,
            fade = { vm.clipSettings(clip.copy(fadeMs = it)) },
            reset = {
                when (it) {
                    CutTab.CUT -> vm.trimClip(clip, clip.trim(0, clip.sourceDurationMs))
                    CutTab.SPEED -> vm.speed(1.0, true)
                    CutTab.FADE -> vm.clipSettings(clip.copy(fadeMs = 0))
                }
            }
        ),
        thumbnail = { ms ->
            runCatching { MediaAssets.thumbnail(context, clip.effectiveUri(true), ms) }
                .getOrNull()?.asImageBitmap()
        }
    )
}

private fun canReset(clip: Clip, tab: CutTab) = when (tab) {
    CutTab.CUT -> clip.startMs > 0 || clip.endMs < clip.sourceDurationMs
    CutTab.SPEED -> clip.speedPoints.isNotEmpty() || !clip.keepPitch
    CutTab.FADE -> clip.fadeMs > 0
}

/** Icon actions of the cut panel (inline vs "mais" is decided by [ActionBar]). */
fun cutActionList(
    clip: Clip,
    tab: CutTab,
    enabled: Boolean,
    inside: Boolean,
    actions: CutActions
): List<PanelAction> = listOf(
    PanelAction(
        Icons.Rounded.ContentCut,
        "Dividir no cursor",
        enabled && inside,
        primary = true,
        onClick = actions.split
    ),
    PanelAction(
        Icons.Rounded.ControlPointDuplicate,
        "Duplicar",
        enabled,
        primary = true,
        onClick = actions.duplicate
    ),
    PanelAction(
        Icons.Rounded.FirstPage,
        "Cortar início até o cursor",
        enabled && inside,
        onClick = actions.cutLeft
    ),
    PanelAction(
        Icons.Rounded.LastPage,
        "Cortar do cursor ao fim",
        enabled && inside,
        onClick = actions.cutRight
    ),
    PanelAction(
        Icons.AutoMirrored.Rounded.ArrowBack,
        "Mover para trás",
        enabled && actions.moveLeft != null
    ) { actions.moveLeft?.invoke() },
    PanelAction(
        Icons.AutoMirrored.Rounded.ArrowForward,
        "Mover para frente",
        enabled && actions.moveRight != null
    ) { actions.moveRight?.invoke() },
    PanelAction(
        Icons.Rounded.DeleteSweep,
        if (actions.ripple) "Ripple ligado (fecha espaços)" else "Ripple desligado",
        enabled,
        onClick = actions.toggleRipple
    ),
    PanelAction(
        Icons.Rounded.RestartAlt,
        "Resetar ${tab.label.lowercase()}",
        enabled && canReset(clip, tab),
        danger = true
    ) { actions.reset(tab) },
    PanelAction(
        Icons.Rounded.Delete,
        "Excluir clipe",
        enabled,
        danger = true,
        onClick = actions.delete
    )
)

/** Stateless cut panel: action bar (icons + "mais"), pill tabs, then the tab body. */
@Composable
fun CutPanel(
    clip: Clip,
    cursorMs: Long,
    tab: CutTab,
    onTab: (CutTab) -> Unit,
    actions: CutActions,
    enabled: Boolean = true,
    thumbnail: suspend (Long) -> ImageBitmap? = { null },
    cached: (Long) -> ImageBitmap? = { null }
) {
    val inside = cursorMs > 0 && cursorMs < clip.durationMs
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ActionBar(cutActionList(clip, tab, enabled, inside, actions))
        SegmentedTabs(CutTab.entries, tab, label = { it.label }, marked = {
            canReset(clip, it)
        }, onSelect = onTab)
        when (tab) {
            CutTab.CUT -> CutTabBody(clip, cursorMs, enabled, actions, thumbnail, cached)
            CutTab.SPEED -> SpeedTabBody(clip, enabled, actions)
            CutTab.FADE -> FadeTabBody(clip, enabled, actions)
        }
    }
}

@Composable
private fun CutTabBody(
    clip: Clip,
    cursorMs: Long,
    enabled: Boolean,
    actions: CutActions,
    thumbnail: suspend (Long) -> ImageBitmap?,
    cached: (Long) -> ImageBitmap?
) {
    var range by remember(clip.id, clip.startMs, clip.endMs) {
        mutableStateOf(clip.startMs..clip.endMs)
    }
    val cursorSource = if (cursorMs in 0..clip.durationMs) clip.sourceAt(cursorMs) else null
    GraphicSurface {
        TrimStrip(
            clip = clip,
            range = range,
            cursorSourceMs = cursorSource,
            enabled = enabled,
            onDrag = { range = it },
            onCommit = { actions.trim(it.first, it.last) },
            thumbnail = thumbnail,
            cached = cached
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            EdgeNudge(TrimEdge.START, enabled, actions)
            Mono(time(range.first), Modifier.padding(start = 6.dp))
            Spacer(Modifier.weight(1f))
            Mono(time(clip.trim(range.first, range.last).durationMs), color = RiseWhite)
            Spacer(Modifier.weight(1f))
            Mono(time(range.last), Modifier.padding(end = 6.dp))
            EdgeNudge(TrimEdge.END, enabled, actions)
        }
    }
}

@Composable
private fun EdgeNudge(edge: TrimEdge, enabled: Boolean, actions: CutActions) {
    val which = if (edge == TrimEdge.START) "início" else "fim"
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        PanelIconButton(
            Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
            "$which: −1 quadro",
            enabled = enabled
        ) {
            actions.nudge(edge, -1)
        }
        PanelIconButton(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            "$which: +1 quadro",
            enabled = enabled
        ) {
            actions.nudge(edge, 1)
        }
    }
}

@Composable
private fun Mono(text: String, modifier: Modifier = Modifier, color: Color = RiseMuted) =
    Text(text, modifier, color = color, fontFamily = FontFamily.Monospace, fontSize = 12.sp)

private const val HANDLE_TOUCH_DP = 32
private const val SNAP_DP = 8

/**
 * Film strip of the whole source with the kept range framed by two thick handles (touch ≥ 32dp).
 * Drag a handle; snaps to the cursor with a light haptic; the cut is applied on release.
 */
@Composable
// detekt: one gesture + draw component, like TileSlider.
@Suppress("LongMethod", "CyclomaticComplexMethod")
fun TrimStrip(
    clip: Clip,
    range: LongRange,
    cursorSourceMs: Long?,
    enabled: Boolean,
    onDrag: (LongRange) -> Unit,
    onCommit: (LongRange) -> Unit,
    thumbnail: suspend (Long) -> ImageBitmap?,
    cached: (Long) -> ImageBitmap? = { null }
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val total = clip.sourceDurationMs.toFloat()
    val current by rememberUpdatedState(range)
    val cursor by rememberUpdatedState(cursorSourceMs)
    var grabbed by remember { mutableStateOf<TrimEdge?>(null) }
    val grow by animateFloatAsState(if (grabbed != null) 1f else 0f, riseSpring(), label = "grab")
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(60.dp).semantics { contentDescription = "Tira de corte" }
    ) {
        val count = (maxWidth / 44.dp).toInt().coerceIn(4, 16)
        Row(Modifier.fillMaxSize().padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp))) {
            repeat(count) { i ->
                val ms = ((i + 0.5f) / count * total).toLong()
                val img by androidx.compose.runtime.produceState(cached(ms), clip.uri, ms) {
                    thumbnail(ms)?.let { value = it }
                }
                Box(Modifier.weight(1f).fillMaxHeight().background(Color(0xFF1B1B1F))) {
                    img?.let {
                        Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                }
            }
        }
        Canvas(
            Modifier.fillMaxSize().pointerInput(enabled, clip.id) {
                if (!enabled) return@pointerInput
                val touch = HANDLE_TOUCH_DP * density.density
                val snap = SNAP_DP * density.density
                fun x(ms: Long) = ms / total * size.width
                var snapped = false
                detectHorizontalDragGestures(
                    onDragStart = { o ->
                        val ds = abs(o.x - x(current.first))
                        val de = abs(o.x - x(current.last))
                        grabbed = when {
                            ds <= touch && ds <= de -> TrimEdge.START
                            de <= touch -> TrimEdge.END
                            else -> null
                        }
                        if (grabbed !=
                            null
                        ) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    },
                    onDragEnd = {
                        if (grabbed != null) onCommit(current)
                        grabbed = null
                    },
                    onDragCancel = {
                        grabbed = null
                        onDrag(clip.startMs..clip.endMs)
                    }
                ) { change, dx ->
                    val edge = grabbed ?: return@detectHorizontalDragGestures
                    change.consume()
                    val r = current
                    val px = (if (edge == TrimEdge.START) x(r.first) else x(r.last)) + dx
                    var ms = (px / size.width * total).toLong()
                    val c = cursor
                    val near = c != null && abs(px - x(c)) < snap
                    if (near && c != null) {
                        ms = c
                        if (!snapped) {
                            haptic.performHapticFeedback(
                                HapticFeedbackType.TextHandleMove
                            )
                        }
                    }
                    snapped = near
                    val next = if (edge == TrimEdge.START) {
                        ms.coerceIn(0, r.last - ClipEdits.MIN_MS)..r.last
                    } else {
                        r.first..ms.coerceIn(r.first + ClipEdits.MIN_MS, clip.sourceDurationMs)
                    }
                    onDrag(next)
                }
            }
        ) {
            val s = range.first / total * size.width
            val e = range.last / total * size.width
            val top = 4.dp.toPx()
            val h = size.height - 2 * top
            val shade = Color.Black.copy(alpha = 0.62f)
            drawRect(shade, Offset(0f, top), Size(s, h))
            drawRect(shade, Offset(e, top), Size(size.width - e, h))
            val w = 12.dp.toPx() + 4.dp.toPx() * grow
            val frame = Color.White
            drawRect(frame, Offset(s, top), Size(e - s, 2.dp.toPx()))
            drawRect(frame, Offset(s, size.height - top - 2.dp.toPx()), Size(e - s, 2.dp.toPx()))
            val r = CornerRadius(4.dp.toPx())
            drawRoundRect(frame, Offset(s - w / 2, 0f), Size(w, size.height), r)
            drawRoundRect(frame, Offset(e - w / 2, 0f), Size(w, size.height), r)
            val grip = Color.Black.copy(alpha = 0.55f)
            listOf(s, e).forEach { cx ->
                drawLine(
                    grip,
                    Offset(cx, size.height * 0.35f),
                    Offset(cx, size.height * 0.65f),
                    2.dp.toPx()
                )
            }
            cursorSourceMs?.let {
                val cx = it / total * size.width
                drawLine(Color(0xFFFFD54F), Offset(cx, 0f), Offset(cx, size.height), 2.dp.toPx())
            }
        }
    }
}

/** Log mapping so 0.1×, 1× and 10× sit at the left, center and right of the tile. */
private fun toTile(speed: Double) = log10(speed).toFloat()
private fun fromTile(v: Float) = 10.0.pow(v.toDouble())

@Composable
private fun SpeedTabBody(clip: Clip, enabled: Boolean, actions: CutActions) {
    val speed = clip.speedAt(clip.startMs)
    val preset = speedPresets.minBy { abs(it - speed) }.takeIf { abs(it - speed) < 0.005 }
    TileGrid(
        listOf(
            Tile("Velocidade", toTile(speed), -1f..1f, default = 0f, format = {
                speedText(fromTile(it))
            }) {
                actions.speed(fromTile(it), clip.keepPitch)
            }
        ),
        enabled
    )
    Mono("Duração no projeto: ${time(clip.durationMs)}")
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RiseDropdown(
            "Predefinição",
            speedPresets + listOfNotNull(speed.takeIf { preset == null }),
            preset ?: speed,
            Modifier.weight(1f),
            text = ::speedText
        ) { actions.speed(it, clip.keepPitch) }
        RiseDropdown(
            "Tom do áudio",
            listOf(true, false),
            clip.keepPitch,
            Modifier.weight(1f),
            text = { if (it) "Manter tom" else "Acompanhar" }
        ) { actions.speed(speed, it) }
    }
}

private val fadeChoices = listOf(0L, 250L, 500L, 1000L, 2000L)

@Composable
private fun FadeTabBody(clip: Clip, enabled: Boolean, actions: CutActions) {
    val max = (clip.durationMs / 2).coerceIn(100, 3000).toFloat()
    TileGrid(
        listOf(
            Tile(
                "Fade (entrada e saída)",
                clip.fadeMs.toFloat().coerceAtMost(
                    max
                ),
                0f..max,
                default = 0f,
                format = {
                    if (it < 1f) "Seco" else "${it.toLong()} ms"
                }
            ) { actions.fade(it.toLong()) }
        ),
        enabled
    )
    RiseDropdown(
        "Tipo",
        fadeChoices.filter { it <= max },
        fadeChoices.minBy { abs(it - clip.fadeMs) },
        text = { if (it == 0L) "Corte seco" else "Fade preto · $it ms" }
    ) { actions.fade(it) }
}
