package br.com.riseedit.tools

import androidx.compose.runtime.Composable
import br.com.riseedit.EditorState
import br.com.riseedit.EditorViewModel

@Composable
fun ToolPanel(
    tool: EditorTool,
    vm: EditorViewModel,
    state: EditorState,
    addAudio: (android.net.Uri) -> Unit,
    /** 0.25.0: open the text studio on a caption (look of the whole track). */
    editLook: (br.com.riseedit.core.TextLayer) -> Unit = {}
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
        EditorTool.Subtitles -> SubtitlesPanel(vm, state, editLook)
        EditorTool.Masks -> MaskPanel(vm, state)
        EditorTool.Markers -> br.com.riseedit.markers.MarkersPanel(vm, state)
    }
}
