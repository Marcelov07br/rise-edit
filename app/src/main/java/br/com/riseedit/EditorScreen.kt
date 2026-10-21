package br.com.riseedit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import br.com.riseedit.editor.ExportBus
import br.com.riseedit.editor.ExportService

/** Fixed regions; timeline and tool sheet scroll internally, never the entire editor. */
@Composable
fun EditorScreen(
    vm: EditorViewModel,
    state: EditorState,
    onBack: () -> Unit,
    onSettings: () -> Unit
) {
    val context = LocalContext.current
    val ui = rememberEditorUiState()
    val media = rememberEditorMediaActions(vm, state, ui)
    val enabled = state.busy == null
    val hasClips = state.project.clips.isNotEmpty()
    EditorScreenEffects(vm, state, ui)
    BoxWithConstraints(Modifier.fillMaxSize().background(RiseBlack).safeDrawingPadding()) {
        val workspaceHeight = maxHeight
        Column(Modifier.fillMaxSize()) {
            EditorTopBar(
                state.project.name,
                enabled,
                enabled && hasClips,
                state.canUndo,
                state.canRedo,
                onBack,
                onUndo = vm::undo,
                onRedo = vm::redo,
                onSettings = onSettings,
                onExport = { ui.exportDialog = true }
            )
            if (state.missingMedia.isNotEmpty()) {
                TextButton(onClick = {
                    ui.recoveryDialog = true
                }, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    Text("⚠ ${state.missingMedia.size} mídia(s) ausente(s) — tocar para recuperar")
                }
            }
            val export by ExportBus.state.collectAsState()
            if (export.active) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Text("Exportando em segundo plano… ${(export.progress * 100).toInt()}%")
                    LinearProgressIndicator(progress = {
                        export.progress
                    }, modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = {
                        ExportService.cancel(context)
                    }) { Text("Cancelar exportação") }
                }
            }
            if (hasClips) {
                EditorWorkspace(vm, state, ui, media, workspaceHeight)
            } else {
                EditorEmptyPanel(enabled, media.videos)
            }
        }
        EditorMessage(state.message, vm::message, if (state.messageUndo && state.canUndo) vm::undo else null)
        state.attributeDialog?.let { d ->
            br.com.riseedit.tools.AttributeSelectDialog(
                d,
                onDismiss = vm::closeAttributeDialog,
                onConfirm = { sel, all -> if (d.paste) vm.confirmPaste(sel, all) else vm.confirmCopy(sel) }
            )
        }
    }
    EditorDialogs(vm, state, ui, media)
}
