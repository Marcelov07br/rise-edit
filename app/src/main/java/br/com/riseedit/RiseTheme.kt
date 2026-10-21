package br.com.riseedit

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * AMOLED palette (0.18.1 values): pure black, white details, neutral grays. 0.19.1: dark only;
 * the only user choice is the accent of icons/needle/primary buttons. No dynamic color.
 */
enum class Accent(val label: String, val color: Color) {
    WHITE("Neutro", Color(0xFFFFFFFF)),
    AMBER("Âmbar", Color(0xFFFFC145)),
    CORAL("Coral", Color(0xFFFF6B5A)),
    MINT("Menta", Color(0xFF4FD6A5)),
    SKY("Céu", Color(0xFF5AB4FF)),
    LILAC("Lilás", Color(0xFFB79CFF))
}

object RisePalette {
    var accent by mutableStateOf(Accent.WHITE)

    /** Settings "háptico": the designsystem feedback goes through [LocalHapticFeedback]. */
    var haptics by mutableStateOf(true)

    /** Settings "reduzir animações": springs become fast and critically damped. */
    var reduceMotion by mutableStateOf(false)
}

val RiseBlack = Color(0xFF000000)
val RiseWhite = Color(0xFFFFFFFF)
val RiseBackground = RiseBlack
val RiseSurface = Color(0xFF0E0E0F)
val RiseSurfaceHigh = Color(0xFF1A1A1C)
val RiseOutline = Color(0xFF2C2C2E)
val RiseMuted = Color(0xFF8E8E93)
val RiseAccent get() = RisePalette.accent.color

val LaneVideo = Color(0xFFEDEDED)
val LaneOverlay = Color(0xFFB5B5B5)
val LaneText = Color(0xFF9A9A9A)
val LaneAudio = Color(0xFF7C7C7C)

/** Camada de ajuste: the only tinted lane, so it reads as "color" at a glance. */
val LaneAdjust = Color(0xFFC9A6FF)

/** Springs with a gentle, clamped bounce for panels and presses ("reduzir animações": fast, no bounce). */
fun <T> riseSpring(): SpringSpec<T> = if (RisePalette.reduceMotion) {
    spring(dampingRatio = 1f, stiffness = Spring.StiffnessHigh)
} else {
    spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
}

val risePanelSpring: SpringSpec<IntOffset> get() =
    spring(
        dampingRatio = 0.86f,
        stiffness = Spring.StiffnessMediumLow,
        visibilityThreshold = IntOffset(1, 1)
    )

private fun colors() = darkColorScheme(
    primary = RiseAccent,
    onPrimary = RiseBlack,
    primaryContainer = RiseSurfaceHigh,
    onPrimaryContainer = RiseWhite,
    secondary = Color(0xFFD1D1D6),
    onSecondary = RiseBlack,
    secondaryContainer = RiseSurfaceHigh,
    onSecondaryContainer = RiseWhite,
    tertiary = Color(0xFFD1D1D6),
    onTertiary = RiseBlack,
    background = RiseBackground,
    onBackground = RiseWhite,
    surface = RiseBackground,
    onSurface = RiseWhite,
    surfaceVariant = RiseSurfaceHigh,
    onSurfaceVariant = RiseMuted,
    surfaceContainerLowest = RiseBlack,
    surfaceContainerLow = RiseSurface,
    surfaceContainer = RiseSurface,
    surfaceContainerHigh = RiseSurfaceHigh,
    surfaceContainerHighest = Color(0xFF242426),
    outline = RiseOutline,
    outlineVariant = Color(0xFF1F1F21),
    inverseSurface = RiseWhite,
    inverseOnSurface = RiseBlack,
    surfaceTint = RiseBlack
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun RiseTheme(content: @Composable () -> Unit) {
    val base = LocalHapticFeedback.current
    val haptic = if (RisePalette.haptics) base else NoHaptics
    // Default content color is white on black: plain Box/Card text never falls back to black.
    CompositionLocalProvider(
        LocalHapticFeedback provides haptic,
        LocalContentColor provides RiseWhite
    ) {
        MaterialTheme(colorScheme = colors(), shapes = shapes, content = content)
    }
}

private object NoHaptics : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
}
