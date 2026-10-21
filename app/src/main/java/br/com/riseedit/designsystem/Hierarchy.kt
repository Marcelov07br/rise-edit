package br.com.riseedit.designsystem

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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

/** Segmented pill tab bar with a sliding indicator. Scrolls horizontally when tabs would be < [minTab]. */
@Composable
fun <T> SegmentedTabs(
    items: List<T>,
    selected: T,
    modifier: Modifier = Modifier,
    label: (T) -> String,
    marked: (T) -> Boolean = { false },
    minTab: Dp = 76.dp,
    onSelect: (T) -> Unit
) {
    val t = HierarchyTokens
    BoxWithConstraints(
        modifier.fillMaxWidth().height(t.TabBarHeight).clip(CircleShape).background(t.TabBarBg)
            .border(1.dp, t.TabIndicatorBorder.copy(alpha = 0.5f), CircleShape).padding(3.dp)
    ) {
        val fit = maxWidth / items.size
        val scrolls = fit < minTab
        val w = if (scrolls) minTab else fit
        val index = items.indexOf(selected).coerceAtLeast(0)
        val density = androidx.compose.ui.platform.LocalDensity.current
        val view = maxWidth
        fun centered(i: Int) =
            with(density) { (w * i - (view - w) / 2).toPx().toInt().coerceAtLeast(0) }
        val scroll = rememberScrollState(if (scrolls) centered(index) else 0)
        if (scrolls) {
            androidx.compose.runtime.LaunchedEffect(index) {
                scroll.animateScrollTo(centered(index))
            }
        }
        Box(if (scrolls) Modifier.horizontalScroll(scroll) else Modifier) {
            Box(Modifier.width(w * items.size).fillMaxHeight()) {
                val x by animateDpAsState(w * index, tabSpring(), label = "tab")
                Box(
                    Modifier.offset(
                        x = x
                    ).width(w).fillMaxHeight().clip(CircleShape).background(t.TabIndicator)
                        .border(1.dp, t.TabIndicatorBorder, CircleShape)
                )
                Row(Modifier.fillMaxWidth().fillMaxHeight()) {
                    items.forEach { item ->
                        val on = item == selected
                        Box(
                            Modifier.width(w).fillMaxHeight().clip(CircleShape)
                                .clickable(role = Role.Tab) { onSelect(item) },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    label(item),
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    color = if (on) RiseWhite else RiseMuted,
                                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal
                                )
                                // Indicator dot: this tool has a non-neutral setting.
                                if (marked(item)) {
                                    Box(
                                        Modifier.padding(start = 4.dp).size(5.dp).clip(CircleShape)
                                            .background(if (on) RiseWhite else RiseMuted)
                                    )
                                }
                            }
                        }
                    }
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
            .border(
                1.dp,
                t.GraphicBorder,
                RoundedCornerShape(t.GraphicRadius)
            ).padding(t.GraphicPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

/** Small channel chips (Master/R/G/B, HSL bands); optional color dot. */
@Composable
fun <T> ChannelChips(
    items: List<T>,
    selected: T,
    label: (T) -> String,
    dot: ((T) -> Color?)? = null,
    onSelect: (T) -> Unit
) {
    val t = HierarchyTokens
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
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
                Text(
                    label(item),
                    fontSize = 12.sp,
                    color = if (on) RiseBlack else RiseWhite,
                    maxLines = 1
                )
            }
        }
    }
}

/** Thin vertical divider used to set the destructive action apart. */
@Composable
fun ActionDivider(gap: Dp = HierarchyTokens.DangerGap) {
    Box(
        Modifier.padding(
            horizontal = (gap - 1.dp) / 2
        ).width(1.dp).height(20.dp).background(RiseOutline)
    )
}

/**
 * One icon-only action of an [ActionBar]. [danger] actions are pushed apart behind a divider.
 * [primary] actions stay visible; the others collapse into the "mais" menu when they don't fit.
 */
class PanelAction(
    val icon: ImageVector,
    val name: String,
    val enabled: Boolean = true,
    val danger: Boolean = false,
    val primary: Boolean = false,
    val onClick: () -> Unit
)

val DangerTint = Color(0xFFFF8A80)

/** Pure layout decision of [ActionBar] (unit-testable): which actions are inline vs in the menu. */
object ActionOverflow {
    /** [slots] = how many 36dp buttons fit (extra content already subtracted). */
    fun split(actions: List<PanelAction>, slots: Int): Pair<List<PanelAction>, List<PanelAction>> {
        val danger = actions.filter { it.danger }
        val normal = actions.filterNot { it.danger }
        // Everything fits (divider counts as ~half a slot).
        val needed = normal.size + danger.size + (if (danger.isNotEmpty()) 1 else 0)
        if (needed <= slots) return actions to emptyList()
        // Primaries first (keep order), one slot for "mais"; secondaries + danger go to the menu.
        val room = (slots - 1).coerceAtLeast(0)
        val prim = normal.filter { it.primary }
        val inline = prim.take(room)
        val rest = prim.drop(room) + normal.filterNot { it.primary }
        return inline to (rest + danger)
    }
}

/**
 * Level 2: compact row of icon-only actions; destructive ones last, separated by gap + divider.
 * When the row does not fit, non-[PanelAction.primary] actions collapse into a "mais" menu
 * (more_horiz) with names; destructive ones go last in the menu, in red. [extraSlots] = how many
 * 36dp buttons [extra] takes.
 */
@Composable
fun ActionBar(
    actions: List<PanelAction>,
    modifier: Modifier = Modifier,
    extraSlots: Int = 0,
    extra: (@Composable () -> Unit)? = null
) {
    BoxWithConstraints(modifier) {
        val t = HierarchyTokens
        val slots = ((maxWidth + t.ActionGap) / (t.ActionSize + t.ActionGap)).toInt() - extraSlots
        val (inline, menu) = ActionOverflow.split(actions, slots)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(t.ActionGap)
        ) {
            extra?.invoke()
            inline.filterNot {
                it.danger
            }.forEach {
                PanelIconButton(it.icon, it.name, enabled = it.enabled, onClick = it.onClick)
            }
            val danger = inline.filter { it.danger }
            if (danger.isNotEmpty()) {
                ActionDivider()
                danger.forEach {
                    PanelIconButton(
                        it.icon,
                        it.name,
                        enabled = it.enabled,
                        tint = DangerTint,
                        onClick = it.onClick
                    )
                }
            }
            if (menu.isNotEmpty()) OverflowMenu(menu)
        }
    }
}

@Composable
private fun OverflowMenu(items: List<PanelAction>) {
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Box {
        PanelIconButton(androidx.compose.material.icons.Icons.Rounded.MoreHoriz, "Mais ações") {
            open =
                true
        }
        androidx.compose.material3.DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = RiseSurfaceHigh
        ) {
            ActionMenuItems(items) { open = false }
        }
    }
}

/** Items of the "mais" menu: icon + name; destructive last, after a divider, in red. */
@Composable
fun ActionMenuItems(items: List<PanelAction>, onPicked: () -> Unit = {}) {
    items.forEachIndexed { i, a ->
        if (a.danger && i > 0 && !items[i - 1].danger) {
            androidx.compose.material3.HorizontalDivider(
                Modifier.padding(vertical = 4.dp),
                color = RiseOutline
            )
        }
        val tint = if (a.danger) DangerTint else RiseWhite
        androidx.compose.material3.DropdownMenuItem(
            text = { Text(a.name, color = tint, fontSize = 14.sp) },
            leadingIcon = { Icon(a.icon, null, Modifier.size(20.dp), tint = tint) },
            enabled = a.enabled,
            onClick = {
                onPicked()
                a.onClick()
            }
        )
    }
}
