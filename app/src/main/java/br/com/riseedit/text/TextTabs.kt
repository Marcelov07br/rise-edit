package br.com.riseedit.text

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatAlignLeft
import androidx.compose.material.icons.automirrored.outlined.FormatAlignRight
import androidx.compose.material.icons.outlined.FormatAlignCenter
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.riseedit.EditorUiState
import br.com.riseedit.RiseAccent
import br.com.riseedit.RiseMuted
import br.com.riseedit.RiseSurfaceHigh
import br.com.riseedit.RiseWhite
import br.com.riseedit.TextTab
import br.com.riseedit.core.CaptionWord
import br.com.riseedit.core.CaptionWords
import br.com.riseedit.core.LookScope
import br.com.riseedit.core.TextAlign
import br.com.riseedit.core.TextAnimation
import br.com.riseedit.core.TextLayer
import br.com.riseedit.core.TextLook
import br.com.riseedit.core.TextLooks
import br.com.riseedit.core.TextStyle
import br.com.riseedit.core.WordAnimation
import br.com.riseedit.core.WordUnit
import br.com.riseedit.designsystem.ActionBar
import br.com.riseedit.designsystem.GraphicSurface
import br.com.riseedit.designsystem.PanelAction
import br.com.riseedit.designsystem.RiseDropdown
import br.com.riseedit.designsystem.SegmentedTabs
import br.com.riseedit.tools.Tile
import br.com.riseedit.tools.TileGrid

/** 0.25.0: what the studio needs besides the draft (stateless for Paparazzi). */
internal class StudioActions(
    val userLooks: List<TextLook> = emptyList(),
    val saveLook: (TextLayer) -> Unit = {},
    val deleteLook: (TextLook) -> Unit = {},
    /** Captions in the draft's track (incl. itself), shown on the "Todas" choice. */
    val trackCount: Int = 1
)

/**
 * Text and caption studio: SegmentedTabs Texto / Estilo / Animação, icon-only ActionBar in the
 * corner (save look, info, red reset). Captions get a scope switch: all of the track by default.
 */
