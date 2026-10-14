package br.com.riseedit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Compact panel docked just above the toolbar. The preview stays visible; back and the check
 * both return to the toolbar.
 */
@Composable
fun ToolSheet(
    title: String,
    maxContentHeight: Dp,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(RiseSurface)
    ) {
        Box(
            Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp)
                .width(32.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(RiseOutline)
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Voltar", tint = RiseWhite)
            }
            Text(
                title,
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = RiseWhite
            )
            IconButton(onClick = onClose) {
                Box(
                    Modifier.size(30.dp).clip(RoundedCornerShape(15.dp)).background(RiseWhite),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Check, "Concluir", Modifier.size(18.dp), tint = RiseBlack)
                }
            }
        }
        Column(
            Modifier.heightIn(max = maxContentHeight)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}
