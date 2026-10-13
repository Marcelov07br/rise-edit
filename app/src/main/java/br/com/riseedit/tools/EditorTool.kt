package br.com.riseedit.tools

import androidx.compose.runtime.saveable.Saver

/** Stable keys preserve the selected tool across recreation, independently of labels. */
sealed class EditorTool(val key: String, val label: String, val glyph: String) {
    data object Trim : EditorTool("Corte", "Cortar", "✂")
    data object Stabilize : EditorTool("Estabilizar", "Estabilização Rise", "✦")
    data object Text : EditorTool("Texto", "Texto", "T")
    data object Audio : EditorTool("Áudio", "Áudio", "♪")
    data object Overlay : EditorTool("Overlay", "Overlay", "▣")
    data object Color : EditorTool("Cor", "Cor", "◐")
    data object Movement : EditorTool("Movimento", "Movimento", "◆")
    data object Format : EditorTool("Formato", "Formato", "▭")

    val title: String get() = if (this == Stabilize) "✦ $label" else label

    companion object {
        val panels = listOf(Trim, Text, Audio, Overlay, Color, Movement, Format)
        private val all = panels + Stabilize
        val saver = Saver<EditorTool?, String>(
            save = { it?.key ?: "" },
            restore = { saved -> all.firstOrNull { it.key == saved } }
        )
    }
}
