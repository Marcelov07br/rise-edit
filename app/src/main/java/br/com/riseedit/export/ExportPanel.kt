package br.com.riseedit.export

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.com.riseedit.RiseMuted
import br.com.riseedit.RiseOutline
import br.com.riseedit.RiseSurface
import br.com.riseedit.RiseWhite
import br.com.riseedit.core.Chroma
import br.com.riseedit.core.EncoderSupport
import br.com.riseedit.core.ExportOptions
import br.com.riseedit.core.ExportPlan
import br.com.riseedit.core.ExportRoute
import br.com.riseedit.core.ExportSettings
import br.com.riseedit.core.FrameRateChoice
import br.com.riseedit.core.MovAudio
import br.com.riseedit.core.Project
import br.com.riseedit.core.ProresProfile
import br.com.riseedit.core.VideoCodec
import br.com.riseedit.designsystem.ActionBar
import br.com.riseedit.designsystem.GraphicSurface
import br.com.riseedit.designsystem.PanelAction
import br.com.riseedit.designsystem.RiseDropdown
import br.com.riseedit.designsystem.SegmentedTabs
import br.com.riseedit.designsystem.TileSlider

private const val QUALITY_HIGH = 2

/** 0.21: export panel in a dialog; options come from what the phone can encode. */
@Composable
fun ExportDialog(project: Project, onClose: () -> Unit, onExport: (ExportSettings) -> Unit) {
    val support = remember { br.com.riseedit.editor.EncoderSupportCache.get() }
    val allowedFps = remember(project) { ExportPlan.allowedFps(project) }
    var value by remember {
        val e = project.export
        mutableStateOf(
            ExportOptions.normalize(
                e.copy(fps = if (e.fps in allowedFps) e.fps else (allowedFps.maxOrNull() ?: 30)),
                support
            )
        )
    }
    var advanced by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ExportContent(project, value, allowedFps, support, advanced, { value = it }, { advanced = !advanced },
            onClose, { onExport(value) })
    }
}