@Composable
internal fun TextStudio(
    draft: TextLayer,
    update: (TextLayer) -> Unit,
    ui: EditorUiState,
    actions: StudioActions = StudioActions(),
    infoInitially: Boolean = false,
    fontMenuOpen: Boolean = false
) {
    var info by rememberSaveable { mutableStateOf(infoInitially) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SegmentedTabs(TextTab.entries, ui.textTab, Modifier.weight(1f), label = { it.label }, minTab = 64.dp) {
                ui.textTab = it
            }
            Spacer(Modifier.size(8.dp))
            ActionBar(
                listOf(
                    PanelAction(Icons.Rounded.BookmarkAdd, "Salvar como meu estilo") { actions.saveLook(draft) },
                    PanelAction(Icons.Rounded.Info, "Sobre", checked = info) { info = !info },
                    PanelAction(Icons.Rounded.RestartAlt, "Restaurar estilo", danger = true) { update(reset(draft)) }
                )
            )
        }
        if (draft.isCaption) {
            SegmentedTabs(
                LookScope.entries,
                ui.lookScope,
                label = { if (it == LookScope.ALL) "Todas as legendas (${actions.trackCount})" else it.label }
            ) { ui.lookScope = it }
        }
        if (info) {
            GraphicSurface {
                Text(
                    if (draft.isCaption) {
                        "Estilo e animação valem para todas as legendas desta faixa (um só desfazer). " +
                            "Use \"Só esta\" para uma exceção. O texto de cada legenda continua individual."
                    } else {
                        "Prévia e exportação usam o mesmo renderizador. Fontes: SIL OFL 1.1."
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        when (ui.textTab) {
            TextTab.TEXT -> TextSection(draft, update, fontMenuOpen)
            TextTab.STYLE -> StyleSection(draft, update, ui, actions)
            TextTab.ANIMATION -> AnimationSection(draft, update)
        }
    }
}

private fun reset(layer: TextLayer): TextLayer = if (layer.isCaption) {
    TextLooks.all.first().applyTo(layer).copy(highlightColor = CaptionWords.HIGHLIGHT)
} else {
    layer.copy(style = TextStyle(), color = TextStyle.WHITE, highlightColor = 0)
}

// ---------------------------------------------------------------- Texto

@Composable
private fun TextSection(draft: TextLayer, update: (TextLayer) -> Unit, fontMenuOpen: Boolean) {
    val s = draft.style
    fun set(style: TextStyle) = update(draft.copy(style = style))
    FontPicker(draft, update, fontMenuOpen)
    FormatRow(draft, update)
    if (s.flow != br.com.riseedit.core.FlowKind.NONE || s.secondaryFont.isNotEmpty()) WordFontChips(draft, update)
    TileGrid(
        listOf(
            Tile("Tamanho", draft.size, 0.4f..3f, 1f, format = { "%.0f%%".format(it * 100) }) {
                update(draft.copy(size = it))
            },
            Tile("Opacidade", s.opacity, 0f..1f, 1f, format = { "%.0f%%".format(it * 100) }) {
                set(s.copy(opacity = it))
            },
            Tile("Espaçamento", s.letterSpacing, -0.1f..0.5f, 0f, format = { "%.2f".format(it) }) {
                set(s.copy(letterSpacing = it))
            },
            Tile("Altura da linha", s.lineHeight, 0.8f..2.4f, 1.2f, format = { "%.1f".format(it) }) {
                set(s.copy(lineHeight = it))
            }
        )
    )
}

/** Fonts in their own face, grouped Sans / Serifa / Display; two columns. */
@Composable
internal fun FontPicker(draft: TextLayer, update: (TextLayer) -> Unit, open: Boolean = true) {
    var category by rememberSaveable {
        mutableStateOf(TextFonts.all.firstOrNull { it.key == draft.style.font }?.category ?: TextFonts.Category.SANS)
    }
    var expanded by rememberSaveable { mutableStateOf(open) }
    val current = TextFonts.all.firstOrNull { it.key == draft.style.font } ?: TextFonts.all.first()
    GraphicSurface {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.semantics { contentDescription = "Fonte" },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Fonte", fontSize = 13.sp, color = RiseMuted, modifier = Modifier.weight(1f))
            Text(
                current.label,
                fontSize = 18.sp,
                color = RiseWhite,
                fontFamily = remember(current.key) { FontFamily(TextFonts.typeface(current.key, true, false)) }
            )
            Text(if (expanded) "  ▴" else "  ▾", color = RiseMuted, fontSize = 13.sp)
        }
        if (expanded) {
            SegmentedTabs(TextFonts.Category.entries, category, label = { it.label }, minTab = 64.dp) { category = it }
            val fonts = TextFonts.all.filter { it.category == category || (it.key == "default" && category == TextFonts.Category.SANS) }
            fonts.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { font -> FontCard(font, font.key == draft.style.font, Modifier.weight(1f)) {
                        update(draft.copy(style = draft.style.copy(font = font.key)))
                    } }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun FontCard(font: TextFonts.Font, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val family = remember(font.key) { FontFamily(TextFonts.typeface(font.key, true, false)) }
    Column(
        modifier.height(64.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF242426))
            .border(if (active) 2.dp else 1.dp, if (active) RiseWhite else Color(0x1FFFFFFF), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics { contentDescription = "Fonte ${font.label}" },
        verticalArrangement = Arrangement.Center
    ) {
        Text("Legenda Ágil", color = RiseWhite, fontFamily = family, fontSize = 19.sp, maxLines = 1, overflow = TextOverflow.Clip)
        Text(font.label, color = RiseMuted, fontSize = 11.sp, maxLines = 1)
    }
}

/** Bold/italic and alignment toggles. */
@Composable
internal fun FormatRow(draft: TextLayer, update: (TextLayer) -> Unit) {
    val s = draft.style
    fun set(style: TextStyle) = update(draft.copy(style = style))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RoundIcon(Icons.Outlined.FormatBold, "Negrito", s.bold) { set(s.copy(bold = !s.bold)) }
        RoundIcon(Icons.Outlined.FormatItalic, "Itálico", s.italic) { set(s.copy(italic = !s.italic)) }
        Box(Modifier.size(12.dp))
        RoundIcon(Icons.AutoMirrored.Outlined.FormatAlignLeft, "Alinhar à esquerda", s.align == TextAlign.LEFT) {
            set(s.copy(align = TextAlign.LEFT))
        }
        RoundIcon(Icons.Outlined.FormatAlignCenter, "Centralizar", s.align == TextAlign.CENTER) {
            set(s.copy(align = TextAlign.CENTER))
        }
        RoundIcon(Icons.AutoMirrored.Outlined.FormatAlignRight, "Alinhar à direita", s.align == TextAlign.RIGHT) {
            set(s.copy(align = TextAlign.RIGHT))
        }
    }
}

// ---------------------------------------------------------------- Estilo

private val SAMPLE = TextLayer(
    text = "Sua legenda",
    endMs = 2000,
    words = listOf(CaptionWord(0, 700, "Sua"), CaptionWord(700, 2000, "legenda"))
)

private val FLOW_SAMPLE = TextLayer(
    text = "amo essa vida demais",
    endMs = 2000,
    words = listOf(
        CaptionWord(0, 500, "amo"), CaptionWord(500, 1000, "essa"),
        CaptionWord(1000, 1500, "vida"), CaptionWord(1500, 2000, "demais")
    )
)

/**
 * 0.26.0: per-word font override. Each chip cycles Auto → Base → Destaque; Auto follows the
 * preset rule (emotive / emphasis / thematic keywords + heuristics). Never copied by apply-to-all.
 */
@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
internal fun WordFontChips(draft: TextLayer, update: (TextLayer) -> Unit) {
    val s = draft.style
    val words = remember(draft.text) { Regex("\\S+").findAll(draft.text).map { it.value }.toList() }
    val auto = br.com.riseedit.core.WordRoles.accents(s.flow, words, emptyMap())
    GraphicSurface {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Fonte por palavra", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Text("toque: auto → base → destaque", fontSize = 11.sp, color = RiseMuted)
        }
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            words.forEachIndexed { i, w ->
                val override = draft.fontOverrides[i]
                val accent = override ?: auto[i]
                val face = if (accent && s.secondaryFont.isNotEmpty()) {
                    TextFonts.typeface(s.secondaryFont, true, false, s.secondaryWeight)
                } else {
                    TextFonts.typeface(s.font, s.bold, s.italic, s.weight)
                }
                val tag = when (override) { null -> "auto"; true -> "destaque"; false -> "base" }
                Column(
                    Modifier.clip(RoundedCornerShape(10.dp))
                        .background(if (accent) RiseAccent.copy(alpha = 0.22f) else Color(0x14FFFFFF))
                        .border(1.dp, if (override != null) RiseAccent else Color(0x22FFFFFF), RoundedCornerShape(10.dp))
                        .clickable {
                            val next = when (override) { null -> !auto[i]; !auto[i] -> auto[i]; else -> null }
                            val map = draft.fontOverrides.toMutableMap()
                            if (next == null) map.remove(i) else map[i] = next
                            update(draft.copy(fontOverrides = map))
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        w,
                        fontSize = 15.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily(face),
                        color = Color.White
                    )
                    Text(tag, fontSize = 10.sp, color = RiseMuted)
                }
            }
        }
    }
}

/** Sample at [localMs] with the look applied (word 2 is "spoken" for the highlight). */
private fun sampleOf(look: TextLook, localMs: Long = 1200): Pair<TextLayer, IntRange?> {
    val flow = look.style.flow != br.com.riseedit.core.FlowKind.NONE
    val layer = look.applyTo(if (flow) FLOW_SAMPLE else SAMPLE).copy(highlightColor = look.highlightColor)
    return layer to CaptionWords.activeRange(layer, localMs)
}

/**
 * High-res tile: renders at the tile's real pixel size (density aware) so the text is crisp,
 * instead of the old 220 px frame scaled up.
 */
@Composable
internal fun LookTile(
    label: String,
    layer: TextLayer,
    highlight: IntRange?,
    selected: Boolean,
    modifier: Modifier = Modifier,
    localMs: Long = 1200,
    onClick: () -> Unit
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }.toInt().coerceAtLeast(48)
        val heightPx = (widthPx / TILE_ASPECT).toInt()
        val still = layer.copy(
            style = layer.style.copy(animationIn = TextAnimation.NONE, animationOut = TextAnimation.NONE)
        )
        val bitmap = remember(still, widthPx, highlight, localMs) {
            // Font ≈ 30% of the tile height; wrap width = tile width.
            val frameHeight = (heightPx * 0.30f / TextRenderer.FONT_FRACTION).toInt()
            TextRenderer.render(still, widthPx, frameHeight, 1f, highlight, localMs).asImageBitmap()
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(TILE_ASPECT).clip(RoundedCornerShape(14.dp))
                    .background(TILE_BG)
                    .border(
                        if (selected) 2.dp else 1.dp,
                        if (selected) RiseWhite else Color(0x1FFFFFFF),
                        RoundedCornerShape(14.dp)
                    )
                    .clickable(onClick = onClick).semantics { contentDescription = label }
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) { Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Inside) }
            Text(
                label,
                color = if (selected) RiseWhite else RiseMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

private const val TILE_ASPECT = 1.45f
private val TILE_BG = Color(0xFF3A4654)

/** Adaptive grid: columns of at least [min] wide. */
@Composable
internal fun <T> AdaptiveGrid(items: List<T>, min: Dp = 104.dp, cell: @Composable (T, Modifier) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cols = (maxWidth / min).toInt().coerceIn(2, 6)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items.chunked(cols).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { cell(it, Modifier.weight(1f)) }
                    repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

private enum class StylePart(val label: String) {
    TEXT("Texto"),
    WORD("Palavra falada"),
    STROKE("Traço"),
    SHADOW("Sombra"),
    GLOW("Brilho"),
    BACKGROUND("Fundo")
}

@Composable
private fun StyleSection(draft: TextLayer, update: (TextLayer) -> Unit, ui: EditorUiState, a: StudioActions) {
    val (flows, classic) = TextLooks.all.partition { it.style.flow != br.com.riseedit.core.FlowKind.NONE }
    Text("Estilos com fluxo", fontSize = 13.sp, color = RiseMuted)
    AdaptiveGrid(flows) { look, m ->
        val (layer, hl) = sampleOf(look, 1400)
        LookTile(look.name, layer, hl, look.matches(draft), m, 1400) { update(look.applyTo(draft)) }
    }
    Text("Estilos", fontSize = 13.sp, color = RiseMuted)
    AdaptiveGrid(classic) { look, m ->
        val (layer, hl) = sampleOf(look)
        LookTile(look.name, layer, hl, look.matches(draft), m) { update(look.applyTo(draft)) }
    }
    if (a.userLooks.isNotEmpty()) {
        Text("Meus estilos", fontSize = 13.sp, color = RiseMuted)
        AdaptiveGrid(a.userLooks) { look, m ->
            val (layer, hl) = sampleOf(look)
            LookTile(look.name, layer, hl, look.matches(draft), m) { update(look.applyTo(draft)) }
        }
    }
    ColorSection(draft, update, ui)
}

@Composable
private fun ColorSection(draft: TextLayer, update: (TextLayer) -> Unit, ui: EditorUiState) {
    val parts = StylePart.entries.filter { it != StylePart.WORD || draft.isCaption }
    var part by rememberSaveable { mutableStateOf(StylePart.TEXT) }
    var wheel by remember { mutableStateOf(false) }
    PillRow(parts, part, { it.label }) { part = it }
    val apply = { color: Int -> update(withColor(draft, part, color)) }
    SwatchRow(
        colorOf(draft, part),
        onEyedropper = {
            ui.eyedropper = if (ui.eyedropper != null) null else { color ->
                ui.textDraft?.let { current -> ui.textDraft = withColor(current, part, color) }
                ui.eyedropper = null
            }
        },
        onWheel = { wheel = true },
        eyedropperActive = ui.eyedropper != null,
        onPick = apply
    )
    if (ui.eyedropper != null) {
        Text("Toque na prévia para capturar uma cor do vídeo", color = RiseMuted, fontSize = 12.sp)
    }
    PartTiles(draft, part, update)
    if (wheel) ColorWheelDialog(colorOf(draft, part), onDismiss = { wheel = false }) { apply(it) }
}

@Composable
private fun PartTiles(draft: TextLayer, part: StylePart, update: (TextLayer) -> Unit) {
    val s = draft.style
    fun set(style: TextStyle) = update(draft.copy(style = style))
    val pct = { v: Float -> "%.0f%%".format(v * 100) }
    val tiles = when (part) {
        StylePart.TEXT -> listOf(
            Tile("Opacidade", s.opacity, 0f..1f, 1f, pct) { set(s.copy(opacity = it)) }
        )
        StylePart.WORD -> emptyList()
        StylePart.STROKE -> listOf(
            Tile("Espessura", s.strokeWidth, 0f..0.4f, 0f, { "${(it * 250).toInt()}" }) { set(s.copy(strokeWidth = it)) }
        )
        StylePart.GLOW -> listOf(Tile("Intensidade", s.glow, 0f..1f, 0f, pct) { set(s.copy(glow = it)) })
        StylePart.BACKGROUND -> listOf(
            Tile("Opacidade", s.background, 0f..1f, 0f, pct) { set(s.copy(background = it)) },
            Tile("Arredondar", s.backgroundRadius, 0f..1f, 0.25f, pct) { set(s.copy(backgroundRadius = it)) }
        )
        StylePart.SHADOW -> listOf(
            Tile("Opacidade", s.shadow, 0f..1f, 0f, pct) { set(s.copy(shadow = it)) },
            Tile("Desfoque", s.shadowBlur, 0f..0.5f, 0.12f, { "${(it * 200).toInt()}" }) { set(s.copy(shadowBlur = it)) },
            Tile("Distância", s.shadowDistance, 0f..0.4f, 0.08f, { "${(it * 250).toInt()}" }) {
                set(s.copy(shadowDistance = it))
            }
        )
    }
    if (part == StylePart.TEXT || part == StylePart.WORD) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Gradiente", color = RiseWhite, fontSize = 13.sp, modifier = Modifier.weight(1f))
            br.com.riseedit.designsystem.RiseToggle(s.gradientColor != 0) {
                set(s.copy(gradientColor = if (it) 0xFFFF4081.toInt() else 0))
            }
        }
        if (draft.isCaption) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Caixa na palavra falada", color = RiseWhite, fontSize = 13.sp, modifier = Modifier.weight(1f))
                br.com.riseedit.designsystem.RiseToggle(s.wordBox) { set(s.copy(wordBox = it)) }
            }
        }
    }
    if (tiles.isNotEmpty()) TileGrid(tiles)
}

