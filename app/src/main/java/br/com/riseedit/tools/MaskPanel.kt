package br.com.riseedit.tools

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BlurCircular
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Gradient
import androidx.compose.material.icons.rounded.InvertColors
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.riseedit.EditorState
import br.com.riseedit.EditorViewModel
import br.com.riseedit.RiseMuted
import br.com.riseedit.RiseSurfaceHigh
import br.com.riseedit.RiseWhite
import br.com.riseedit.core.Selection
import br.com.riseedit.core.color.Qualifier
import br.com.riseedit.core.mask.Mask
import br.com.riseedit.core.mask.MaskGeom
import br.com.riseedit.core.mask.MaskOp
import br.com.riseedit.core.mask.MaskSet
import br.com.riseedit.core.mask.MaskType
import br.com.riseedit.designsystem.ActionBar
import br.com.riseedit.designsystem.ChannelChips
import br.com.riseedit.designsystem.PanelAction
import br.com.riseedit.designsystem.PanelIconButton
import br.com.riseedit.designsystem.TileSlider

internal const val MASK_TAG = "RiseMask"

/** What the Máscara tool edits. Mask key times are in [timeMs]'s domain (see [MaskRef.timeMs]). */
internal enum class MaskOwner(val label: String, val effect: String) {
    CLIP("Clipe", "cor do take só dentro da máscara"),
    LAYER("Camada de ajuste", "ajuste só dentro da máscara"),
    OVERLAY("Overlay", "máscara vira a transparência do overlay")
}

/**
 * Resolved target: [timeMs] = owner time at the playhead (clip/overlay clip SOURCE time,
 * layer-local time) — the same domain the renderers evaluate keys in.
 */
internal class MaskRef(
    val owner: MaskOwner,
    val id: String,
    val name: String,
    val masks: MaskSet,
    val timeMs: Long
)

internal fun maskTarget(state: EditorState): MaskRef? {
    val p = state.project
    when (val sel = state.selection) {
        is Selection.Adjustment -> {
            val l = p.adjustmentLayers.firstOrNull { it.id == sel.id } ?: return null
            return MaskRef(
                MaskOwner.LAYER,
                l.id,
                l.name,
                l.masks,
                (state.positionMs - l.startMs).coerceIn(0, l.durationMs)
            )
        }
        is Selection.Overlay -> {
            val o = p.overlays.firstOrNull { it.id == sel.id } ?: return null
            val local = (state.positionMs - o.offsetMs).coerceIn(0, o.clip.durationMs)
            return MaskRef(
                MaskOwner.OVERLAY,
                o.id,
                o.clip.name,
                o.clip.masks,
                o.clip.startMs + local
            )
        }
        else -> {
            val t = colorTarget(state) ?: return null
            if (t.layer != null) return null
            return MaskRef(
                MaskOwner.CLIP,
                t.clip.id,
                t.clip.name,
                t.clip.masks,
                t.sourceMs(state.positionMs)
            )
        }
    }
}

/** Writes [set] back to the owner (one history step per call, like every other panel). */
internal fun EditorViewModel.commitMasks(ref: MaskRef, set: MaskSet) {
    val p = state.value.project
    when (ref.owner) {
        MaskOwner.LAYER -> p.adjustmentLayers.firstOrNull {
            it.id == ref.id
        }?.let { adjustmentLayer(it.copy(masks = set)) }
        MaskOwner.OVERLAY -> p.overlays.firstOrNull {
            it.id == ref.id
        }?.let { overlay(it.copy(clip = it.clip.copy(masks = set))) }
        MaskOwner.CLIP -> p.clips.firstOrNull {
            it.id == ref.id
        }?.let { clipSettings(it.copy(masks = set)) }
    }
}

/**
 * App-wide state of the Máscara tool for the preview (handles) and the renderers ("ver máscara").
 * Set by [MaskPanel] while open; cleared on close.
 */
internal object MaskLive {
    var owner by mutableStateOf<MaskOwner?>(null)
    var ownerId by mutableStateOf<String?>(null)

    /** Masks evaluated at the playhead (what the handles draw). */
    var shown by mutableStateOf<List<Mask>>(emptyList())
    var selectedId by mutableStateOf<String?>(null)
    var view by mutableStateOf(false)
    var onGeom: ((String, MaskGeom) -> Unit)? = null
    var clipboard by mutableStateOf<MaskSet?>(null)

