package br.com.riseedit.tools

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.riseedit.ChoiceRow
import br.com.riseedit.EditorState
import br.com.riseedit.EditorViewModel
import br.com.riseedit.Experiments
import br.com.riseedit.color.GradeLuts
import br.com.riseedit.core.WhisperModel
import br.com.riseedit.core.color.LogProfile
import br.com.riseedit.core.color.Meters
import br.com.riseedit.core.color.ToneCurve
import br.com.riseedit.subtitles.SubtitleJobs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Log input, fixed tone curves and user .cube LUTs (no sliders: steps only). */
@Composable
internal fun GradePanel(vm: EditorViewModel, state: EditorState) {
    val clip = state.project.clips.getOrNull(state.selected) ?: return
    val g = clip.grade
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val result =
                    withContext(Dispatchers.IO) { runCatching { GradeLuts.import(context, uri) } }
                result.onSuccess { (file, name) ->
                    vm.clipSettings(
                        clip.copy(grade = g.copy(lutUri = file, lutName = name, lutIntensity = 1f))
                    )
                }.onFailure { vm.message("LUT inválido: ${it.message}") }
            }
        }
    val known = state.project.clips.mapNotNull { c -> c.grade.lutUri?.let { it to (c.grade.lutName ?: "LUT") } }.distinctBy { it.first }
    GradeControls(g, known, { vm.clipSettings(clip.copy(grade = it)) }, { picker.launch(arrayOf("*/*")) })
}

private const val IMPORT = "\u0000import"

