@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class, androidx.media3.common.util.ExperimentalApi::class])
package br.com.riseedit

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

/**
 * Editor layout, top to bottom: title bar, preview (takes the remaining height), transport bar, a timeline that
 * is always visible, and either the tool toolbar or the sheet of the tool being edited. Nothing scrolls the page:
 * the timeline and the tool sheet scroll internally.
 */
@Composable
fun EditorScreen(vm: EditorViewModel, state: EditorState, onBack: () -> Unit, onSettings: () -> Unit) {
    var tool by rememberSaveable { mutableStateOf<String?>(null) }
    var full by remember { mutableStateOf(false) }
    var exportDialog by remember { mutableStateOf(false) }
    var audioTrack by remember { mutableIntStateOf(0) }
    var overlayTrack by remember { mutableIntStateOf(0) }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.import(it) }
    val audio = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.importAudio(it, audioTrack) }
    val overlay = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.importOverlay(it, overlayTrack) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { it?.let(vm::export) }
    val enabled = state.busy == null
    val hasClips = state.project.clips.isNotEmpty()

    LaunchedEffect(state.selection) {
        state.selection?.let { id ->
            tool = when {
                state.project.audio.any { it.id == id } -> "Áudio"
                state.project.texts.any { it.id == id } -> "Texto"
                state.project.overlays.any { it.id == id } -> "Overlay"
                else -> tool
            }
        }
    }
    LaunchedEffect(state.message) {
        if (state.message != null) { delay(6000); vm.message(null) }
    }
    LaunchedEffect(hasClips) { if (!hasClips) tool = null }

    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        val sheetMax = maxHeight * 0.42f
        Column(Modifier.fillMaxSize()) {
            EditorTopBar(
                name = state.project.name,
                enabled = enabled,
                canExport = enabled && hasClips,
                onBack = onBack,
                onSettings = onSettings,
                onExport = { exportDialog = true }
            )
            if (!hasClips) {
                Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.Center) {
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Vamos criar seu vídeo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Escolha seus vídeos. Depois corte, adicione música e estabilize as cenas.")
                            Button(onClick = { import.launch(arrayOf("video/*")) }, enabled = enabled, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Importar vídeos") }
                        }
                    }
                }
            } else {
                if (full) Box(Modifier.weight(1f))
                else PreviewPanel(vm, state, Modifier.weight(1f).fillMaxWidth().heightIn(min = 120.dp).padding(horizontal = 12.dp))
                TransportBar(vm, state, enabled, onFullscreen = { full = true })
                Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    TimelinePanel(vm, state, trackAreaHeight = if (tool == null) 190.dp else 110.dp)
                }
                val current = tool
                if (current == null) {
                    BottomToolbar(
                        enabled = enabled,
                        layerSelected = state.selection != null,
                        onImport = { import.launch(arrayOf("video/*")) },
                        onTool = { tool = it },
                        onSplit = { vm.split() },
                        onDelete = { if (state.selection != null) vm.removeLayer() else vm.remove() }
                    )
                } else {
                    ToolSheet(title = toolTitle(current), maxContentHeight = sheetMax, onClose = { tool = null }) {
                        ToolPanel(
                            current, vm, state,
                            chooseAudioTrack = { audioTrack = it },
                            chooseOverlayTrack = { overlayTrack = it },
                            addAudio = { audio.launch(arrayOf("audio/*")) },
                            addOverlay = { overlay.launch(arrayOf("video/*")) }
                        )
                    }
                }
            }
        }
        state.message?.let { text ->
            Surface(
                Modifier.align(Alignment.TopCenter).padding(top = 60.dp, start = 12.dp, end = 12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 6.dp
            ) {
                Row(Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(text, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { vm.message(null) }) { Text("OK") }
                }
            }
        }
    }

    if (state.busy != null) Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Card {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(state.busy, style = MaterialTheme.typography.titleMedium)
                LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                Text("${(state.progress * 100).toInt()}%")
                OutlinedButton(onClick = vm::cancel) { Text("Cancelar") }
            }
        }
    }
    if (full) Dialog(onDismissRequest = { full = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            Column(Modifier.safeDrawingPadding()) {
                TextButton(onClick = { full = false }) { Text("Fechar tela cheia") }
                PreviewPanel(vm, state, Modifier.weight(1f).fillMaxWidth())
                Button(onClick = vm::togglePlay, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(if (state.playing) "Pausar" else "Reproduzir") }
            }
        }
    }
    if (exportDialog) ExportDialog(
        state.project.export,
        onClose = { exportDialog = false },
        onExport = { settings ->
            vm.projectSettings(state.project.aspectRatio, settings)
            exportDialog = false
            export.launch(state.project.name.replace(Regex("[^\\p{L}\\p{N} _-]"), "") + ".mp4")
        }
    )
}

@Composable
private fun EditorTopBar(name: String, enabled: Boolean, canExport: Boolean, onBack: () -> Unit, onSettings: () -> Unit, onExport: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack, enabled = enabled) { Text("‹ Projetos") }
        Text(name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
        TextButton(onClick = onSettings, modifier = Modifier.semantics { contentDescription = "Configurações e atualizações" }) { Text("⚙") }
        Button(onClick = onExport, enabled = canExport) { Text("Exportar") }
    }
}
