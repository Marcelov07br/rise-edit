package br.com.riseedit.tools

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.FilterNone
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.graphics.vector.ImageVector

/** Stable keys preserve the selected tool across recreation, independently of labels. */
sealed class EditorTool(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val panelTitle: String = label
) {
    data object Trim : EditorTool("Corte", "Cortar", Icons.Outlined.ContentCut)
    data object Stabilize :
        EditorTool("Estabilizar", "Estabilizar", Icons.Outlined.AutoAwesome, "Estabilização Rise")
    data object Text : EditorTool("Texto", "Texto", Icons.Outlined.TextFields)
    data object Audio : EditorTool("Áudio", "Áudio", Icons.Outlined.MusicNote)
    data object Overlay : EditorTool("Overlay", "Overlay", Icons.Outlined.FilterNone)
    data object Color : EditorTool("Cor", "Cor", Icons.Outlined.Palette)
    data object Movement : EditorTool("Movimento", "Movimento", Icons.Outlined.OpenWith)
    data object Format : EditorTool("Formato", "Formato", Icons.Outlined.AspectRatio)
    data object Speed : EditorTool("Velocidade", "Velocidade", Icons.Outlined.Speed)
    data object Volume : EditorTool("Volume", "Volume", Icons.AutoMirrored.Outlined.VolumeUp)

    val title: String get() = panelTitle

    companion object {
        /** Main toolbar order (after the import action). */
        val main = listOf(Stabilize, Trim, Text, Audio, Overlay, Color, Movement, Format)
        private val all = main + Speed + Volume
        val saver =
            Saver<EditorTool?, String>(
                save = { it?.key ?: "" },
                restore = { saved -> all.firstOrNull { it.key == saved } }
            )
    }
}