    fun setView(on: Boolean, player: androidx.media3.common.Player?) {
        view = on
        val o = owner
        br.com.riseedit.color.ColorLive.maskViewClip = on && o == MaskOwner.CLIP
        br.com.riseedit.color.ColorLive.maskViewLayer =
            if (on && o == MaskOwner.LAYER) ownerId else null
        br.com.riseedit.color.ColorLive.tick.intValue++
        Log.i(MASK_TAG, "mask view=$on owner=$o")
        br.com.riseedit.redrawColor(player)
    }

    fun clear() {
        owner = null
        ownerId = null
        shown = emptyList()
        onGeom = null
        if (view) {
            view = false
            br.com.riseedit.color.ColorLive.maskViewClip = false
            br.com.riseedit.color.ColorLive.maskViewLayer = null
        }
    }
}

internal fun MaskType.icon(): ImageVector = when (this) {
    MaskType.RECT -> Icons.Rounded.CropSquare
    MaskType.ELLIPSE -> Icons.Rounded.RadioButtonUnchecked
    MaskType.LINEAR -> Icons.Rounded.Gradient
    MaskType.RADIAL -> Icons.Rounded.BlurCircular
    MaskType.COLOR -> Icons.Rounded.Palette
}

/** Callbacks of the stateless [MaskContent] (Paparazzi passes no-ops). */
internal class MaskUi(
    val onAdd: (MaskType) -> Unit = {},
    val onSelect: (String) -> Unit = {},
    val onChange: (Mask) -> Unit = {},
    val onGeom: (Mask, MaskGeom) -> Unit = { _, _ -> },
    val onDelete: (String) -> Unit = {},
    val onToggleKey: (Mask) -> Unit = {},
    val onView: () -> Unit = {},
    val onCopy: () -> Unit = {},
    val onPaste: (() -> Unit)? = null,
    val onEyedropper: (() -> Unit)? = null,
    val picking: Boolean = false
)

@Composable
internal fun MaskPanel(vm: EditorViewModel, state: EditorState) {
    DisposableEffect(Unit) { onDispose { MaskLive.clear() } }
    val ref = maskTarget(state)
    if (ref == null) {
        Text(
            "Selecione um clipe, overlay ou camada de ajuste para usar máscaras.",
            color = RiseMuted,
            fontSize = 13.sp
        )
        return
    }
    val selected =
        ref.masks.masks.firstOrNull { it.id == MaskLive.selectedId }
            ?: ref.masks.masks.firstOrNull()
    SideEffect { MaskLive.bind(vm, ref, selected?.id) }
    MaskContent(
        ref.owner,
        ref.name,
        ref.masks,
        selected?.id,
        ref.timeMs,
        MaskLive.view,
        maskUi(vm, ref)
    )
}

/** Latest target with the same owner id (async callbacks must not write a stale copy). */
private fun EditorViewModel.latestMasks(ref: MaskRef) =
    maskTarget(state.value)?.takeIf { it.id == ref.id } ?: ref

private fun MaskLive.bind(vm: EditorViewModel, ref: MaskRef, selected: String?) {
    if (ownerId != ref.id && view) setView(false, vm.player)
    owner = ref.owner
    ownerId = ref.id
    shown = ref.masks.at(ref.timeMs)
    selectedId = selected
    onGeom = { id, g ->
        val r = vm.latestMasks(ref)
        r.masks.masks.firstOrNull {
            it.id == id
        }?.let { vm.commitMasks(r, r.masks.replace(it.withGeomAt(r.timeMs, g))) }
    }
}