/** Stateless body (also drawn by the screenshot tests, which cannot render popups). */
@Suppress("LongMethod", "LongParameterList")
@Composable
fun ExportContent(
    project: Project,
    value: ExportSettings,
    allowedFps: List<Int>,
    support: EncoderSupport,
    advanced: Boolean,
    onChange: (ExportSettings) -> Unit,
    onToggleAdvanced: () -> Unit,
    onClose: () -> Unit,
    onExport: () -> Unit,
    codecMenuOpen: Boolean = false,
    maxBodyHeight: androidx.compose.ui.unit.Dp = 520.dp
) {
    fun set(e: ExportSettings) = onChange(ExportOptions.normalize(e, support))
    val quality = remember(value) {
        if (value.codec == VideoCodec.PRORES) {
            if (value.prores == ProresProfile.HQ) QUALITY_HIGH else 1
        } else {
            (0..2).minBy { kotlin.math.abs(ExportPlan.suggestedBitrate(value.height, value.fps, value.codec, it) - value.bitrate) }
        }
    }
    Column(
        Modifier.padding(16.dp).widthIn(max = 460.dp).fillMaxWidth()
            .clip(RoundedCornerShape(24.dp)).background(RiseSurface)
            .border(1.dp, RiseOutline, RoundedCornerShape(24.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Exportar", color = RiseWhite, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(ExportOptions.summary(value), color = RiseMuted, fontSize = 12.sp)
        Column(
            Modifier.heightIn(max = maxBodyHeight).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GraphicSurface {
                Label("Resolução")
                SegmentedTabs(ExportSettings.HEIGHTS, value.height, label = { if (it == 2160) "4K" else "${it}p" }) {
                    set(value.copy(height = it, bitrate = ExportPlan.suggestedBitrate(it, value.fps, value.codec, quality)))
                }
                Label("Quadros por segundo")
                SegmentedTabs(allowedFps, value.fps, label = {
                    FrameRateChoice(it, value.ntsc && it in ExportSettings.NTSC_BASES).label
                }) { set(value.copy(fps = it, ntsc = value.ntsc && it in ExportSettings.NTSC_BASES)) }
                Label("Qualidade")
                SegmentedTabs(listOf(0, 1, 2), quality, label = { listOf("Leve", "Boa", "Alta")[it] }) {
                    set(
                        value.copy(
                            bitrate = ExportPlan.suggestedBitrate(value.height, value.fps, value.codec, it),
                            prores = if (it == QUALITY_HIGH) ProresProfile.HQ else ProresProfile.STANDARD
                        )
                    )
                }
            }
            val turn by animateFloatAsState(if (advanced) 180f else 0f, label = "chevron")
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onToggleAdvanced)
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Avançado", color = RiseWhite, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                Text(
                    if (advanced) "" else "${value.codec.label} · ${value.bitDepth} bits ${value.chroma.label}",
                    color = RiseMuted, fontSize = 12.sp
                )
                Icon(Icons.Rounded.ExpandMore, "Avançado", tint = RiseMuted, modifier = Modifier.size(22.dp).rotate(turn))
            }
            AnimatedVisibility(advanced) {
                GraphicSurface {
                    RiseDropdown(
                        "Codec", ExportOptions.codecs(support), value.codec,
                        expandedInitially = codecMenuOpen, text = { it.label }
                    ) { c ->
                        set(value.copy(codec = c, bitrate = ExportPlan.suggestedBitrate(value.height, value.fps, c, quality)))
                    }
                    if (ExportOptions.usesBitrate(value)) {
                        TileSlider(
                            "Taxa de bits", value.bitrate / 1_000_000f, 1f..150f,
                            default = ExportPlan.suggestedBitrate(value.height, value.fps, value.codec, 1) / 1_000_000f,
                            format = { "%.0f Mbps".format(it) }
                        ) { set(value.copy(bitrate = (it.toInt().coerceIn(1, 150)) * 1_000_000)) }
                    } else {
                        Text(
                            "Taxa de bits fixa do ProRes: ≈ ${ExportOptions.proresMbps(value, value.height * 16 / 9)} Mbps",
                            color = RiseMuted, fontSize = 12.sp
                        )
                    }
                    val depths = ExportOptions.depths(value.codec, support)
                    Label("Profundidade de cor")
                    SegmentedTabs(depths, value.bitDepth, label = { "$it bits" }) { set(value.copy(bitDepth = it)) }
                    val chromas = ExportOptions.chromas(value.codec, value.bitDepth, support)
                    Label("Subamostragem de croma")
                    SegmentedTabs(chromas, value.chroma, label = { it.label }) { set(value.copy(chroma = it)) }
                    if (value.codec == VideoCodec.PRORES) {
                        Label("ProRes 422")
                        SegmentedTabs(ProresProfile.entries.toList(), value.prores, label = { it.label }) {
                            set(value.copy(prores = it))
                        }
                        Label("Áudio no .mov")
                        SegmentedTabs(MovAudio.entries.toList(), value.movAudio, label = { it.label }) {
                            set(value.copy(movAudio = it))
                        }
                    }
                    Text(routeNote(value, support), color = RiseMuted, fontSize = 11.sp, lineHeight = 15.sp)
                }
            }
            if (ExportPlan.upscales(project.copy(export = value))) {
                Text("Resolução acima da fonte só amplia.", color = RiseMuted, fontSize = 11.sp)
            }
        }
        Box(Modifier.fillMaxWidth()) {
            ActionBar(
                listOf(
                    PanelAction(Icons.Rounded.Close, "Cancelar", onClick = onClose),
                    PanelAction(Icons.Rounded.FileUpload, "Escolher destino e exportar", primary = true, onClick = onExport)
                ),
                Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

@Composable
private fun Label(text: String) = Text(text, color = RiseMuted, fontSize = 11.sp)

private fun routeNote(e: ExportSettings, s: EncoderSupport): String = when (ExportOptions.route(e)) {
    ExportRoute.DIRECT ->
        if (e.bitDepth == 10) "HEVC Main10 no codificador do aparelho. Fontes HDR (HLG/PQ) ficam em 10 bits; fontes SDR saem SDR."
        else "Codificador de hardware do aparelho."
    ExportRoute.PRORES ->
        "ProRes por software (FFmpeg prores_ks): mais lento e arquivos grandes. Os efeitos são renderizados num intermediário HEVC de alta taxa e então convertidos — uma geração extra de compressão; o croma vem do caminho 4:2:0 dos efeitos."
    ExportRoute.HARDWARE_422 ->
        "${e.codec.label} 4:2:2 no codificador de hardware (${if (s.apv && e.codec == VideoCodec.APV) "Android 16+" else "detectado"}). Passa por um intermediário HEVC de alta taxa; o croma 4:2:2 é reconstruído a partir do 4:2:0 dos efeitos."
}
