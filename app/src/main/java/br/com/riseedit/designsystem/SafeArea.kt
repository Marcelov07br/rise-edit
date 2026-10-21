package br.com.riseedit.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import br.com.riseedit.RiseBlack

/**
 * 0.19 insets: one source for the safe area (status bar + gesture/nav bar + cutout + IME).
 * Tests override it with fixed insets to preview a phone with bars (Paparazzi has none).
 */
val LocalRiseInsets = staticCompositionLocalOf<WindowInsets?> { null }

val riseSafeInsets: WindowInsets
    @Composable
    get() = LocalRiseInsets.current ?: WindowInsets.safeDrawing

/** Pads content/controls out of the system bars; consumed, so nested uses add nothing. */
fun Modifier.riseSafeArea(): Modifier = composed { windowInsetsPadding(riseSafeInsets) }

/** Edge-to-edge background with the content kept inside the safe area. */
@Composable
fun RiseScreenFrame(
    modifier: Modifier = Modifier,
    background: Color = RiseBlack,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier.fillMaxSize().background(background)) {
        Box(Modifier.fillMaxSize().riseSafeArea(), content = content)
    }
}
