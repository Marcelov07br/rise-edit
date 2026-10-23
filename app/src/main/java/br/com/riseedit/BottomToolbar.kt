package br.com.riseedit

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One toolbar entry: icon over a short label. */
internal data class ToolbarItem(
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val active: Boolean = false,
    val onClick: () -> Unit
)

/**
 * CapCut-style horizontally scrolling toolbar. [leading] stays pinned (e.g. the back arrow of a
 * contextual toolbar) while the remaining items scroll.
 */
@Composable
internal fun BottomToolbar(
    items: List<ToolbarItem>,
    modifier: Modifier = Modifier,
    leading: ToolbarItem? = null
) {
    Row(
        modifier.fillMaxWidth().background(RiseBlack).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            ToolButton(leading, Modifier.padding(start = 6.dp))
            Box(Modifier.width(1.dp).height(36.dp).background(RiseOutline))
        }
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { ToolButton(it) }
            Spacer(Modifier.width(8.dp))
        }
    }
}

/** 0.22 (DeX/tablet): the same items as a vertical side tool column. */
@Composable
internal fun SideToolColumn(
    items: List<ToolbarItem>,
    modifier: Modifier = Modifier,
    leading: ToolbarItem? = null
) {
    androidx.compose.foundation.layout.Column(
        modifier.width(84.dp).fillMaxHeight().background(RiseBlack)
            .verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (leading != null) {
            ToolButton(leading)
            Box(Modifier.width(36.dp).height(1.dp).background(RiseOutline))
        }
        items.forEach { ToolButton(it) }
    }
}

@Composable
private fun ToolButton(item: ToolbarItem, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, riseSpring(), label = "press")
    val tint = if (item.active) RiseBlack else RiseWhite
    Column(
        modifier
            .widthIn(min = 62.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .alpha(if (item.enabled) 1f else 0.35f)
            .clip(RoundedCornerShape(14.dp))
            .background(if (hovered && item.enabled) RiseSurface else androidx.compose.ui.graphics.Color.Transparent)
            .hoverable(interaction, enabled = item.enabled)
            .clickable(interaction, null, enabled = item.enabled, onClick = item.onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(12.dp))
                .background(if (item.active) RiseWhite else RiseSurfaceHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(item.icon, null, Modifier.size(20.dp), tint = tint)
        }
        Text(
            item.label,
            color = if (item.enabled) RiseWhite else RiseMuted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
