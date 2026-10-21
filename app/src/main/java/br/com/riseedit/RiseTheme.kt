package br.com.riseedit

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * 0.19: two fixed palettes (AMOLED dark, light) + a small fixed accent palette; no dynamic color.
 * Tokens keep their historical names: RiseBlack = background, RiseWhite = foreground. They read
 * [RisePalette] snapshot state, so switching theme recomposes every screen.
 */
enum class ThemeMode(val label: String) { DARK("Escuro"), LIGHT("Claro"), SYSTEM("Sistema") }

enum class Accent(val label: String, val color: Color) {
    WHITE("Neutro", Color(0xFFFFFFFF)),
    AMBER("Âmbar", Color(0xFFFFC145)),
    CORAL("Coral", Color(0xFFFF6B5A)),
    MINT("Menta", Color(0xFF4FD6A5)),
    SKY("Céu", Color(0xFF5AB4FF)),
    LILAC("Lilás", Color(0xFFB79CFF))
}

object RisePalette {
    var dark by mutableStateOf(true)
    var accent by mutableStateOf(Accent.WHITE)

    /** Settings "háptico": the designsystem feedback goes through [LocalHapticFeedback]. */
    var haptics by mutableStateOf(true)

    /** Settings "reduzir animações": springs become fast and critically damped. */
    var reduceMotion by mutableStateOf(false)

    val black get() = if (dark) Color(0xFF000000) else Color(0xFFF3F3F5)
    val white get() = if (dark) Color(0xFFFFFFFF) else Color(0xFF121214)
    val surface get() = if (dark) Color(0xFF0E0E0F) else Color(0xFFFFFFFF)
    val surfaceHigh get() = if (dark) Color(0xFF1A1A1C) else Color(0xFFE6E6EA)
    val outline get() = if (dark) Color(0xFF2C2C2E) else Color(0xFFCBCBD1)
    val muted get() = if (dark) Color(0xFF8E8E93) else Color(0xFF66666C)
    val graphicBg get() = if (dark) Color(0xFF070708) else Color(0xFFDCDCE1)
    val graphicBorder get() = if (dark) Color(0xFF242426) else Color(0xFFC2C2C8)

    /** Accent on the current background (neutral = foreground; light theme darkens hues). */
    val accentColor get() = when {
        accent == Accent.WHITE -> white
        dark -> accent.color
        else -> lerp(accent.color, Color.Black, 0.25f)
    }
}

val RiseBlack get() = RisePalette.black
val RiseWhite get() = RisePalette.white
val RiseBackground get() = RisePalette.black
val RiseSurface get() = RisePalette.surface
val RiseSurfaceHigh get() = RisePalette.surfaceHigh
val RiseOutline get() = RisePalette.outline
val RiseMuted get() = RisePalette.muted
val RiseAccent get() = RisePalette.accentColor

val LaneVideo get() = if (RisePalette.dark) Color(0xFFEDEDED) else Color(0xFF2E2E33)
val LaneOverlay get() = if (RisePalette.dark) Color(0xFFB5B5B5) else Color(0xFF4A4A50)
val LaneText get() = if (RisePalette.dark) Color(0xFF9A9A9A) else Color(0xFF5E5E64)
val LaneAudio get() = if (RisePalette.dark) Color(0xFF7C7C7C) else Color(0xFF75757B)

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

private fun colors() = (if (RisePalette.dark) darkColorScheme() else lightColorScheme()).copy(
    primary = RiseAccent,
    onPrimary = if (RisePalette.accent == Accent.WHITE ||
        !RisePalette.dark
    ) {
        RiseBlack
    } else {
        Color.Black
    },
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
    val settings by br.com.riseedit.settings.SettingsStore.state.collectAsState()
    val system = isSystemInDarkTheme()
    // "Sistema" follows the OS; the explicit modes are applied by SettingsStore.
    if (settings.theme == ThemeMode.SYSTEM && RisePalette.dark != system) {
        SideEffect { RisePalette.dark = system }
    }
    val base = LocalHapticFeedback.current
    val haptic = if (RisePalette.haptics) base else NoHaptics
    CompositionLocalProvider(LocalHapticFeedback provides haptic) {
        MaterialTheme(colorScheme = colors(), shapes = shapes, content = content)
    }
}

private object NoHaptics : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
}
