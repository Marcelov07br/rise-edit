package br.com.riseedit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import br.com.riseedit.core.ExportSettings

@Composable
fun ExportDialog(initial: ExportSettings, onClose: () -> Unit, onExport: (ExportSettings) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Exportar vídeo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Resolução"); ChoiceRow(listOf(480, 720, 1080), value.height, { value = value.copy(height = it) }) { "${it}p" }
                Text("Limite de quadros por segundo"); ChoiceRow(listOf(24, 30, 60), value.fps, { value = value.copy(fps = it) }) { "$it fps" }
                Text("Qualidade"); ChoiceRow(listOf(4, 8, 16), value.bitrate / 1_000_000, { value = value.copy(bitrate = it * 1_000_000) }) { when (it) { 4 -> "Leve"; 8 -> "Boa"; else -> "Alta" } }
                Text("MP4 · H.264 / AAC. O vídeo é salvo em um arquivo novo. A taxa de quadros não aumenta acima da fonte.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(onClick = { onExport(value) }) { Text("Escolher destino") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancelar") } }
    )
}
