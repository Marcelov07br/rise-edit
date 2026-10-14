package br.com.riseedit.tools

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Gradient
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Opacity
import androidx.compose.material.icons.outlined.PhotoFilter
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Sub-tools that need parameters and therefore open a compact docked panel. Transforms
 * (position, size, rotation) are never here: they live in the tactile preview.
 */
sealed class EditorTool(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val panelTitle: String = label
) {
    data object Trim : EditorTool("Corte", "Cortar", Icons.Outlined.ContentCut)
    data object Stabilize :
        EditorTool("Estabilizar", "Estabilizar", Icons.Outlined.AutoAwesome, "Estabilização Rise")
    data object Format : EditorTool("Formato", "Formato", Icons.Outlined.AspectRatio)
    data object Speed : EditorTool("Velocidade", "Velocidade", Icons.Outlined.Speed)
    data object Volume : EditorTool("Volume", "Volume", Icons.AutoMirrored.Outlined.VolumeUp)
    data object Adjust : EditorTool("Ajustes", "Ajustes", Icons.Outlined.Tune)
    data object Filters : EditorTool("Filtros", "Filtros", Icons.Outlined.PhotoFilter)
    data object ClipAnimation : EditorTool("Animação", "Animação", Icons.Outlined.Animation)
    data object AudioLayer : EditorTool("Áudio", "Áudio", Icons.Outlined.MusicNote, "Ajustar áudio")
    data object Record : EditorTool("Gravar", "Gravar", Icons.Outlined.Mic, "Gravar narração")
    data object OverlayOpacity : EditorTool("Opacidade", "Opacidade", Icons.Outlined.Opacity)
    data object OverlayBlend : EditorTool("Mesclagem", "Mesclagem", Icons.Outlined.Contrast)
    data object OverlayMask : EditorTool("Máscara", "Máscara", Icons.Outlined.Gradient)
    data object OverlayTrim :
        EditorTool("CorteOverlay", "Cortar", Icons.Outlined.ContentCut, "Cortar overlay")
    data object TextTiming : EditorTool("Duração", "Duração", Icons.Outlined.Timer)

    val title: String get() = panelTitle

    companion object {
        private val all = listOf(
            Trim, Stabilize, Format, Speed, Volume, Adjust, Filters, ClipAnimation, AudioLayer,
            Record, OverlayOpacity, OverlayBlend, OverlayMask, OverlayTrim, TextTiming
        )
        val saver =
            Saver<EditorTool?, String>(
                save = { it?.key ?: "" },
                restore = { saved -> all.firstOrNull { it.key == saved } }
            )
    }
}
