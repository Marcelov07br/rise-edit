package br.com.riseedit.tools

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.FastRewind
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
import br.com.riseedit.core.editing.SpeedCurve
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
enum class CutTab(val label: String) { CUT("Corte"), SPEED("Velocidade"), CURVE("Curva"), FADE("Fade") }

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
    val reset: (CutTab) -> Unit = {},
    // 0.21
    val freeze: () -> Unit = {},
    val replace: () -> Unit = {},
    val reverse: () -> Unit = {},
    val curve: (List<SpeedCurve.Knot>) -> Unit = {}
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
            freeze = { vm.freezeFrame(FREEZE_MS) },
            replace = { vm.replacePicker.value = true },
            reverse = vm::reverseClip,
            curve = vm::speedCurve,
            fade = { vm.clipSettings(clip.copy(fadeMs = it)) },
            reset = {
                when (it) {
                    CutTab.CUT -> vm.trimClip(clip, clip.trim(0, clip.sourceDurationMs))
                    CutTab.SPEED, CutTab.CURVE -> vm.speed(1.0, true)
                    CutTab.FADE -> vm.clipSettings(clip.copy(fadeMs = 0))
                }
            }
        ),
        thumbnail = { ms ->
            runCatching { MediaAssets.thumbnail(context, clip.effectiveUri(true), ms) }
                .getOrNull()?.asImageBitmap()
        },
        fps = p.export.fps
    )
}

private fun canReset(clip: Clip, tab: CutTab) = when (tab) {
    CutTab.CUT -> clip.startMs > 0 || clip.endMs < clip.sourceDurationMs
    CutTab.SPEED -> clip.speedPoints.isNotEmpty() || !clip.keepPitch
    CutTab.CURVE -> clip.speedPoints.size > 1
    CutTab.FADE -> clip.fadeMs > 0
}

