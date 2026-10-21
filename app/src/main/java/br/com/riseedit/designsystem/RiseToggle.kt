package br.com.riseedit.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import br.com.riseedit.RiseAccent
import br.com.riseedit.RiseBlack
import br.com.riseedit.RiseMuted
import br.com.riseedit.RiseOutline
import br.com.riseedit.RiseSurfaceHigh
import br.com.riseedit.riseSpring

/** Level 4 on/off: flat pill (no Material You switch), knob slides on a low-bounce spring. */
@Composable
fun RiseToggle(checked: Boolean, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val x by animateDpAsState(if (checked) 18.dp else 0.dp, riseSpring(), label = "knob")
    val track by animateColorAsState(if (checked) RiseAccent else RiseSurfaceHigh, label = "track")
    Box(
        modifier.size(44.dp, 26.dp).clip(CircleShape).background(track)
            .border(1.dp, RiseOutline, CircleShape)
            .toggleable(checked, role = Role.Switch) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onChange(it)
            }
            .padding(3.dp)
    ) {
        Box(
            Modifier.offset(x = x).size(20.dp).clip(CircleShape)
                .background(if (checked) RiseBlack else RiseMuted)
        )
    }
}
