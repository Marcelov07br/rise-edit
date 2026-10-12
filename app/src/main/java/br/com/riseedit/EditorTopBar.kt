package br.com.riseedit

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun EditorTopBar(
    name: String,
    enabled: Boolean,
    canExport: Boolean,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onExport: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onBack, enabled = enabled) { Text("‹ Projetos") }
        Text(
            name,
            Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Bold
        )
        TextButton(
            onClick = onSettings,
            modifier = Modifier.semantics {
                contentDescription =
                    "Configurações e atualizações"
            }
        ) { Text("⚙") }
        Button(onClick = onExport, enabled = canExport) { Text("Exportar") }
    }
}