/** Freeze-frame length; the still is rendered longer so it can be extended with the handles. */
internal const val FREEZE_MS = 2_000L

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
        Icons.Rounded.AcUnit,
        "Congelar quadro no cursor",
        enabled,
        onClick = actions.freeze
    ),
    PanelAction(Icons.Rounded.SwapHoriz, "Substituir mídia", enabled, onClick = actions.replace),
    PanelAction(
        Icons.Rounded.FastRewind,
        "Inverter clipe",
        enabled && clip.sourceLengthMs <= br.com.riseedit.media.CutMedia.MAX_REVERSE_MS,
        onClick = actions.reverse
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
    cached: (Long) -> ImageBitmap? = { null },
    fps: Int = 30,
    loupeInitially: TrimEdge? = null
) {
    val inside = cursorMs > 0 && cursorMs < clip.durationMs
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ActionBar(cutActionList(clip, tab, enabled, inside, actions))
        SegmentedTabs(CutTab.entries, tab, label = { it.label }, marked = {
            canReset(clip, it)
        }, onSelect = onTab)
        when (tab) {
            CutTab.CUT -> CutTabBody(clip, cursorMs, enabled, actions, thumbnail, cached, fps, loupeInitially)
            CutTab.SPEED -> SpeedTabBody(clip, enabled, actions)
            CutTab.CURVE -> CurveTabBody(clip, enabled, actions)
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
    cached: (Long) -> ImageBitmap?,
    fps: Int,
    loupeInitially: TrimEdge?
) {
    var range by remember(clip.id, clip.startMs, clip.endMs) {
        mutableStateOf(clip.startMs..clip.endMs)
    }
    var grabbed by remember { mutableStateOf(loupeInitially) }
    val cursorSource = if (cursorMs in 0..clip.durationMs) clip.sourceAt(cursorMs) else null
    GraphicSurface {
        androidx.compose.animation.AnimatedVisibility(
            grabbed != null,
            enter = androidx.compose.animation.fadeIn(riseSpring()) +
                androidx.compose.animation.expandVertically(riseSpring()),
            exit = androidx.compose.animation.fadeOut(riseSpring()) +
                androidx.compose.animation.shrinkVertically(riseSpring())
        ) {
            val edge = grabbed ?: TrimEdge.START
            TimeLoupe(
                if (edge == TrimEdge.START) range.first else range.last,
                edge,
                clip.sourceDurationMs,
                fps,
                thumbnail,
                cached
            )
        }
        TrimStrip(
            onGrab = { grabbed = it },
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
    cached: (Long) -> ImageBitmap? = { null },
    onGrab: (TrimEdge?) -> Unit = {}
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
                        onGrab(grabbed)
                        if (grabbed !=
                            null
                        ) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    },
                    onDragEnd = {
                        if (grabbed != null) onCommit(current)
                        grabbed = null
                        onGrab(null)
                    },
                    onDragCancel = {
                        grabbed = null
                        onGrab(null)
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

private const val LOUPE_SPAN_MS = 1_500L

/**
 * 0.21 "Lupa de tempo": while a trim handle is held, a ±1.5 s magnified ruler with one tick per
 * frame, the handle's frame and its timecode, so frame-exact cuts are possible on a phone.
 */
@Composable
fun TimeLoupe(
    edgeMs: Long,
    edge: TrimEdge,
    totalMs: Long,
    fps: Int,
    thumbnail: suspend (Long) -> ImageBitmap?,
    cached: (Long) -> ImageBitmap? = { null }
) {
    val frameMs = 1000f / fps.coerceAtLeast(1)
    val frame = (edgeMs / frameMs).toLong()
    val img by androidx.compose.runtime.produceState(cached(edgeMs), edgeMs / 100) {
        thumbnail(edgeMs)?.let { value = it }
    }
    Row(
        Modifier.fillMaxWidth().semantics { contentDescription = "Lupa de tempo" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.width(72.dp).height(48.dp).clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF1B1B1F))
        ) {
            img?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row {
                Mono(if (edge == TrimEdge.START) "Início" else "Fim")
                Spacer(Modifier.weight(1f))
                Mono("${time(edgeMs)} · quadro $frame", color = RiseWhite)
            }
            Canvas(Modifier.fillMaxWidth().height(28.dp)) {
                val pxPerMs = size.width / (2 * LOUPE_SPAN_MS)
                val first = ((edgeMs - LOUPE_SPAN_MS) / frameMs).toLong()
                val last = ((edgeMs + LOUPE_SPAN_MS) / frameMs).toLong()
                val outside = Color.Black.copy(alpha = 0.55f)
                for (f in first..last) {
                    val ms = f * frameMs
                    val x = (ms - edgeMs) * pxPerMs + size.width / 2
                    val valid = ms >= 0 && ms <= totalMs
                    val major = f % (fps.coerceAtLeast(1) / 2).coerceAtLeast(1) == 0L
                    val h = if (major) size.height * 0.7f else size.height * 0.35f
                    drawLine(
                        if (valid) RiseMuted else outside,
                        Offset(x, size.height - h),
                        Offset(x, size.height),
                        if (major) 1.5.dp.toPx() else 1.dp.toPx()
                    )
                }
                drawLine(Color.White, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 2.dp.toPx())
            }
        }
    }
}

private fun speedY(speed: Double, h: Float) = (1f - (log10(speed).toFloat() + 1f) / 2f) * h
private fun ySpeed(y: Float, h: Float) =
    10.0.pow((1f - y / h) * 2f - 1.0).coerceIn(br.com.riseedit.core.MIN_SPEED, br.com.riseedit.core.MAX_SPEED)

/**
 * 0.21 "Curva": editable speed curve (drag the points up/down; log scale 0.1×–10×, 1× in the
 * middle) + presets. Applied on release, baked into step points (see [SpeedCurve]).
 */
@Composable
private fun CurveTabBody(clip: Clip, enabled: Boolean, actions: CutActions) {
    var knots by remember(clip.id, clip.speedPoints) { mutableStateOf(SpeedCurve.knotsOf(clip)) }
    val current by rememberUpdatedState(knots)
    val preset = SpeedCurve.Preset.entries.firstOrNull {
        SpeedCurve.bake(clip, it.knots) == clip.speedPoints
    }
    var dragged by remember { mutableStateOf(-1) }
    val haptic = LocalHapticFeedback.current
    GraphicSurface {
        Canvas(
            Modifier.fillMaxWidth().height(150.dp).semantics { contentDescription = "Curva de velocidade" }
                .pointerInput(enabled, clip.id) {
                    if (!enabled) return@pointerInput
                    detectDragGestures(
                        onDragStart = { o ->
                            val k = current
                            dragged = k.indices.minBy { abs(k[it].x * size.width - o.x) }
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        onDragEnd = { if (dragged >= 0) actions.curve(current); dragged = -1 },
                        onDragCancel = { dragged = -1 }
                    ) { change, _ ->
                        val i = dragged.takeIf { it >= 0 } ?: return@detectDragGestures
                        change.consume()
                        val y = change.position.y.coerceIn(0f, size.height.toFloat())
                        val v = (ySpeed(y, size.height.toFloat()) * 100).toLong() / 100.0
                        knots = current.toMutableList().also { it[i] = it[i].copy(speed = v) }
                    }
                }
        ) {
            val h = size.height
            val grid = RiseMuted.copy(alpha = 0.25f)
            listOf(0.25, 0.5, 2.0, 4.0).forEach {
                drawLine(grid, Offset(0f, speedY(it, h)), Offset(size.width, speedY(it, h)), 1.dp.toPx())
            }
            drawLine(RiseMuted.copy(alpha = 0.6f), Offset(0f, h / 2), Offset(size.width, h / 2), 1.dp.toPx())
            val path = androidx.compose.ui.graphics.Path()
            val n = 64
            for (i in 0..n) {
                val x = i / n.toFloat()
                val y = speedY(SpeedCurve.at(knots, x), h)
                if (i == 0) path.moveTo(0f, y) else path.lineTo(x * size.width, y)
            }
            drawPath(path, Color.White, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
            knots.forEachIndexed { i, k ->
                val c = Offset(k.x * size.width, speedY(k.speed, h))
                drawCircle(if (i == dragged) Color(0xFFFFD54F) else Color.White, 7.dp.toPx(), c)
                drawCircle(Color.Black, 3.dp.toPx(), c)
            }
        }
        Row {
            Mono("↑ até 10×"); Spacer(Modifier.weight(1f)); Mono("linha central = 1×"); Spacer(Modifier.weight(1f)); Mono("↓ até 0,1×")
        }
    }
    Mono("Duração no projeto: ${time(clip.durationMs)}")
    RiseDropdown(
        "Predefinição",
        SpeedCurve.Preset.entries,
        preset ?: SpeedCurve.Preset.CONSTANT,
        text = { it.label }
    ) { p ->
        knots = p.knots
        actions.curve(p.knots)
    }
}
