package br.com.riseedit.designsystem

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.riseedit.RiseBlack
import br.com.riseedit.RiseMuted
import br.com.riseedit.RiseOutline
import br.com.riseedit.RiseSurface
import br.com.riseedit.RiseSurfaceHigh
import br.com.riseedit.RiseWhite

/** Centralized tokens for the 4-level panel hierarchy (1 tools / 2 actions / 3 direct graphic controls / 4 choices). Use in every panel. */
object HierarchyTokens {
    // 1. tools: segmented pill
    val TabBarHeight = 40.dp
    val TabBarBg = RiseSurface
    val TabIndicator = RiseSurfaceHigh
    val TabIndicatorBorder = RiseOutline
    // 2. actions: small icon-only
    val ActionSize = 36.dp
    val ActionIcon = 20.dp
    val ActionBg = RiseSurfaceHigh
    val ActionGap = 6.dp
    val DangerGap = 14.dp
    // 3. direct graphic controls
    val GraphicBg = Color(0xFF070708)
    val GraphicBorder = Color(0xFF242426)
    val GraphicRadius = 20.dp
    val GraphicPadding = 12.dp
    val ChipHeight = 26.dp
    // 4. choices: field look
    val FieldBg = RiseSurface
    val FieldBorder = RiseOutline
    val FieldRadius = 12.dp
    val FieldLabel = 11.sp
}

/** Low-bounce spring for the sliding tab indicator. */
fun <T> tabSpring() = spring<T>(dampingRatio = 0.9f, stiffness = 500f)

/** Segmented pill tab bar with a sliding indicator. */
@Composable
fun <T> SegmentedTabs(items: List<T>, selected: T, modifier: Modifier = Modifier, label: (T) -> String, onSelect: (T) -> Unit) {
    val t = HierarchyTokens
    BoxWithConstraints(
        modifier.fillMaxWidth().height(t.TabBarHeight).clip(CircleShape).background(t.TabBarBg)
            .border(1.dp, t.TabIndicatorBorder.copy(alpha = 0.5f), CircleShape).padding(3.dp)
    ) {
        val w = maxWidth / items.size
        val x by animateDpAsState(w * items.indexOf(selected).coerceAtLeast(0), tabSpring(), label = "tab")
        Box(
            Modifier.offset(x = x).width(w).fillMaxHeight().clip(CircleShape).background(t.TabIndicator)
                .border(1.dp, t.TabIndicatorBorder, CircleShape)
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            items.forEach { item ->
                val on = item == selected
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(CircleShape)
                        .clickable(role = Role.Tab) { onSelect(item) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label(item), fontSize = 13.sp, maxLines = 1,
                        color = if (on) RiseWhite else RiseMuted,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

/** Small icon-only action with contentDescription and a long-press tooltip naming it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanelIconButton(
    icon: ImageVector,
    name: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = RiseWhite,
    onClick: () -> Unit
) {
    val t = HierarchyTokens
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(name) } },
        state = rememberTooltipState()
    ) {
        Box(
            modifier.size(t.ActionSize).clip(CircleShape).background(t.ActionBg)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = name }
                .alpha(if (enabled) 1f else 0.35f),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, Modifier.size(t.ActionIcon), tint = tint) }
    }
}

/** Darker, bordered surface that frames a direct graphic control (curve, wheels). */
@Composable
fun GraphicSurface(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val t = HierarchyTokens
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(t.GraphicRadius)).background(t.GraphicBg)
            .border(1.dp, t.GraphicBorder, RoundedCornerShape(t.GraphicRadius)).padding(t.GraphicPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

/** Small channel chips (Master/R/G/B, HSL bands); optional color dot. */
@Composable
fun <T> ChannelChips(
    items: List<T>, selected: T, label: (T) -> String, dot: ((T) -> Color?)? = null, onSelect: (T) -> Unit
) {
    val t = HierarchyTokens
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item ->
            val on = item == selected
            Row(
                Modifier.height(t.ChipHeight).clip(CircleShape)
                    .background(if (on) RiseWhite else RiseSurfaceHigh)
                    .clickable { onSelect(item) }.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                dot?.invoke(item)?.let { Box(Modifier.size(7.dp).clip(CircleShape).background(it)) }
                Text(label(item), fontSize = 12.sp, color = if (on) RiseBlack else RiseWhite, maxLines = 1)
            }
        }
    }
}

/** Thin vertical divider used to set the destructive action apart. */
@Composable
fun ActionDivider(gap: Dp = HierarchyTokens.DangerGap) {
    Box(Modifier.padding(horizontal = (gap - 1.dp) / 2).width(1.dp).height(20.dp).background(RiseOutline))
}

/** One icon-only action of an [ActionBar]. [danger] actions are pushed apart behind a divider. */
class PanelAction(
    val icon: ImageVector, val name: String, val enabled: Boolean = true,
    val danger: Boolean = false, val onClick: () -> Unit
)

/** Level 2: compact row of icon-only actions; destructive ones last, separated by gap + divider. */
@Composable
fun ActionBar(actions: List<PanelAction>, modifier: Modifier = Modifier, extra: (@Composable () -> Unit)? = null) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HierarchyTokens.ActionGap)) {
        extra?.invoke()
        actions.filterNot { it.danger }.forEach { PanelIconButton(it.icon, it.name, enabled = it.enabled, onClick = it.onClick) }
        val danger = actions.filter { it.danger }
        if (danger.isNotEmpty()) {
            ActionDivider()
            danger.forEach { PanelIconButton(it.icon, it.name, enabled = it.enabled, tint = Color(0xFFFF8A80), onClick = it.onClick) }
        }
    }
}