/** Log/curve/LUT as rounded dropdowns + LUT order. Stateless (Paparazzi). */
@Composable
internal fun GradeControls(
    g: br.com.riseedit.core.color.ColorGrade,
    knownLuts: List<Pair<String, String>>,
    change: (br.com.riseedit.core.color.ColorGrade) -> Unit,
    importLut: () -> Unit,
    lutMenuOpen: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        br.com.riseedit.designsystem.RiseDropdown("Perfil log da câmera", LogProfile.entries.toList(), g.log, text = { it.label }) {
            change(g.copy(log = it))
        }
        br.com.riseedit.designsystem.RiseDropdown("Curva", ToneCurve.entries.toList(), g.curve, text = { it.label }) {
            change(g.copy(curve = it))
        }
        val names = (knownLuts + listOfNotNull(g.lutUri?.let { it to (g.lutName ?: "LUT") })).distinctBy { it.first }.toMap()
        val options = listOf<String?>(null) + names.keys + IMPORT
        br.com.riseedit.designsystem.RiseDropdown(
            "LUT", options, g.lutUri, expandedInitially = lutMenuOpen,
            text = { when (it) { null -> "Nenhum"; IMPORT -> "Importar .cube…"; else -> names[it] ?: "LUT" } }
        ) {
            when (it) {
                IMPORT -> importLut()
                null -> change(g.copy(lutUri = null, lutName = null))
                else -> change(g.copy(lutUri = it, lutName = names[it]))
            }
        }
        if (g.lutUri != null) {
            Text("Intensidade do LUT", color = br.com.riseedit.RiseMuted, style = MaterialTheme.typography.labelMedium)
            ChoiceRow(listOf(0.25f, 0.5f, 0.75f, 1f), g.lutIntensity, { change(g.copy(lutIntensity = it)) }) {
                "${(it * 100).toInt()}%"
            }
        }
        Text("Ordem", color = br.com.riseedit.RiseMuted, style = MaterialTheme.typography.labelMedium)
        ChoiceRow(listOf(false, true), g.lutBeforeAdjust, { change(g.copy(lutBeforeAdjust = it)) }) {
            if (it) "LUT antes dos ajustes" else "LUT depois dos ajustes"
        }
        Text(
            "Grade: log → LUT → curva. Para Samsung Log use o LUT oficial com perfil Nenhum. " +
                "HDR (HLG/PQ) é convertido para SDR antes.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private class MeterData(
    val frame: Bitmap,
    val histogram: IntArray,
    val wave: Array<IntArray>,
    val clipped: Double
)

/** Histogram, waveform and zebra of the graded frame under the playhead (CPU, ~240 px). */
@Composable
internal fun MetersPanel(vm: EditorViewModel, state: EditorState) {
    val context = LocalContext.current
    val clip = vm.previewClip
    if (clip == null) {
        Text("Sem clipe no cursor.", style = MaterialTheme.typography.bodySmall)
        return
    }
    val sourceMs = vm.previewSourceMs
    var data by remember { mutableStateOf<MeterData?>(null) }
    LaunchedEffect(clip.id, clip.grade, clip.adjustments, sourceMs / 250, state.project.modifiedAt) {
        data = withContext(Dispatchers.Default) { measure(context, clip, sourceMs) }
    }
    val d = data ?: return Text("Lendo quadro…", style = MaterialTheme.typography.bodySmall)
    Row(Modifier.height(96.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Image(d.frame.asImageBitmap(), "Zebra", Modifier.weight(1f))
        Canvas(Modifier.weight(1f).height(96.dp)) {
            val max = (d.histogram.maxOrNull() ?: 1).coerceAtLeast(1).toFloat()
            val bw = size.width / 256f
            d.histogram.forEachIndexed { i, v ->
                val h = size.height * v / max
                drawRect(Color.White, Offset(i * bw, size.height - h), Size(bw, h))
            }
        }
        Canvas(Modifier.weight(1f).height(96.dp)) {
            if (d.wave.isEmpty()) return@Canvas
            val cw = size.width / d.wave.size
            val lh = size.height / d.wave[0].size
            val peak = d.wave.maxOf { it.maxOrNull() ?: 0 }.coerceAtLeast(1).toFloat()
            d.wave.forEachIndexed { c, column ->
                column.forEachIndexed { l, count ->
                    if (count > 0) {
                        val alpha = (0.25f + 0.75f * count / peak).coerceAtMost(1f)
                        drawRect(
                            Color.White.copy(alpha = alpha),
                            Offset(c * cw, size.height - (l + 1) * lh),
                            Size(cw, lh)
                        )
                    }
                }
            }
        }
    }
    Text(
        "Zebra ≥95% · estourado: ${"%.1f".format(
            d.clipped * 100
        )}% · quadro com a cor do clipe aplicada.",
        style = MaterialTheme.typography.bodySmall
    )
}

private fun measure(
    context: android.content.Context,
    clip: br.com.riseedit.core.Clip,
    sourceMs: Long
): MeterData? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, Uri.parse(clip.effectiveUri(true)))
        val bmp = retriever.getScaledFrameAtTime(
            sourceMs * 1000,
            MediaMetadataRetriever.OPTION_CLOSEST,
            240,
            240
        ) ?: return null
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        bmp.recycle()
        for (i in px.indices) px[i] = GradeLuts.applyFull(clip.adjustments, clip.grade, px[i])
        val zebra = Meters.zebra(px)
        val shown = px.copyOf()
        for (i in shown.indices) {
            if (zebra[i] && ((i % w + i / w) / 3) % 2 == 0) shown[i] = 0xFF000000.toInt()
        }
        MeterData(
            Bitmap.createBitmap(shown, w, h, Bitmap.Config.ARGB_8888),
            Meters.histogram(px),
            Meters.waveform(px, w, 96),
            Meters.clipped(px)
        )
    } catch (error: RuntimeException) {
        br.com.riseedit.diagnostics.Diagnostics.log("medidores", "quadro indisponível", error)
        null
    } finally {
        retriever.release()
    }
}

private val languages = listOf("pt", "auto", "en", "es")

