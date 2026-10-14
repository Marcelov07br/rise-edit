package br.com.riseedit.tools

import androidx.compose.runtime.Composable
import br.com.riseedit.EditorState
import br.com.riseedit.EditorViewModel

@Composable
fun ToolPanel(
    tool: EditorTool,
    vm: EditorViewModel,
    state: EditorState,
    addAudio: (android.net.Uri) -> Unit
) {
    when (tool) {
        EditorTool.Trim -> TrimPanel(vm, state)
        EditorTool.Stabilize -> StabilizePanel(vm, state)
        EditorTool.Format -> FormatPanel(vm, state)
        EditorTool.Speed -> SpeedPanel(vm, state)
        EditorTool.Volume -> VolumePanel(vm, state)
        EditorTool.Adjust -> AdjustPanel(vm, state)
        EditorTool.Filters -> FiltersPanel(vm, state)
        EditorTool.ClipAnimation -> ClipAnimationPanel(vm, state)
        EditorTool.AudioLayer -> AudioPanel(vm, state)
        EditorTool.Record -> RecordPanel(addAudio)
        EditorTool.OverlayOpacity -> OverlayOpacityPanel(vm, state)
        EditorTool.OverlayBlend -> OverlayBlendPanel(vm, state)
        EditorTool.OverlayMask -> OverlayMaskPanel(vm, state)
        EditorTool.OverlayTrim -> OverlayTrimPanel(vm, state)
        EditorTool.TextTiming -> TextTimingPanel(vm, state)
        EditorTool.Orientation -> OrientationPanel(vm, state)
        EditorTool.Keyframe -> KeyframePanel(vm, state)
        EditorTool.Grade -> GradePanel(vm, state)
        EditorTool.Meters -> MetersPanel(vm, state)
        EditorTool.Subtitles -> SubtitlesPanel(vm, state)
    }
}
