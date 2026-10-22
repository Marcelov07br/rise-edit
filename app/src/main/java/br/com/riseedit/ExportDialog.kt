package br.com.riseedit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.riseedit.core.ExportPlan
import br.com.riseedit.core.ExportSettings
import br.com.riseedit.core.Project
import br.com.riseedit.core.VideoCodec

private val bitrateSteps = listOf(2, 4, 6, 8, 12, 16, 20, 30, 40, 50, 60, 80, 100, 120, 150)

@Suppress("LongMethod")
@Composable
fun ExportDialog(project: Project, onClose: () -> Unit, onExport: (ExportSettings) -> Unit) {
    val allowedFps = remember(project) { ExportPlan.allowedFps(project) }
    var value by remember {
        val e = project.export
        mutableStateOf(
            e.copy(
                fps = if (e.fps in
                    allowedFps
                ) {
                    e.fps
                } else {
                    (allowedFps.maxOrNull() ?: 30)
                }
            )
        )
    }
    val mbps = value.bitrate / 1_000_000
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Exportar vídeo") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Resolução")
                ChoiceRow(ExportSettings.HEIGHTS, value.height, {
                    value = value.copy(height = it)
                }) {
                    if (it == 2160) "4K" else "${it}p"
                }
                Text("Codec")
                ChoiceRow(VideoCodec.entries.toList(), value.codec, {
                    value = value.copy(codec = it)
                }) { it.label }
                Text("Limite de quadros por segundo")
                ChoiceRow(allowedFps, value.fps, {
                    value =
                        value.copy(fps = it, ntsc = value.ntsc && it in ExportSettings.NTSC_BASES)
                }) {
                    br.com.riseedit.core.FrameRateChoice(
                        it,
                        value.ntsc && it in ExportSettings.NTSC_BASES
                    ).label
                }
                Text("Qualidade")
                ChoiceRow(listOf(0, 1, 2), -1, {
                    value =
                        value.copy(
                            bitrate = ExportPlan.suggestedBitrate(
                                value.height,
                                value.fps,
                                value.codec,
                                it
                            )
                        )
                }) { listOf("Leve", "Boa", "Alta")[it] }
                Text("Taxa de bits: $mbps Mbps")
                ChoiceRow(bitrateSteps, bitrateSteps.minBy { kotlin.math.abs(it - mbps) }, {
                    value = value.copy(bitrate = it * 1_000_000)
                }) { "$it" }
                val fpsNote = ExportPlan.sourceFps(project).takeIf { it > 0f }
                    ?.let { " Fonte: %.0f fps; 60 fps só aparece se a fonte tiver.".format(it) }
                    ?: ""
                val upscale = if (ExportPlan.upscales(project.copy(export = value))) {
                    " Atenção: resolução acima da fonte só amplia."
                } else {
                    ""
                }
                Text(
                    "MP4 · ${value.codec.label} / AAC. Se o aparelho não codificar a escolha, o Media3 usa a mais próxima.$fpsNote$upscale",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = { Button(onClick = { onExport(value) }) { Text("Escolher destino") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancelar") } }
    )
}
