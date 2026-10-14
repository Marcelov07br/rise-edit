package br.com.riseedit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun EditorDialogs(
    vm: EditorViewModel,
    state: EditorState,
    ui: EditorUiState,
    media: EditorMediaActions
) {
    state.busy?.let { ProcessingDialog(it, state.progress, vm::cancel) }
    if (ui.full) FullscreenPreview(vm, state, onClose = { ui.full = false })
    if (ui.exportDialog) {
        ExportDialog(
            state.project.export,
            onClose = { ui.exportDialog = false },
            onExport = media.export
        )
    }
    if (ui.recoveryDialog) RecoveryDialog(vm, state, ui)
}

@Composable
private fun ProcessingDialog(phase: String, progress: Float, onCancel: () -> Unit) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Card {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(phase, style = MaterialTheme.typography.titleMedium)
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Text("${(progress * 100).toInt()}%")
                OutlinedButton(onClick = onCancel) { Text("Cancelar") }
            }
        }
    }
}

@Composable
private fun FullscreenPreview(vm: EditorViewModel, state: EditorState, onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            Column(Modifier.safeDrawingPadding()) {
                TextButton(onClick = onClose) { Text("Fechar tela cheia") }
                PreviewPanel(vm, state, Modifier.weight(1f).fillMaxWidth())
                Button(
                    onClick = vm::togglePlay,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(if (state.playing) "Pausar" else "Reproduzir")
                }
            }
        }
    }
}
