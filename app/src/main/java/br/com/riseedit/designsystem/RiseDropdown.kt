package br.com.riseedit.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

/** Level 4 (choices): outlined field with small label above the value + chevron. Rounded field + rounded menu (design system), for picking one option among many. */
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
        val t = HierarchyTokens
        Box {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(t.FieldRadius)).background(t.FieldBg)
                    .border(1.dp, t.FieldBorder, RoundedCornerShape(t.FieldRadius))
                    .clickable { open = true }.padding(start = 14.dp, end = 10.dp, top = 7.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(label, color = RiseMuted, fontSize = t.FieldLabel)
                    Text(text(selected), color = RiseWhite, fontSize = 15.sp)
                }
                Icon(Icons.Outlined.KeyboardArrowDown, null, tint = RiseMuted)
            }
            if (open && (expandedInitially || androidx.compose.ui.platform.LocalInspectionMode.current)) {
                // Popups don't render in previews/screenshots: draw the same rounded menu inline.
                Column(
                    Modifier.padding(top = 58.dp).fillMaxWidth(0.8f).clip(RoundedCornerShape(16.dp))
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
