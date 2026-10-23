package br.com.riseedit.keys

import android.app.Activity
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.platform.LocalContext
import br.com.riseedit.EditorViewModel

/**
 * 0.22: drop files from DeX "Meus arquivos" / another app onto the editor. Read permission is
 * requested from the drag event (requestDragAndDropPermissions); videos/images go to the main
 * track, audio to audio track 1.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.externalMediaDrop(vm: EditorViewModel): Modifier {
    val activity = LocalContext.current as? Activity ?: return this
    val target = remember(vm, activity) {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val drag = event.toAndroidDragEvent()
                activity.requestDragAndDropPermissions(drag) ?: return false
                val clip = drag.clipData ?: return false
                val uris = (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
                if (uris.isEmpty()) return false
                val (audio, visual) = uris.partition { isAudio(activity, it) }
                if (visual.isNotEmpty()) vm.import(visual)
                if (audio.isNotEmpty()) vm.importAudio(audio, 0)
                return true
            }
        }
    }
    return dragAndDropTarget(
        shouldStartDragAndDrop = { e ->
            e.mimeTypes().any { it.startsWith("video/") || it.startsWith("image/") || it.startsWith("audio/") }
        },
        target = target
    )
}

private fun isAudio(activity: Activity, uri: Uri) =
    activity.contentResolver.getType(uri)?.startsWith("audio/") == true
