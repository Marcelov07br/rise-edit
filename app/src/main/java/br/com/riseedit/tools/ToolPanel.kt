package br.com.riseedit.tools

import androidx.compose.runtime.Composable
import br.com.riseedit.EditorState
import br.com.riseedit.EditorViewModel

@Composable
fun ToolPanel(
    tool: EditorTool,
    vm: EditorViewModel,
    state: EditorState,
    chooseAudioTrack: (Int) -> Unit,
    chooseOverlayTrack: (Int) -> Unit,
    addAudio: () -> Unit,
    addOverlay: () -> Unit
) {
    when (tool) {
        EditorTool.Trim -> TrimPanel(vm, state)
        EditorTool.Stabilize -> StabilizePanel(vm, state)
        EditorTool.Text -> TextPanel(vm, state)
        EditorTool.Audio -> AudioPanel(vm, state, chooseAudioTrack, addAudio)
        EditorTool.Overlay -> OverlayPanel(vm, state, chooseOverlayTrack, addOverlay)
        EditorTool.Color -> ColorPanel(vm, state)
        EditorTool.Movement -> MovementPanel(vm, state)
        EditorTool.Format -> FormatPanel(vm, state)
        EditorTool.Speed -> SpeedPanel(vm, state)
        EditorTool.Volume -> VolumePanel(vm, state)
    }
}
