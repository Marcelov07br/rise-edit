package br.com.riseedit

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun RecoveryDialog(vm: EditorViewModel, state: EditorState, ui: EditorUiState) {
    val reconnect =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val old = ui.reconnectUri
            ui.reconnectUri = null
            if (uri != null && old != null) vm.reconnectMedia(old, uri)
        }
    AlertDialog(
        onDismissRequest = { ui.recoveryDialog = false },
        title = { Text("Recuperar projeto") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.missingMedia.isEmpty()) {
                    Text("Nenhum arquivo ausente neste projeto.")
                } else {
                    Text("${state.missingMedia.size} arquivo(s) ausente(s). Reconecte cada um:")
                    state.missingMedia.forEach { missing ->
                        OutlinedButton(
                            onClick = {
                                ui.reconnectUri = missing.uri
                                reconnect.launch(arrayOf("*/*"))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Reconectar: ${missing.name}") }
                    }
                }
                Text("Versões salvas automaticamente:")
                val versions = vm.projectVersions()
                if (versions.isEmpty()) {
                    Text("Ainda não há versões anteriores.")
                } else {
                    versions.take(5).forEach { stamp ->
                        val label = SimpleDateFormat(
                            "dd/MM HH:mm:ss",
                            Locale("pt", "BR")
                        ).format(Date(stamp))
                        TextButton(onClick = {
                            ui.recoveryDialog = false
                            vm.restoreProjectVersion(stamp)
                        }) { Text("Restaurar $label") }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { ui.recoveryDialog = false }) { Text("Fechar") }
        }
    )
}
