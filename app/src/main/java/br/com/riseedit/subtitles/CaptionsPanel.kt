package br.com.riseedit.subtitles

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.riseedit.RiseMuted
import br.com.riseedit.core.CaptionWords
import br.com.riseedit.core.TextLayer
import br.com.riseedit.core.WhisperModel
import br.com.riseedit.designsystem.ActionBar
import br.com.riseedit.designsystem.GraphicSurface
import br.com.riseedit.designsystem.PanelAction
import br.com.riseedit.designsystem.PanelIconButton
import br.com.riseedit.designsystem.RiseDropdown
import br.com.riseedit.tools.Tile
import br.com.riseedit.tools.TileGrid

internal val CAPTION_LANGUAGES = listOf("pt", "auto", "en", "es")

internal fun languageLabel(code: String) = when (code) {
    "pt" -> "Português (BR)"
    "auto" -> "Detectar"
    "en" -> "English"
    "es" -> "Español"
    else -> code.uppercase()
}

/** Stateless captions zone (Paparazzi without a ViewModel / context). */
internal class CaptionsActions(
    val status: SubtitleStatus = SubtitleStatus(),
    val downloaded: List<WhisperModel> = emptyList(),
    val hasClips: Boolean = true,
    val start: (WhisperModel, String) -> Unit = { _, _ -> },
    val cancel: () -> Unit = {},
    val delete: (WhisperModel) -> Unit = {},
    /** Selected caption layer (word editor), null = none selected. */
    val selected: TextLayer? = null,
    val editText: (TextLayer) -> Unit = {}
)

@Composable
internal fun CaptionsContent(
    a: CaptionsActions,
    initialModel: WhisperModel = WhisperModel.DEFAULT,
    infoOpenInitially: Boolean = false,
    initialWord: Int = 0
) {
    var model by remember { mutableStateOf(initialModel) }
    var language by remember { mutableStateOf("pt") }
    var info by remember { mutableStateOf(infoOpenInitially) }
    val have = model in a.downloaded
    val running = a.status.running
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            ActionBar(
                listOfNotNull(
                    if (running) PanelAction(Icons.Rounded.Close, "Cancelar", primary = true, onClick = a.cancel) else null,
                    PanelAction(Icons.Rounded.Info, "Sobre as legendas", checked = info) { info = !info },
                    PanelAction(
                        Icons.Rounded.DeleteOutline,
                        "Apagar modelo ${model.label}",
                        have && !running,
                        danger = true
                    ) { a.delete(model) }
                )
            )
        }
        RiseDropdown(
            "Modelo",
            WhisperModel.entries.toList(),
            model,
            text = { "${it.label} · ${it.approxMb} MB" + if (it in a.downloaded) " ✓" else "" }
        ) { model = it }
        RiseDropdown("Idioma", CAPTION_LANGUAGES, language, text = ::languageLabel) { language = it }
        AnimatedVisibility(info) {
            GraphicSurface {
                Text(
                    "Roda no aparelho (whisper.cpp, MIT). O modelo é baixado uma vez, conferido por " +
                        "SHA-256 e fica no app; o APK não o inclui. Cada legenda guarda o tempo de cada palavra.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        if (running || a.status.text.isNotEmpty()) {
            GraphicSurface {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (have && !running) {
                        Icon(Icons.Rounded.Verified, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(a.status.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    if (running) Text("${(a.status.progress * 100).toInt()}%", fontSize = 13.sp, color = RiseMuted)
                }
                if (running) LinearProgressIndicator(progress = { a.status.progress }, modifier = Modifier.fillMaxWidth())
            }
        }
        Button(
            onClick = { a.start(model, language) },
            enabled = !running && a.hasClips,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text(if (have) "Gerar legendas" else "Baixar ${model.approxMb} MB e gerar") }
        a.selected?.takeIf { it.words.isNotEmpty() }?.let { WordEditor(it, a.editText, initialWord) }
    }
}

/** Word timings of the selected caption: tap a word, then nudge its start/end (local ms). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordEditor(layer: TextLayer, edit: (TextLayer) -> Unit, initialWord: Int) {
    var index by remember(layer.id) { mutableIntStateOf(initialWord.coerceIn(0, layer.words.lastIndex)) }
    val w = layer.words[index.coerceIn(0, layer.words.lastIndex)]
    val span = (layer.endMs - layer.startMs).toFloat()
    GraphicSurface {
        Text("Palavras da legenda", fontSize = 13.sp, color = RiseMuted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            layer.words.forEachIndexed { i, word ->
                val on = i == index
                val accent = MaterialTheme.colorScheme.primary
                Text(
                    word.text,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp))
                        .background(if (on) Color(CaptionWords.HIGHLIGHT).copy(alpha = 0.22f) else Color(0xFF2C2C2E))
                        .border(1.dp, if (on) Color(CaptionWords.HIGHLIGHT) else Color.Transparent, RoundedCornerShape(10.dp))
                        .clickable { index = i }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    color = if (on) Color(CaptionWords.HIGHLIGHT) else Color.White,
                    fontSize = 14.sp
                )
            }
        }
    }
    TileGrid(
        listOf(
            Tile("Início", w.startMs.toFloat(), 0f..span, w.startMs.toFloat(), format = { "%.2f s".format(it / 1000) }) {
                edit(CaptionWords.retime(layer, index, it.toLong(), w.endMs))
            },
            Tile("Fim", w.endMs.toFloat(), 0f..span, w.endMs.toFloat(), format = { "%.2f s".format(it / 1000) }) {
                edit(CaptionWords.retime(layer, index, w.startMs, it.toLong()))
            }
        )
    )
}
