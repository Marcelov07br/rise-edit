package br.com.riseedit.markers

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.riseedit.EditorState
import br.com.riseedit.EditorViewModel
import br.com.riseedit.RiseMuted
import br.com.riseedit.core.Marker
import br.com.riseedit.core.editing.MarkerEdits
import br.com.riseedit.core.editing.YouTubeChapters
import br.com.riseedit.designsystem.ActionBar
import br.com.riseedit.designsystem.GraphicSurface
import br.com.riseedit.designsystem.PanelAction

/** Stateless markers zone (Paparazzi). */
internal class MarkersActions(
    val add: () -> String = { "" },
    val update: (Marker) -> Unit = {},
    val remove: (String) -> Unit = {},
    val seek: (Marker) -> Unit = {},
    val jump: (Boolean) -> Unit = {},
    val copy: (String) -> Unit = {},
    val share: (String) -> Unit = {}
)

@Composable
internal fun MarkersContent(
    markers: List<Marker>,
    durationMs: Long,
    positionMs: Long,
    a: MarkersActions,
    initialSelected: String? = null,
    chaptersOpenInitially: Boolean = false
) {
    var selectedId by remember {
        mutableStateOf(initialSelected ?: markers.minByOrNull { kotlin.math.abs(it.timeMs - positionMs) }?.id)
    }
    var chapters by remember { mutableStateOf(chaptersOpenInitially) }
    val selected = markers.firstOrNull { it.id == selectedId }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            ActionBar(
                listOf(
                    PanelAction(Icons.Rounded.SkipPrevious, "Marcador anterior", markers.isNotEmpty()) { a.jump(false) },
                    PanelAction(Icons.Rounded.SkipNext, "Próximo marcador", markers.isNotEmpty()) { a.jump(true) },
                    PanelAction(Icons.Rounded.BookmarkAdd, "Adicionar marcador (M)", primary = true) { selectedId = a.add() },
                    PanelAction(Icons.Rounded.DeleteOutline, "Remover marcador", selected != null, danger = true) {
                        selected?.let { a.remove(it.id) }
                        selectedId = null
                    }
                )
            )
        }
        if (markers.isEmpty()) {
            GraphicSurface {
                Text("Nenhum marcador. Aperte M (ou Adicionar) na posição da agulha.", color = RiseMuted, fontSize = 14.sp)
            }
        } else {
            GraphicSurface {
                markers.forEach { m ->
                    val on = m.id == selectedId
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(if (on) Color(0xFF2C2C2E) else Color.Transparent)
                            .clickable { selectedId = m.id; a.seek(m) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(Color(m.color)))
                        Spacer(Modifier.width(10.dp))
                        Text(YouTubeChapters.stamp(m.timeMs, durationMs >= 3_600_000), fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp, color = RiseMuted)
                        Spacer(Modifier.width(12.dp))
                        Text(m.label.ifEmpty { "Sem nome" }, fontSize = 15.sp, color = Color.White)
                    }
                }
            }
        }
        selected?.let { m ->
            OutlinedTextField(
                m.label,
                { a.update(m.copy(label = it.take(100))) },
                Modifier.fillMaxWidth(),
                label = { Text("Nome do marcador") },
                singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MarkerEdits.COLORS.forEach { c ->
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(Color(c))
                            .border(if (c == m.color) 3.dp else 0.dp, Color.White, CircleShape)
                            .clickable { a.update(m.copy(color = c)) }
                    )
                }
            }
        }
        if (!chapters) {
            OutlinedButton({ chapters = true }, Modifier.fillMaxWidth().height(52.dp)) { Text("Exportar capítulos do YouTube") }
        } else {
            ChaptersBox(YouTubeChapters.build(markers, durationMs), a)
        }
    }
}

@Composable
private fun ChaptersBox(r: YouTubeChapters.Result, a: MarkersActions) {
    GraphicSurface {
        Text("Capítulos do YouTube", fontSize = 13.sp, color = RiseMuted)
        Text(r.text.ifEmpty { "—" }, fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = Color.White)
        r.problems.forEach { p ->
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.WarningAmber, null, Modifier.size(18.dp), tint = Color(0xFFFFB340))
                Spacer(Modifier.width(8.dp))
                Text(p, fontSize = 13.sp, color = Color(0xFFFFB340))
            }
        }
        r.notes.forEach { Text(it, fontSize = 12.sp, color = RiseMuted) }
        if (r.ok) Text("Pronto para colar na descrição do vídeo.", fontSize = 12.sp, color = RiseMuted)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button({ a.copy(r.text) }, Modifier.weight(1f).height(52.dp), enabled = r.chapters.isNotEmpty()) {
            Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Copiar")
        }
        OutlinedButton({ a.share(r.text) }, Modifier.weight(1f).height(52.dp), enabled = r.chapters.isNotEmpty()) {
            Icon(Icons.Rounded.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Compartilhar")
        }
    }
}

@Composable
internal fun MarkersPanel(vm: EditorViewModel, state: EditorState) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    MarkersContent(
        state.project.markers,
        state.project.durationMs,
        state.positionMs,
        MarkersActions(
            add = vm::addMarker,
            update = vm::updateMarker,
            remove = vm::removeMarker,
            seek = vm::seekMarker,
            jump = vm::jumpMarker,
            copy = {
                clipboard.setText(AnnotatedString(it))
                vm.message("Capítulos copiados.")
            },
            share = { text ->
                runCatching {
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                                .putExtra(Intent.EXTRA_SUBJECT, "Capítulos · ${state.project.name}"),
                            "Compartilhar capítulos"
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        )
    )
}
