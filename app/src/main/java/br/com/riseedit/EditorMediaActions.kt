package br.com.riseedit

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import br.com.riseedit.core.ExportSettings

internal data class EditorMediaActions(
    val videos: () -> Unit,
    val audio: () -> Unit,
    val overlay: () -> Unit,
    val export: (ExportSettings) -> Unit,
    /** Audio track of a picked video ("Extrair"). */
    val extract: () -> Unit = {},
    /** Inserts a recorded/local audio file at the playhead. */
    val audioFile: (android.net.Uri) -> Unit = {}
)

@Composable
internal fun rememberEditorMediaActions(
    vm: EditorViewModel,
    state: EditorState,
    ui: EditorUiState
): EditorMediaActions {
    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
        vm::import
    )
    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { vm.importAudio(it, ui.audioTrack) }
    val overlayPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { vm.importOverlay(it, ui.overlayTrack) }
    val extractPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { vm.importAudio(it, ui.audioTrack) }
    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("video/mp4")
    ) { it?.let(vm::export) }
    return EditorMediaActions(
        videos = { videoPicker.launch(arrayOf("video/*")) },
        audio = { audioPicker.launch(arrayOf("audio/*")) },
        overlay = { overlayPicker.launch(arrayOf("video/*")) },
        export = { settings ->
            vm.projectSettings(state.project.aspectRatio, settings)
            ui.exportDialog = false
            val name = state.project.name.replace(Regex("[^\\p{L}\\p{N} _-]"), "")
            exportPicker.launch("$name.mp4")
        },
        extract = { extractPicker.launch(arrayOf("video/*")) },
        audioFile = { vm.importAudio(listOf(it), ui.audioTrack) }
    )
}
