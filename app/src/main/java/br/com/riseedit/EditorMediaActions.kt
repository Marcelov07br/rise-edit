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
    val context = androidx.compose.ui.platform.LocalContext.current
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
    // 0.21: ProRes goes to a QuickTime .mov.
    val movPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("video/quicktime")
    ) { it?.let(vm::export) }
    return EditorMediaActions(
        // 0.19: in-app MediaStore picker (Vídeos | Imagens) replaces the system document picker.
        videos = { ui.mediaPicker = true },
        audio = { audioPicker.launch(arrayOf("audio/*")) },
        overlay = { overlayPicker.launch(arrayOf("video/*")) },
        export = { settings ->
            vm.projectSettings(state.project.aspectRatio, settings)
            ui.exportDialog = false
            val name = state.project.name.replace(Regex("[^\\p{L}\\p{N} _-]"), "")
            // 0.19: Configurações › Exportação › pasta destino (SAF tree) skips "Salvar como".
            val folder = br.com.riseedit.settings.SettingsStore.current.exportFolder
            val ext = br.com.riseedit.core.ExportOptions.extension(settings)
            val mime = br.com.riseedit.core.ExportOptions.mime(settings)
            val direct = folder?.let { createInTree(context, it, "$name.$ext", mime) }
            when {
                direct != null -> vm.export(direct)
                ext == "mov" -> movPicker.launch("$name.mov")
                else -> exportPicker.launch("$name.mp4")
            }
        },
        extract = { extractPicker.launch(arrayOf("video/*")) },
        audioFile = { vm.importAudio(listOf(it), ui.audioTrack) }
    )
}

/** New MP4 inside the chosen folder; null when the grant was lost (falls back to the picker). */
internal fun createInTree(
    context: android.content.Context,
    tree: String,
    name: String,
    mime: String = "video/mp4"
): android.net.Uri? = runCatching {
    val treeUri = android.net.Uri.parse(tree)
    val parent = android.provider.DocumentsContract.buildDocumentUriUsingTree(
        treeUri,
        android.provider.DocumentsContract.getTreeDocumentId(treeUri)
    )
    android.provider.DocumentsContract.createDocument(
        context.contentResolver,
        parent,
        mime,
        name
    )
}.onFailure { android.util.Log.w("RiseExport", "pasta destino indisponível", it) }.getOrNull()