private fun maskUi(vm: EditorViewModel, ref: MaskRef): MaskUi {
    fun r() = vm.latestMasks(ref)
    fun commit(set: MaskSet) = vm.commitMasks(r(), set)
    return MaskUi(
        onAdd = { type ->
            val m = Mask.new(type)
            Log.i(
                MASK_TAG,
                "add ${type.id} to ${ref.owner} ${ref.id.take(
                    6
                )} (${r().masks.masks.size + 1}/${MaskSet.MAX})"
            )
            commit(r().masks.add(m))
            MaskLive.selectedId = m.id
        },
        onSelect = { MaskLive.selectedId = it },
        onChange = { m -> commit(r().masks.replace(m)) },
        onGeom = { m, g ->
            val t = r()
            commit(t.masks.replace(m.withGeomAt(t.timeMs, g)))
        },
        onDelete = { id ->
            commit(r().masks.remove(id))
            Log.i(MASK_TAG, "remove ${id.take(6)}")
        },
        onToggleKey = { m ->
            val t = r()
            val out = m.toggleKey(t.timeMs)
            Log.i(MASK_TAG, "key toggle ${m.id.take(6)} at ${t.timeMs}ms -> ${out.keys.size} keys")
            commit(t.masks.replace(out))
        },
        onView = { MaskLive.setView(!MaskLive.view, vm.player) },
        onCopy = {
            MaskLive.clipboard = r().masks
            vm.message("Máscaras copiadas")
        },
        onPaste = MaskLive.clipboard?.let { clip ->
            {
                Log.i(MASK_TAG, "paste ${clip.masks.size} mask(s) into ${ref.owner}")
                commit(MaskSet((r().masks.masks + clip.withNewIds().masks).take(MaskSet.MAX)))
            }
        },
        onEyedropper = if (ref.owner ==
            MaskOwner.OVERLAY
        ) {
            null
        } else {
            { toggleEyedropper(vm, ref) }
        },
        picking = ColorTools.eyedropper != null
    )
}

private fun toggleEyedropper(vm: EditorViewModel, ref: MaskRef) {
    ColorTools.eyedropper = if (ColorTools.eyedropper != null) {
        null
    } else {
        pick@{ argb ->
            ColorTools.eyedropper = null
            val r = vm.latestMasks(ref)
            val m = r.masks.masks.firstOrNull { it.id == MaskLive.selectedId } ?: return@pick
            val rgb = floatArrayOf(
                ((argb shr 16) and 255) / 255f,
                ((argb shr 8) and 255) / 255f,
                (argb and 255) / 255f
            )
            Log.i(MASK_TAG, "color mask picked #%06X".format(argb and 0xFFFFFF))
            vm.commitMasks(r, r.masks.replace(m.copy(qualifier = Qualifier.pick(m.qualifier, rgb))))
        }
    }
}

/** Stateless Máscara panel: header + actions, add row, combined list, selected mask controls. */
@Composable
internal fun MaskContent(
    owner: MaskOwner,
    name: String,
    set: MaskSet,
    selectedId: String?,
    timeMs: Long,
    viewing: Boolean,
    ui: MaskUi
) {
    val selected = set.masks.firstOrNull { it.id == selectedId }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MaskHeader(owner, name, set, selected, timeMs, viewing, ui)
        AddRow(set.masks.size < MaskSet.MAX, ui.onAdd)
        if (set.masks.isEmpty()) {
            Text(EMPTY_HINT, color = RiseMuted, fontSize = 12.sp)
        } else {
            MaskList(set, selectedId, ui)
            selected?.let { MaskControls(it, set.masks.firstOrNull()?.id == it.id, timeMs, ui) }
        }
    }
}

private const val EMPTY_HINT =
    "Sem máscara: o efeito vale para o quadro todo. Adicione até 4 formas " +
        "e combine somando, subtraindo ou cruzando."

@Composable
private fun MaskHeader(
    owner: MaskOwner,
    name: String,
    set: MaskSet,
    selected: Mask?,
    timeMs: Long,
    viewing: Boolean,
    ui: MaskUi
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("${owner.label} · $name", color = RiseWhite, fontSize = 13.sp, maxLines = 1)
            Text(owner.effect, color = RiseMuted, fontSize = 11.sp, maxLines = 1)
        }
        if (selected != null && selected.isGeometric) {
            val here = selected.hasKeyAt(timeMs)
            val tint = when {
                here -> KeyGold
                selected.keys.isNotEmpty() -> Color.White
                else -> Color.White.copy(alpha = 0.55f)
            }
            val label = if (here) "Remover keyframe" else "Adicionar keyframe"
            PanelIconButton(KeyDiamond, label, tint = tint) { ui.onToggleKey(selected) }
        }
        ActionBar(maskActions(owner, set, selected, viewing, ui), Modifier.width(136.dp))
    }
}

private fun maskActions(
    owner: MaskOwner,
    set: MaskSet,
    selected: Mask?,
    viewing: Boolean,
    ui: MaskUi
): List<PanelAction> {
    val any = set.masks.isNotEmpty()
    val eye = if (viewing) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility
    return listOf(
        PanelAction(
            eye,
            "Ver máscara",
            enabled = any && owner != MaskOwner.OVERLAY,
            primary = true,
            onClick = ui.onView
        ),
        PanelAction(
            Icons.Rounded.ContentCopy,
            "Copiar máscaras",
            enabled = any,
            onClick = ui.onCopy
        ),
        PanelAction(Icons.Rounded.ContentPaste, "Colar máscaras", enabled = ui.onPaste != null) {
            ui.onPaste?.invoke()
        },
        PanelAction(
            Icons.Rounded.DeleteOutline,
            "Excluir máscara",
            enabled = selected != null,
            danger = true
        ) {
            selected?.let { ui.onDelete(it.id) }
        }
    )
}