/** Whisper on-device captions as text layers; any model size (downloaded on demand). */
@Composable
internal fun SubtitlesPanel(vm: EditorViewModel, state: EditorState) {
    val context = LocalContext.current
    val status by SubtitleJobs.status.collectAsStateWithLifecycle()
    var model by remember { mutableStateOf(WhisperModel.BASE) }
    var language by remember { mutableStateOf("pt") }
    var downloaded by remember(status.running) { mutableStateOf(SubtitleJobs.downloaded(context)) }
    ChoiceRow(WhisperModel.entries.toList(), model, { model = it }) {
        "${it.label} ${if (it in downloaded) "✓" else "· ${it.approxMb} MB"}"
    }
    ChoiceRow(languages, language, { language = it }) {
        if (it ==
            "auto"
        ) {
            "Detectar"
        } else {
            it.uppercase()
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (status.running) {
            OutlinedButton(onClick = SubtitleJobs::cancel) { Text("Cancelar") }
        } else {
            OutlinedButton(onClick = {
                SubtitleJobs.start(context, state.project, model, language, vm::addSubtitles)
            }, enabled = state.project.clips.isNotEmpty()) {
                Text(if (model in downloaded) "Gerar legendas" else "Baixar e gerar")
            }
            if (model in downloaded) {
                OutlinedButton(onClick = {
                    SubtitleJobs.delete(context, model)
                    downloaded = SubtitleJobs.downloaded(context)
                }) { Text("Apagar modelo") }
            }
        }
    }
    if (status.running) LinearProgressIndicator(progress = { status.progress })
    if (status.text.isNotEmpty()) Text(status.text, style = MaterialTheme.typography.bodySmall)
    Text(
        "Roda no aparelho (whisper.cpp). Usa o áudio dos clipes principais; modelos maiores são mais precisos e mais lentos.",
        style = MaterialTheme.typography.bodySmall
    )
}

/** J/L cut presets for separated audio. */
@Composable
internal fun JLCutRows(vm: EditorViewModel, audio: br.com.riseedit.core.AudioClip) {
    if (audio.linkedClipId == null) return
    val steps = listOf(0L, 250L, 500L, 1000L, 2000L)
    fun label(ms: Long) = if (ms == 0L) "0" else "${ms / 1000.0}s"
    Text("Corte J (áudio entra antes)", style = MaterialTheme.typography.labelMedium)
    ChoiceRow(steps, steps.minBy { kotlin.math.abs(it - audio.leadMs) }, {
        vm.jlCut(audio.id, it, audio.tailMs)
    }, ::label)
    Text("Corte L (áudio continua depois)", style = MaterialTheme.typography.labelMedium)
    ChoiceRow(steps, steps.minBy { kotlin.math.abs(it - audio.tailMs) }, {
        vm.jlCut(audio.id, audio.leadMs, it)
    }, ::label)
}

/** Gyro experiment readout (flag-gated): does this file carry motion telemetry? */
@Composable
internal fun TelemetryReadout(state: EditorState) {
    val context = LocalContext.current
    if (!Experiments.gyro(context)) return
    val clip = state.project.clips.getOrNull(state.selected) ?: return
    var text by remember(clip.uri) { mutableStateOf("Experimento giroscópio: lendo…") }
    LaunchedEffect(clip.uri) {
        val report = withContext(Dispatchers.IO) { Experiments.telemetry(context, clip.uri) }
        text = when {
            report == null -> "Experimento giroscópio: arquivo ilegível."
            report.kinds.isEmpty() -> "Experimento giroscópio: sem telemetria no arquivo."
            else -> "Experimento giroscópio: ${report.kinds.joinToString { it.label }}" +
                if (report.hasGyro) " (dados utilizáveis)" else " (sem leitor)"
        }
        br.com.riseedit.diagnostics.Diagnostics.log(
            "giroscópio",
            "${clip.name}: ${report?.boxes?.take(80)}"
        )
    }
    Text(text, style = MaterialTheme.typography.bodySmall)
}
