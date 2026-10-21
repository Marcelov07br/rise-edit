package br.com.riseedit

import br.com.riseedit.core.MediaFault
import br.com.riseedit.core.Project
import br.com.riseedit.core.Selection
import br.com.riseedit.preview.PreviewQuality

data class EditorState(
    val project: Project = Project(),
    val selected: Int = 0,
    val positionMs: Long = 0,
    val playing: Boolean = false,
    val busy: String? = null,
    val progress: Float = 0f,
    val message: String? = null,
    /** The message offers "Desfazer" (history undo). */
    val messageUndo: Boolean = false,
    val attributeDialog: AttributeDialog? = null,
    val analyzed: Set<String> = emptySet(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val selectionId: String? = null,
    val comparing: Boolean = false,
    val muted: Boolean = false,
    val previewQuality: PreviewQuality = PreviewQuality.Fast,
    val previewRequested: PreviewQuality = PreviewQuality.Faithful,
    val previewDegraded: String? = null,
    val previewGeneration: Int = 0,
    val previewFault: MediaFault? = null,
    val missingMedia: List<br.com.riseedit.core.MissingMedia> = emptyList()
) {
    /** Typed single selection, revalidated against the current project (deleted target → None). */
    val selection: Selection get() = Selection.of(project, selectionId)

    /** Id of the selected audio/overlay/text layer; null in the main-clip context. */
    val layerId: String? get() = selection.let {
        if (it is Selection.None ||
            it is Selection.Clip
        ) {
            null
        } else {
            it.id
        }
    }
}

/** Copy/paste attribute checklist: [paste] false = copying from the selected clip. */
data class AttributeDialog(
    val paste: Boolean,
    val shown: List<br.com.riseedit.core.editing.AttributeGroup>,
    val initial: Set<br.com.riseedit.core.editing.AttributeGroup>
)
