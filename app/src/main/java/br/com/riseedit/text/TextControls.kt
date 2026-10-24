package br.com.riseedit.text

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.riseedit.RiseBlack
import br.com.riseedit.RiseMuted
import br.com.riseedit.RiseOutline
import br.com.riseedit.RiseSurfaceHigh
import br.com.riseedit.RiseWhite
import br.com.riseedit.core.TextAnimation
import br.com.riseedit.core.TextLayer
import br.com.riseedit.core.TextPresets

/** Horizontally scrolling row of square tiles. */
@Composable
internal fun TileRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) { content() }
}

/** Pill chips used for sub-tabs. */
@Composable
internal fun <T> PillRow(items: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items.forEach { item ->
            val active = item == selected
            Text(
                label(item),
                color = if (active) RiseBlack else RiseWhite,
                fontSize = 12.sp,
                modifier = Modifier.clip(RoundedCornerShape(50))
                    .background(if (active) RiseWhite else RiseSurfaceHigh)
                    .clickable { onSelect(item) }.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

/** Eyedropper, color wheel, then the palette. */
@Composable
internal fun SwatchRow(
    selected: Int,
    onEyedropper: () -> Unit,
    onWheel: () -> Unit,
    eyedropperActive: Boolean,
    onPick: (Int) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoundIcon(Icons.Outlined.Colorize, "Conta-gotas", eyedropperActive, onEyedropper)
        RoundIcon(Icons.Outlined.Palette, "Roda de cores", false, onWheel)
        Box(Modifier.width(1.dp).size(1.dp, 24.dp).background(RiseOutline))
        TextPresets.palette.forEach { color ->
            val active = color == selected
            Box(
                Modifier.size(30.dp).clip(CircleShape)
                    .border(
                        if (active) 2.dp else 1.dp,
                        if (active) RiseWhite else RiseOutline,
                        CircleShape
                    )
                    .padding(if (active) 3.dp else 0.dp).clip(CircleShape).background(Color(color))
                    .clickable { onPick(color) }
                    .semantics { contentDescription = "Cor ${Integer.toHexString(color)}" }
            )
        }
    }
}

@Composable
internal fun RoundIcon(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(
            32.dp
        ).clip(CircleShape).background(if (active) RiseWhite else RiseSurfaceHigh)
            .clickable(onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, Modifier.size(18.dp), tint = if (active) RiseBlack else RiseWhite) }
}

/** Slider that applies while dragging (the draft is not an undo step). */
@Composable
internal fun LiveSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String = { "${(it * 100).toInt()}" },
    onChange: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            color = RiseWhite,
            fontSize = 12.sp,
            modifier = Modifier.width(96.dp),
            maxLines = 1
        )
        Slider(
            value.coerceIn(range),
            onChange,
            Modifier.weight(1f),
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = RiseWhite,
                activeTrackColor = RiseWhite,
                inactiveTrackColor = RiseOutline
            )
        )
        Text(
            format(value),
            color = RiseMuted,
            fontSize = 12.sp,
            modifier = Modifier.width(40.dp).padding(start = 6.dp)
        )
    }
}
