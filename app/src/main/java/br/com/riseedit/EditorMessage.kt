package br.com.riseedit

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun BoxScope.EditorMessage(message: String?, dismiss: (String?) -> Unit, undo: (() -> Unit)? = null) {
    message?.let { text ->
        Surface(
            Modifier.align(Alignment.TopCenter).padding(top = 60.dp, start = 12.dp, end = 12.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 6.dp
        ) {
            Row(
                Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text,
                    Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.bodySmall
                )
                if (undo != null) TextButton(onClick = { undo(); dismiss(null) }) { Text("Desfazer") }
                TextButton(onClick = { dismiss(null) }) { Text("OK") }
            }
        }
    }
}