@Composable
private fun AddRow(enabled: Boolean, onAdd: (MaskType) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MaskType.entries.forEach { type ->
            Row(
                Modifier.height(36.dp).clip(CircleShape).background(RiseSurfaceHigh)
                    .clickable(enabled = enabled) { onAdd(type) }.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Rounded.Add,
                    null,
                    Modifier.size(14.dp),
                    tint = if (enabled) RiseWhite else RiseMuted
                )
                Icon(
                    type.icon(),
                    null,
                    Modifier.size(16.dp),
                    tint = if (enabled) RiseWhite else RiseMuted
                )
                Text(
                    type.label,
                    fontSize = 12.sp,
                    color = if (enabled) RiseWhite else RiseMuted,
                    maxLines = 1
                )
            }
        }
    }
}

/** Combined list, top = first; each row: shape, operation chip, invert, on/off. */
@Composable
private fun MaskList(set: MaskSet, selectedId: String?, ui: MaskUi) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        set.masks.forEachIndexed { i, m -> MaskRow(i, m, m.id == selectedId, ui) }
    }
}

@Composable
private fun MaskRow(i: Int, m: Mask, on: Boolean, ui: MaskUi) {
    Row(
        Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp))
            .background(if (on) Color(0xFF242428) else Color(0xFF111113))
            .border(
                1.dp,
                if (on) RiseWhite.copy(alpha = 0.6f) else Color(0xFF2A2A2E),
                RoundedCornerShape(12.dp)
            )
            .clickable { ui.onSelect(m.id) }.padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val tint = if (m.enabled) RiseWhite else RiseMuted
        Icon(m.type.icon(), null, Modifier.size(18.dp), tint = tint)
        Column(Modifier.weight(1f)) {
            Text(
                "${i + 1}. ${m.type.label}",
                color = tint,
                fontSize = 13.sp,
                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1
            )
            val extra = maskSummary(m)
            if (extra.isNotEmpty()) Text(extra, color = RiseMuted, fontSize = 10.sp, maxLines = 1)
        }
        if (i > 0) {
            OpChip(m.op) {
                ui.onChange(
                    m.copy(
                        op = MaskOp.entries[
                            (m.op.ordinal + 1) %
                                MaskOp.entries.size
                        ]
                    )
                )
            }
        } else {
            Text("base", color = RiseMuted, fontSize = 11.sp)
        }
        PanelIconButton(
            Icons.Rounded.InvertColors,
            if (m.invert) "Desinverter" else "Inverter",
            tint = if (m.invert) KeyGold else RiseWhite
        ) {
            ui.onChange(m.copy(invert = !m.invert))
        }
        PanelIconButton(
            if (m.enabled) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
            if (m.enabled) "Desligar máscara" else "Ligar máscara"
        ) {
            ui.onChange(m.copy(enabled = !m.enabled))
        }
    }
}

private fun maskSummary(m: Mask) = buildList {
    if (m.invert) add("invertida")
    if (m.opacity < 1f) add("%.0f%%".format(m.opacity * 100))
    if (m.keys.isNotEmpty()) add("${m.keys.size} keys")
}.joinToString(" · ")

@Composable
private fun OpChip(op: MaskOp, onClick: () -> Unit) {
    val sign = when (op) {
        MaskOp.ADD -> "＋"
        MaskOp.SUBTRACT -> "−"
        MaskOp.INTERSECT -> "∩"
    }
    Row(
        Modifier.height(
            26.dp
        ).clip(
            CircleShape
        ).background(RiseSurfaceHigh).clickable(onClick = onClick).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) { Text("$sign ${op.label}", color = RiseWhite, fontSize = 11.sp, maxLines = 1) }
}

private class MaskTile(
    val label: String,
    val value: Float,
    val range: ClosedFloatingPointRange<Float>,
    val default: Float,
    val format: (Float) -> String,
    val set: (Float) -> Unit
)

private val pct: (Float) -> String = { "%.0f%%".format(it * 100f) }
private val deg: (Float) -> String = { "%.0f°".format(it) }

