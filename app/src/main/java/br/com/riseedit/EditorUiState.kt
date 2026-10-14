package br.com.riseedit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import br.com.riseedit.core.TextLayer
import br.com.riseedit.tools.EditorTool

@Stable
internal class EditorUiState(toolState: MutableState<EditorTool?>) {
    var tool by toolState
    var full by mutableStateOf(false)

    /** True while a timeline item is focused: the toolbar shows actions for that item. */
    var contextual by mutableStateOf(false)

    /** Keeps the closing panel rendered during its exit animation. */
    var lastTool by mutableStateOf<EditorTool?>(null)
    var exportDialog by mutableStateOf(false)
    var recoveryDialog by mutableStateOf(false)
    var reconnectUri by mutableStateOf<String?>(null)

    /** Sub-toolbar stack (CapCut-style): back pops one level. */
    var menu by mutableStateOf(emptyList<ToolMenu>())

    /** Text being created/edited in the text sheet; null when the sheet is closed. */
    var textDraft by mutableStateOf<TextLayer?>(null)
    var textTab by mutableStateOf(TextTab.STYLES)

    /** Set while the eyedropper waits for a tap on the preview. */
    var eyedropper by mutableStateOf<((Int) -> Unit)?>(null)
    var audioTrack by mutableIntStateOf(0)
    var overlayTrack by mutableIntStateOf(0)
}

@Composable
internal fun rememberEditorUiState(): EditorUiState {
    val tool = rememberSaveable(stateSaver = EditorTool.saver) {
        mutableStateOf<EditorTool?>(null)
    }
    return remember(tool) { EditorUiState(tool) }
}

internal enum class ToolMenu { TEXT, AUDIO, OVERLAY, COLOR, MARKERS }

internal enum class TextTab(val label: String) {
    TEMPLATES("Modelos"),
    FONTS("Fontes"),
    STYLES("Estilos"),
    EFFECTS("Efeitos"),
    ANIMATIONS("Animações")
}
