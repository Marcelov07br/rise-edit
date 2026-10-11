package br.com.riseedit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Label shown in the sheet header for a tool key understood by [ToolPanel]. */
fun toolTitle(tool: String): String = when (tool) {
    "Corte" -> "Cortar"
    "Estabilizar" -> "✦ Estabilização Rise"
    else -> tool
}

/** Bottom panel that replaces the toolbar while a tool is open, keeping the timeline visible above it. */
@Composable
fun ToolSheet(title: String, maxContentHeight: Dp, onClose: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), color = RiseSurface, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), tonalElevation = 2.dp) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (title.startsWith("✦")) RiseGreen else MaterialTheme.colorScheme.onSurface
                )
                TextButton(onClick = onClose) { Text("Concluir") }
            }
            Column(
                Modifier.heightIn(max = maxContentHeight).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content
            )
        }
    }
}
