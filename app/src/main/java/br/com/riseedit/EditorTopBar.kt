package br.com.riseedit

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Back, project name, always-visible undo/redo, settings and export. */
@Composable
internal fun EditorTopBar(
    name: String,
    enabled: Boolean,
    canExport: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSettings: () -> Unit,
    onExport: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TopIcon(Icons.AutoMirrored.Outlined.ArrowBack, "Projetos", enabled, onBack)
        Text(
            name,
            Modifier.weight(1f).padding(horizontal = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        TopIcon(Icons.AutoMirrored.Outlined.Undo, "Desfazer", enabled && canUndo, onUndo)
        TopIcon(Icons.AutoMirrored.Outlined.Redo, "Refazer", enabled && canRedo, onRedo)
        TopIcon(Icons.Outlined.Settings, "Configurações e atualizações", true, onSettings)
        Button(
            onClick = onExport,
            enabled = canExport,
            shape = RoundedCornerShape(50),
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.height(36.dp).padding(start = 4.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = RiseWhite,
                contentColor = RiseBlack
            )
        ) { Text("Exportar", fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun TopIcon(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(icon, label, tint = if (enabled) RiseWhite else RiseOutline)
    }
}
