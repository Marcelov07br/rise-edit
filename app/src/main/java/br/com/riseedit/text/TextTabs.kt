package br.com.riseedit.text

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.riseedit.EditorUiState
import br.com.riseedit.RiseMuted
import br.com.riseedit.RiseSurfaceHigh
import br.com.riseedit.RiseWhite
import br.com.riseedit.core.TextAlign
import br.com.riseedit.core.TextAnimation
import br.com.riseedit.core.TextLayer
import br.com.riseedit.core.TextPreset
import br.com.riseedit.core.TextPresets
import br.com.riseedit.core.TextStyle
import br.com.riseedit.core.TextTemplates

@Composable
internal fun TemplatesTab(draft: TextLayer, update: (TextLayer) -> Unit) {
    TileRow {
        TextTemplates.all.forEach { template ->
            val sample = draft.copy(
                text = template.sample,
                color = template.color,
                style = template.style
            )
            RenderedTile(
                template.name,
                sample,
                draft.style == template.style && draft.color == template.color,
                size = 76.dp
            ) {
                update(draft.copy(color = template.color, style = template.style))
            }
        }
    }
    Text(
        "Modelos combinam fonte, estilo e animação. Ajuste depois nas outras abas.",
        color = RiseMuted,
        fontSize = 12.sp
    )
}