private fun colorOf(layer: TextLayer, part: StylePart) = when (part) {
    StylePart.TEXT -> layer.color
    StylePart.WORD -> layer.highlightColor
    StylePart.STROKE -> layer.style.strokeColor
    StylePart.GLOW -> layer.style.glowColor
    StylePart.BACKGROUND -> layer.style.backgroundColor
    StylePart.SHADOW -> layer.style.shadowColor
}

/** Picking a color for an effect that is off turns it on at a sensible amount. */
private fun withColor(layer: TextLayer, part: StylePart, color: Int): TextLayer {
    val s = layer.style
    return when (part) {
        StylePart.TEXT -> layer.copy(color = color)
        StylePart.WORD -> layer.copy(highlightColor = color)
        StylePart.STROKE -> layer.copy(style = s.copy(strokeColor = color, strokeWidth = s.strokeWidth.takeIf { it > 0 } ?: 0.12f))
        StylePart.GLOW -> layer.copy(style = s.copy(glowColor = color, glow = s.glow.takeIf { it > 0 } ?: 0.6f))
        StylePart.BACKGROUND -> layer.copy(style = s.copy(backgroundColor = color, background = s.background.takeIf { it > 0 } ?: 1f))
        StylePart.SHADOW -> layer.copy(style = s.copy(shadowColor = color, shadow = s.shadow.takeIf { it > 0 } ?: 0.7f))
    }
}

