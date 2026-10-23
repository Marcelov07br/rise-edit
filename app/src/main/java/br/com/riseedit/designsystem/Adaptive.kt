package br.com.riseedit.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.riseedit.LocalWindowSizeClass
import br.com.riseedit.RiseAccent
import br.com.riseedit.RiseBlack
import br.com.riseedit.RiseMuted
import br.com.riseedit.RiseSurface

/** 0.22.1: width class for the non-editor screens (Home, Criar projeto, picker, Configurações). */
enum class ScreenWidth { COMPACT, MEDIUM, EXPANDED }

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun screenWidth(): ScreenWidth {
    val c = LocalWindowSizeClass.current?.widthSizeClass
    if (c != null) {
        return when (c) {
            WindowWidthSizeClass.Expanded -> ScreenWidth.EXPANDED
            WindowWidthSizeClass.Medium -> ScreenWidth.MEDIUM
            else -> ScreenWidth.COMPACT
        }
    }
    val w = LocalConfiguration.current.screenWidthDp
    return when {
        w >= 840 -> ScreenWidth.EXPANDED
        w >= 600 -> ScreenWidth.MEDIUM
        else -> ScreenWidth.COMPACT
    }
}

/** Max readable content width on large screens (nothing edge-to-edge on DeX). */
object AdaptiveTokens {
    val FormMax: Dp = 960.dp
    val SettingsMax: Dp = 880.dp
    val PillMax: Dp = 320.dp
    val SideColumn: Dp = 240.dp
}

/** Pill CTA with bounded width; reused by Home, Criar projeto and the picker. */
@Composable
fun PillButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier.widthIn(min = 160.dp, max = AdaptiveTokens.PillMax).height(52.dp)
            .clip(RoundedCornerShape(50))
            .background(if (enabled) RiseAccent else RiseSurface)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (enabled) RiseBlack else RiseMuted,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            maxLines = 1
        )
    }
}