/** Position/size/rotation/feather (+ corners for rectangles); writes keys when animated. */
private fun geomTiles(m: Mask, g: MaskGeom, ui: MaskUi): List<MaskTile> = buildList {
    fun geo(
        label: String,
        v: Float,
        r: ClosedFloatingPointRange<Float>,
        d: Float,
        f: (Float) -> String,
        set: (Float) -> MaskGeom
    ) = add(MaskTile(label, v, r, d, f) { ui.onGeom(m, set(it)) })
    val size = MaskGeom.MIN_SIZE..2f
    geo("Posição X", g.cx, 0f..1f, 0.5f, pct) { g.copy(cx = it) }
    geo("Posição Y", g.cy, 0f..1f, 0.5f, pct) { g.copy(cy = it) }
    geo("Largura", g.w, size, 0.5f, pct) { g.copy(w = it) }
    geo(
        if (m.type ==
            MaskType.LINEAR
        ) {
            "Transição"
        } else {
            "Altura"
        },
        g.h,
        size,
        0.5f,
        pct
    ) { g.copy(h = it) }
    geo("Rotação", g.rotation, -180f..180f, 0f, deg) { g.copy(rotation = it) }
    geo("Suavidade", g.feather, 0f..1f, 0.1f, pct) { g.copy(feather = it) }
    if (m.type ==
        MaskType.RECT
    ) {
        add(
            MaskTile("Cantos", m.roundness, 0f..1f, 0.2f, pct) {
                ui.onChange(m.copy(roundness = it))
            }
        )
    }
}

/** Color mask: the qualifier key ranges (adjustment fields of the qualifier are unused). */
private fun colorTiles(m: Mask, ui: MaskUi): List<MaskTile> {
    val q = m.qualifier
    fun set(q2: Qualifier) = ui.onChange(m.copy(qualifier = q2))
    return listOf(
        MaskTile("Matiz", q.hue, 0f..360f, 30f, deg) { set(q.copy(hue = it)) },
        MaskTile("Faixa", q.hueWidth, 0f..180f, 25f, {
            "±%.0f°".format(it)
        }) { set(q.copy(hueWidth = it)) },
        MaskTile("Suav. matiz", q.hueSoft, 0f..1f, 0.5f, pct) { set(q.copy(hueSoft = it)) },
        MaskTile("Sat. mínima", q.satLow, 0f..1f, 0.15f, pct) { set(q.copy(satLow = it)) },
        MaskTile("Lum. mínima", q.lumLow, 0f..1f, 0.05f, pct) { set(q.copy(lumLow = it)) },
        MaskTile("Lum. máxima", q.lumHigh, 0f..1f, 0.95f, pct) { set(q.copy(lumHigh = it)) }
    )
}

@Composable
private fun MaskControls(m: Mask, first: Boolean, timeMs: Long, ui: MaskUi) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!first) {
            ChannelChips(MaskOp.entries, m.op, {
                it.label
            }) { ui.onChange(m.copy(op = it)) }
        }
        if (!m.isGeometric) {
            EyedropperRow(ui)
        } else if (m.keys.isNotEmpty()) {
            Text(
                "Animada: mexer na prévia ou nos controles grava keyframe no cursor.",
                color = RiseMuted,
                fontSize = 11.sp
            )
        }
        val tiles = (if (m.isGeometric) geomTiles(m, m.geomAt(timeMs), ui) else colorTiles(m, ui)) +
            MaskTile("Opacidade", m.opacity, 0f..1f, 1f, pct) { ui.onChange(m.copy(opacity = it)) }
        tiles.chunked(2).forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                line.forEach { t ->
                    TileSlider(
                        t.label,
                        t.value,
                        t.range,
                        Modifier.weight(1f),
                        default = t.default,
                        format = t.format,
                        change = t.set
                    )
                }
                if (line.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun EyedropperRow(ui: MaskUi) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PanelIconButton(
            Icons.Rounded.Colorize,
            if (ui.picking) "Cancelar conta-gotas" else "Conta-gotas: toque na prévia",
            enabled = ui.onEyedropper != null,
            tint = if (ui.picking) KeyGold else RiseWhite
        ) { ui.onEyedropper?.invoke() }
        val hint = if (ui.picking) "Toque na cor na prévia…" else "Cor como máscara (Seleção)"
        Text(hint, color = RiseMuted, fontSize = 12.sp)
    }
}
