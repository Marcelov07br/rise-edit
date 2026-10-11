@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class, androidx.media3.common.util.ExperimentalApi::class])
package br.com.riseedit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.PlayerView
import br.com.riseedit.core.*
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val editor by viewModels<EditorViewModel>()
    private val updates by viewModels<UpdateViewModel>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFB9FF66), onPrimary = Color(0xFF173000),
                background = Color(0xFF101116), surface = Color(0xFF1B1D24), secondary = Color(0xFFBFC5D2))) {
                EditorScreen(editor, updates) { updates.installationIntent()?.let { intent ->
                    runCatching { startActivity(intent) }.onFailure { editor.message("Não foi possível abrir o instalador do Android.") }
                } }
            }
        }
    }
    override fun onStart() { super.onStart(); updates.onOpen() }
    override fun onStop() { editor.player.pause(); super.onStop() }
}

fun time(ms: Long): String = String.format(Locale.US, "%02d:%02d.%01d", ms / 60000, (ms / 1000) % 60, (ms % 1000) / 100)

@Composable
private fun EditorScreen(vm: EditorViewModel, updateVm: UpdateViewModel, install: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val update by updateVm.state.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.import(it) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { it?.let(vm::export) }
    val enabled = state.busy == null
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("RISE EDIT", fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    Text("Seu movimento. Seu corte.", style = MaterialTheme.typography.labelMedium)
                }
                TextButton(onClick = { showSettings = true }) { Text(if (update.release != null) "Config. • nova versão" else "Configurações") }
            }
            if (state.project.clips.isEmpty()) {
                Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF1B1D24)) {
                    Column(Modifier.fillMaxWidth().height(220.dp).padding(24.dp), verticalArrangement = Arrangement.Center) {
                        Text("Comece com um vídeo", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("Monte sua timeline, corte os clipes e experimente um movimento mais suave. Seus originais ficam preservados.")
                    }
                }
            } else AndroidView(factory = { context -> PlayerView(context).apply { player = vm.player; useController = false; setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING) } },
                update = { it.player = vm.player }, modifier = Modifier.fillMaxWidth().height(220.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { import.launch(arrayOf("video/*")) }, enabled = enabled, modifier = Modifier.weight(1f)) { Text("+ Importar") }
                OutlinedButton(onClick = { export.launch("Rise-Edit-${System.currentTimeMillis()}.mp4") }, enabled = enabled && state.project.clips.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Exportar 720p") }
            }
            if (state.busy != null) {
                Text(state.busy!!)
                LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = vm::cancel) { Text("Cancelar") }
            }
            if (state.project.clips.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    FilledTonalButton(onClick = vm::togglePlay, enabled = enabled) { Text(if (state.playing) "Pausar" else "Reproduzir") }
                    Spacer(Modifier.weight(1f))
                    Text("${time(state.positionMs)} / ${time(state.project.durationMs)}", style = MaterialTheme.typography.labelLarge)
                }
                Slider(value = state.positionMs.toFloat().coerceIn(0f, state.project.durationMs.toFloat()), onValueChange = { vm.seek(it.toLong()) },
                    valueRange = 0f..maxOf(1f, state.project.durationMs.toFloat()), enabled = enabled)
                Text("TIMELINE · ${state.project.clips.size} clipes", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(state.project.clips, key = { _, clip -> clip.id }) { index, clip ->
                        Surface(shape = RoundedCornerShape(10.dp), color = if (state.selected == index) Color(0xFF34432B) else Color(0xFF252832),
                            border = BorderStroke(1.dp, if (state.selected == index) MaterialTheme.colorScheme.primary else Color(0xFF3D414D)),
                            modifier = Modifier.width((110 + clip.durationMs / 150).coerceAtMost(260).toInt().dp).clickable(enabled = enabled) { vm.select(index) }) {
                            Column(Modifier.padding(12.dp)) {
                                Text("${index + 1} · ${clip.name}", maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                                Text(time(clip.durationMs), style = MaterialTheme.typography.labelMedium)
                                Text(if (clip.stabilization.enabled) "Estabilização global" else "Original", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = vm::split, enabled = enabled) { Text("Dividir no cursor") }
                    OutlinedButton(onClick = { vm.move(-1) }, enabled = enabled && state.selected > 0) { Text("← Mover") }
                    OutlinedButton(onClick = { vm.move(1) }, enabled = enabled && state.selected < state.project.clips.lastIndex) { Text("Mover →") }
                    OutlinedButton(onClick = vm::remove, enabled = enabled) { Text("Remover") }
                }
                val clip = state.project.clips.getOrNull(state.selected)
                clip?.let { ClipControls(it, enabled, it.uri in state.analyzed, vm) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = vm::undo, enabled = enabled && state.canUndo) { Text("Desfazer") }
                    TextButton(onClick = vm::redo, enabled = enabled && state.canRedo) { Text("Refazer") }
                }
                Text("Projeto salvo automaticamente no aparelho. Exportação MP4 H.264/AAC, altura de 720 pixels.", style = MaterialTheme.typography.bodySmall)
            }
            state.message?.let { message ->
                Surface(color = Color(0xFF272D36), shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { vm.message(null) }) { Text("OK") }
                    }
                }
            }
        }
    }
    if (showSettings) UpdateSettings(updateVm, update, enabled, install) { showSettings = false }
}