// ---------------------------------------------------------------- Animação

/** Frame of each preset that shows its idea in a still card (mid-motion of word 2). */
private fun cardMs(a: WordAnimation) = when (a) {
    WordAnimation.NONE -> 1200L
    WordAnimation.TYPE -> 640L
    WordAnimation.KARAOKE -> 1100L
    else -> 820L
}

@Composable
private fun AnimationSection(draft: TextLayer, update: (TextLayer) -> Unit) {
    val s = draft.style
    fun set(style: TextStyle) = update(draft.copy(style = style))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        RiseDropdown(
            "Fluxo",
            br.com.riseedit.core.FlowKind.entries.toList(),
            s.flow,
            Modifier.weight(1f),
            text = { it.label }
        ) { set(s.copy(flow = it)) }
        RiseDropdown(
            "Caixa",
            br.com.riseedit.core.CapsRule.entries.toList(),
            s.caps,
            Modifier.weight(1f),
            text = { it.label }
        ) { set(s.copy(caps = it)) }
    }
    if (s.flow != br.com.riseedit.core.FlowKind.NONE) {
        TileGrid(
            listOf(
                Tile("Desfoque de movimento", s.blurScale, 0f..2f, 1f, { "%.0f%%".format(it * 100) }) {
                    set(s.copy(blurScale = it))
                },
                Tile("Palavras por grupo", s.maxWords.toFloat(), 0f..8f, 0f, { if (it < 0.5f) "frase" else "%.0f".format(it) }) {
                    set(s.copy(maxWords = it.toInt()))
                }
            )
        )
    }
    if (s.flow == br.com.riseedit.core.FlowKind.NONE) {
        Text("Por palavra", fontSize = 13.sp, color = RiseMuted)
        AdaptiveGrid(WordAnimation.entries.toList(), min = 150.dp) { anim, m ->
            val sample = SAMPLE.copy(
                text = "Bora editar hoje",
                endMs = 2400,
                words = listOf(CaptionWord(0, 600, "Bora"), CaptionWord(600, 1500, "editar"), CaptionWord(1500, 2400, "hoje")),
                color = draft.color,
                highlightColor = draft.highlightColor.takeIf { it != 0 } ?: CaptionWords.HIGHLIGHT,
                style = s.copy(wordAnimation = anim, font = s.font, wordMs = 260)
            )
            val ms = cardMs(anim)
            LookTile(anim.label, sample, CaptionWords.activeRange(sample, ms), s.wordAnimation == anim, m, ms) {
                set(s.copy(wordAnimation = anim))
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        RiseDropdown("Unidade", WordUnit.entries.toList(), s.wordUnit, Modifier.weight(1f), text = { it.label }) {
            set(s.copy(wordUnit = it))
        }
        RiseDropdown("Entrada", TextAnimation.entries.toList(), s.animationIn, Modifier.weight(1f), text = { it.label }) {
            set(s.copy(animationIn = it))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        RiseDropdown("Saída", TextAnimation.entries.toList(), s.animationOut, Modifier.weight(1f), text = { it.label }) {
            set(s.copy(animationOut = it))
        }
        Spacer(Modifier.weight(1f))
    }
    TileGrid(
        listOf(
            Tile("Entrada/saída", s.animationMs / 1000f, 0.1f..2f, 0.4f, { "%.2f s".format(it) }) {
                set(s.copy(animationMs = (it * 1000).toLong()))
            },
            Tile("Ritmo por palavra", s.wordMs / 1000f, 0.04f..1f, 0.26f, { "%.2f s".format(it) }) {
                set(s.copy(wordMs = (it * 1000).toLong().coerceIn(40, 2000)))
            }
        ),
        enabled = true
    )
    Text(
        "Movimento com easing (mola de baixo bounce no pop); nada linear. Prévia = exportação.",
        color = RiseMuted,
        fontSize = 12.sp
    )
}