@Composable
internal fun FontsTab(draft: TextLayer, update: (TextLayer) -> Unit) {
    val style = draft.style
    TextFonts.all.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { font ->
                val active = style.font == font.key
                val family =
                    remember(font.key) { FontFamily(TextFonts.typeface(font.key, false, false)) }
                Box(
                    Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(12.dp))
                        .background(RiseSurfaceHigh)
                        .border(
                            if (active) 2.dp else 0.dp,
                            if (active) RiseWhite else Color.Transparent,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { update(draft.copy(style = style.copy(font = font.key))) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        font.label,
                        color = RiseWhite,
                        fontFamily = family,
                        fontSize = 16.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
    FormatRow(draft, update)
}

/** Bold/italic and alignment toggles. */
@Composable
internal fun FormatRow(draft: TextLayer, update: (TextLayer) -> Unit) {
    val s = draft.style
    fun set(style: TextStyle) = update(draft.copy(style = style))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RoundIcon(Icons.Outlined.FormatBold, "Negrito", s.bold) { set(s.copy(bold = !s.bold)) }
        RoundIcon(Icons.Outlined.FormatItalic, "Itálico", s.italic) {
            set(s.copy(italic = !s.italic))
        }
        Box(Modifier.size(12.dp))
        RoundIcon(
            Icons.AutoMirrored.Outlined.FormatAlignLeft,
            "Alinhar à esquerda",
            s.align == TextAlign.LEFT
        ) {
            set(s.copy(align = TextAlign.LEFT))
        }
        RoundIcon(Icons.Outlined.FormatAlignCenter, "Centralizar", s.align == TextAlign.CENTER) {
            set(s.copy(align = TextAlign.CENTER))
        }
        RoundIcon(
            Icons.AutoMirrored.Outlined.FormatAlignRight,
            "Alinhar à direita",
            s.align == TextAlign.RIGHT
        ) {
            set(s.copy(align = TextAlign.RIGHT))
        }
    }
}

private enum class StylePart(val label: String) {
    TEXT("Texto"),
    STROKE("Traço"),
    GLOW("Brilho"),
    BACKGROUND("Plano de fundo"),
    SHADOW("Sombra")
}

@Composable
private fun PresetTiles(presets: List<TextPreset>, draft: TextLayer, update: (TextLayer) -> Unit) {
    TileRow {
        presets.forEach { preset ->
            val style = preset.apply(draft.style)
            val color = preset.color ?: draft.color
            RenderedTile(
                preset.name,
                draft.copy(text = "Aa", color = color, style = style),
                draft.style == style
            ) {
                update(draft.copy(color = color, style = style))
            }
        }
    }
}

@Composable
internal fun StylesTab(draft: TextLayer, update: (TextLayer) -> Unit, ui: EditorUiState) {
    var part by rememberSaveable { mutableStateOf(StylePart.TEXT) }
    var wheel by remember { mutableStateOf(false) }
    PresetTiles(TextPresets.styles, draft, update)
    PillRow(StylePart.entries, part, { it.label }) { part = it }
    val apply = { color: Int -> update(withColor(draft, part, color)) }
    SwatchRow(
        colorOf(draft, part),
        onEyedropper = {
            ui.eyedropper = if (ui.eyedropper != null) {
                null
            } else {
                { color ->
                    ui.textDraft?.let { current -> ui.textDraft = withColor(current, part, color) }
                    ui.eyedropper = null
                }
            }
        },
        onWheel = { wheel = true },
        eyedropperActive = ui.eyedropper != null,
        onPick = apply
    )
    if (ui.eyedropper != null) {
        Text("Toque na prévia para capturar uma cor do vídeo", color = RiseMuted, fontSize = 12.sp)
    }
    PartSliders(draft, part, update)
    if (wheel) ColorWheelDialog(colorOf(draft, part), onDismiss = { wheel = false }) { apply(it) }
}

@Composable
private fun PartSliders(draft: TextLayer, part: StylePart, update: (TextLayer) -> Unit) {
    val s = draft.style
    fun set(style: TextStyle) = update(draft.copy(style = style))
    when (part) {
        StylePart.TEXT -> {
            LiveSlider("Opacidade", s.opacity, 0f..1f) { set(s.copy(opacity = it)) }
            FormatRow(draft, update)
            LiveSlider("Espaçamento", s.letterSpacing, -0.1f..0.5f, { "%.2f".format(it) }) {
                set(s.copy(letterSpacing = it))
            }
            LiveSlider("Altura da linha", s.lineHeight, 0.8f..2.4f, { "%.1f".format(it) }) {
                set(s.copy(lineHeight = it))
            }
        }
        StylePart.STROKE -> LiveSlider("Espessura", s.strokeWidth, 0f..0.4f, {
            "${(it * 250).toInt()}"
        }) {
            set(s.copy(strokeWidth = it))
        }
        StylePart.GLOW -> LiveSlider("Intensidade", s.glow, 0f..1f) { set(s.copy(glow = it)) }
        StylePart.BACKGROUND -> {
            LiveSlider("Opacidade", s.background, 0f..1f) { set(s.copy(background = it)) }
            LiveSlider("Arredondar", s.backgroundRadius, 0f..1f) {
                set(s.copy(backgroundRadius = it))
            }
        }
        StylePart.SHADOW -> {
            LiveSlider("Opacidade", s.shadow, 0f..1f) { set(s.copy(shadow = it)) }
            LiveSlider("Desfoque", s.shadowBlur, 0f..0.5f, {
                "${(it * 200).toInt()}"
            }) { set(s.copy(shadowBlur = it)) }
            LiveSlider("Distância", s.shadowDistance, 0f..0.4f, { "${(it * 250).toInt()}" }) {
                set(s.copy(shadowDistance = it))
            }
        }
    }
}

private fun colorOf(layer: TextLayer, part: StylePart) = when (part) {
    StylePart.TEXT -> layer.color
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
        StylePart.STROKE -> layer.copy(
            style = s.copy(
                strokeColor = color,
                strokeWidth =
                s.strokeWidth.takeIf { it > 0 } ?: 0.12f
            )
        )
        StylePart.GLOW -> layer.copy(
            style = s.copy(
                glowColor = color,
                glow =
                s.glow.takeIf { it > 0 } ?: 0.6f
            )
        )
        StylePart.BACKGROUND -> layer.copy(
            style = s.copy(
                backgroundColor = color,
                background =
                s.background.takeIf { it > 0 } ?: 1f
            )
        )
        StylePart.SHADOW -> layer.copy(
            style = s.copy(
                shadowColor = color,
                shadow =
                s.shadow.takeIf { it > 0 } ?: 0.7f
            )
        )
    }
}

@Composable
internal fun EffectsTab(draft: TextLayer, update: (TextLayer) -> Unit) {
    PresetTiles(TextPresets.effects, draft, update)
    Text("Efeitos aparecem igual na prévia e na exportação.", color = RiseMuted, fontSize = 12.sp)
}

@Composable
internal fun AnimationsTab(draft: TextLayer, update: (TextLayer) -> Unit) {
    var entering by rememberSaveable { mutableStateOf(true) }
    val s = draft.style
    PillRow(listOf(true, false), entering, { if (it) "Entrada" else "Saída" }) { entering = it }
    val current = if (entering) s.animationIn else s.animationOut
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextAnimation.entries.chunked(4).first().forEach {
            AnimationChip(it, it == current, entering, draft, update)
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextAnimation.entries.drop(4).forEach {
            AnimationChip(it, it == current, entering, draft, update)
        }
    }
    LiveSlider("Duração", s.animationMs / 1000f, 0.1f..2f, { "%.1fs".format(it) }) {
        update(draft.copy(style = s.copy(animationMs = (it * 1000).toLong())))
    }
}

@Composable
private fun AnimationChip(
    animation: TextAnimation,
    active: Boolean,
    entering: Boolean,
    draft: TextLayer,
    update: (TextLayer) -> Unit
) {
    val s = draft.style
    Text(
        animation.label,
        color = if (active) Color.Black else RiseWhite,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        fontStyle = FontStyle.Normal,
        modifier = Modifier.clip(RoundedCornerShape(12.dp))
            .background(if (active) RiseWhite else RiseSurfaceHigh)
            .clickable {
                val style = if (entering) {
                    s.copy(
                        animationIn = animation
                    )
                } else {
                    s.copy(animationOut = animation)
                }
                update(draft.copy(style = style))
            }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    )
}
