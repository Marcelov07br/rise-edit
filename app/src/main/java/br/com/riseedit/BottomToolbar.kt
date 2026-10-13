package br.com.riseedit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import br.com.riseedit.tools.EditorTool

/** Large touch targets that scroll horizontally. Direct actions work immediately; the others open a [ToolSheet]. */
@Composable
fun BottomToolbar(
    enabled: Boolean,
    layerSelected: Boolean,
    onImport: () -> Unit,
    onTool: (EditorTool) -> Unit,
    onSplit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier.fillMaxWidth(), color = RiseSurface) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(
                rememberScrollState()
            ).padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolButton("✦", "Estabilização Rise", enabled, accent = true) {
                onTool(EditorTool.Stabilize)
            }
            ToolButton("+", "Vídeo", enabled, onClick = onImport)
            ToolButton("⫽", "Dividir", enabled && !layerSelected, onClick = onSplit)
            EditorTool.panels.forEach { tool ->
                ToolButton(tool.glyph, tool.label, enabled) { onTool(tool) }
            }
            ToolButton("✕", "Excluir", enabled, onClick = onDelete)
        }
    }
}

@Composable
private fun ToolButton(
    glyph: String,
    label: String,
    enabled: Boolean,
    accent: Boolean = false,
    onClick: () -> Unit
) {
    val tint = if (accent) RiseGreen else MaterialTheme.colorScheme.onSurface
    Column(
        Modifier
            .defaultMinSize(minWidth = 64.dp)
            .heightIn(min = 56.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(12.dp))
            .then(if (accent) Modifier.background(RiseGreen.copy(alpha = 0.14f)) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(glyph, color = tint, style = MaterialTheme.typography.titleMedium)
        Text(label, color = tint, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}
