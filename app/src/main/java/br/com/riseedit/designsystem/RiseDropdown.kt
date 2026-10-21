package br.com.riseedit.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.riseedit.RiseMuted
import br.com.riseedit.RiseSurfaceHigh
import br.com.riseedit.RiseWhite

/** Rounded field + rounded menu (design system), for picking one option among many. */
@Composable
fun <T> RiseDropdown(
    label: String,
    options: List<T>,
    selected: T,
    modifier: Modifier = Modifier,
    /** Screenshots only: starts open and draws the menu inline (popups do not render there). */
    expandedInitially: Boolean = false,
    text: (T) -> String,
    onSelect: (T) -> Unit
) {
    var open by remember { mutableStateOf(expandedInitially) }
    Column(modifier) {
        Text(label, color = RiseMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
        Box {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(RiseSurfaceHigh)
                    .clickable { open = true }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text(selected), color = RiseWhite, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.KeyboardArrowDown, null, tint = RiseMuted)
            }
            if (open && (expandedInitially || androidx.compose.ui.platform.LocalInspectionMode.current)) {
                // Popups don't render in previews/screenshots: draw the same rounded menu inline.
                Column(
                    Modifier.padding(top = 52.dp).fillMaxWidth(0.8f).clip(RoundedCornerShape(16.dp))
                        .background(RiseSurfaceHigh).padding(vertical = 8.dp)
                ) {
                    options.forEach { o ->
                        DropdownMenuItem(
                            text = { Text(text(o), color = RiseWhite) },
                            trailingIcon = { if (o == selected) Icon(Icons.Outlined.Check, null, tint = RiseWhite) },
                            onClick = {}
                        )
                    }
                }
                return@Box
            }
            DropdownMenu(
                open, { open = false },
                shape = RoundedCornerShape(16.dp),
                containerColor = RiseSurfaceHigh
            ) {
                options.forEach { o ->
                    DropdownMenuItem(
                        text = { Text(text(o), color = RiseWhite) },
                        trailingIcon = { if (o == selected) Icon(Icons.Outlined.Check, null, tint = RiseWhite) },
                        onClick = { open = false; onSelect(o) }
                    )
                }
            }
        }
    }
}