@Composable
private fun ClipControls(clip: Clip, enabled: Boolean, analyzed: Boolean, vm: EditorViewModel) {
    var range by remember(clip.id, clip.startMs, clip.endMs) { mutableStateOf(clip.startMs.toFloat()..clip.endMs.toFloat()) }
    Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFF1B1D24)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(clip.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Aparar · ${time(range.start.toLong())} → ${time(range.endInclusive.toLong())}")
            RangeSlider(value = range, onValueChange = { range = it }, valueRange = 0f..clip.sourceDurationMs.toFloat(), enabled = enabled)
            TextButton(onClick = { vm.trim(range.start.toLong(), range.endInclusive.toLong()) }, enabled = enabled && range.endInclusive - range.start >= 100) { Text("Aplicar corte") }
            HorizontalDivider()
            Text("Estabilização global · experimental", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("Quadro inteiro: deslocamento e rotação. Vídeos de até 2 minutos. Confira o resultado em cenas com pessoas em movimento ou pouca textura.", style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (analyzed) "Movimento analisado" else "Analise antes de ativar", modifier = Modifier.weight(1f))
                Switch(checked = clip.stabilization.enabled, onCheckedChange = { vm.settings(clip.stabilization.copy(enabled = it)) }, enabled = enabled && analyzed)
            }
            OutlinedButton(onClick = vm::analyze, enabled = enabled && clip.sourceDurationMs <= 120000) { Text(if (analyzed) "Reanalisar movimento" else "Analisar e estabilizar") }
            if (analyzed) StabilizationControls(clip.stabilization, enabled, vm::settings)
        }
    }
}

@Composable
private fun StabilizationControls(settings: Stabilization, enabled: Boolean, change: (Stabilization) -> Unit) {
    var strength by remember(settings) { mutableFloatStateOf(settings.strength.toFloat()) }
    var smooth by remember(settings) { mutableFloatStateOf(settings.smoothSeconds.toFloat()) }
    var zoom by remember(settings) { mutableFloatStateOf(settings.maxZoom.toFloat()) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = settings.goal == MotionGoal.SMOOTH, onClick = { change(settings.copy(goal = MotionGoal.SMOOTH)) }, label = { Text("Suavizar") }, enabled = enabled)
        FilterChip(selected = settings.goal == MotionGoal.FIXED, onClick = { change(settings.copy(goal = MotionGoal.FIXED)) }, label = { Text("Tentar câmera fixa") }, enabled = enabled)
    }
    Text("Intensidade · ${(strength * 100).toInt()}%")
    Slider(value = strength, onValueChange = { strength = it }, onValueChangeFinished = { change(settings.copy(strength = strength.toDouble())) }, valueRange = 0f..1f, enabled = enabled)
    Text("Suavidade · ${String.format(Locale.US, "%.1f", smooth)} s")
    Slider(value = smooth, onValueChange = { smooth = it }, onValueChangeFinished = { change(settings.copy(smoothSeconds = smooth.toDouble().coerceIn(0.1, 2.0))) }, valueRange = 0.1f..2f, enabled = enabled && settings.goal == MotionGoal.SMOOTH)
    Text("Limite de zoom · ${String.format(Locale.US, "%.2f", zoom)}×")
    Slider(value = zoom, onValueChange = { zoom = it }, onValueChangeFinished = { change(settings.copy(maxZoom = zoom.toDouble().coerceIn(1.0, 1.25))) }, valueRange = 1f..1.25f, enabled = enabled)
    Text("Se o movimento exceder o limite, a correção será reduzida. Câmera fixa é uma tentativa; pode restar movimento. Correção por regiões ou malha ainda não está disponível.", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun UpdateSettings(vm: UpdateViewModel, state: UpdateState, canInstall: Boolean, install: () -> Unit, close: () -> Unit) {
    var token by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = close, title = { Text("Configurações") }, confirmButton = { TextButton(onClick = close) { Text("Fechar") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Rise Edit ${state.installedVersion} · Atualizações pelo GitHub", fontWeight = FontWeight.Bold)
                Text(UpdatePolicy.REPOSITORY, style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Buscar automaticamente ao abrir", modifier = Modifier.weight(1f))
                    Switch(checked = state.automatic, onCheckedChange = vm::automatic)
                }
                Text("Repositório privado: use um token fine-grained, limitado a rise-edit, com Contents: Read-only. A credencial fica criptografada no aparelho.", style = MaterialTheme.typography.bodySmall)
                if (!state.connected) {
                    OutlinedTextField(value = token, onValueChange = { token = it }, label = { Text("Token GitHub") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), enabled = !state.busy)
                    Button(onClick = { vm.connect(token); token = "" }, enabled = token.isNotBlank() && !state.busy) { Text("Conectar GitHub") }
                } else TextButton(onClick = vm::disconnect, enabled = !state.busy) { Text("Desconectar GitHub") }
                OutlinedButton(onClick = vm::check, enabled = !state.busy) { Text("Buscar atualização agora") }
                Text(state.message, style = MaterialTheme.typography.bodyMedium)
                if (state.busy) LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                state.release?.let { release ->
                    Text(release.notes, style = MaterialTheme.typography.bodySmall)
                    if (state.ready == null) Button(onClick = vm::download, enabled = !state.busy) { Text("Baixar ${release.tag}") }
                }
                if (state.ready != null) Button(onClick = install, enabled = canInstall) { Text("Instalar atualização") }
                Text("A primeira instalação usa um APK. As seguintes são baixadas aqui. O Android exige autorização para instalar; depois de concedê-la, toque em instalar novamente.", style = MaterialTheme.typography.bodySmall)
            }
        })
}
