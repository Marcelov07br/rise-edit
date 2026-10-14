@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package br.com.riseedit

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import br.com.riseedit.core.AudioClip
import br.com.riseedit.core.Clip
import br.com.riseedit.core.ExportSettings
import br.com.riseedit.core.Project
import br.com.riseedit.core.Stabilization
import br.com.riseedit.core.TextLayer
import br.com.riseedit.core.VideoOverlay
import br.com.riseedit.editor.EditorDocument
import br.com.riseedit.editor.EditorMedia
import br.com.riseedit.editor.EditorOperations
import br.com.riseedit.editor.EditorPreview
import br.com.riseedit.editor.ExportCoordinator
import br.com.riseedit.editor.ImportCoordinator
import br.com.riseedit.editor.LayerCommands
import br.com.riseedit.editor.MotionCoordinator
import br.com.riseedit.editor.ProjectCommands
import br.com.riseedit.editor.ProjectLoader
import br.com.riseedit.preview.PreviewQuality

/** UI facade. Coordinators share one document and one processing owner. */
class EditorViewModel(application: Application) : AndroidViewModel(application) {
    private val document = EditorDocument(ProjectStore(application))
        .also { br.com.riseedit.text.TextFonts.install(application) }
    val state = document.state
    private val preview = EditorPreview(application, viewModelScope, document)
    private val operations =
        EditorOperations(
            viewModelScope,
            document,
            preview.engine::pauseForProcessing,
            preview::rebuild
        )
    private val commands = ProjectCommands(document)
    private val layers = LayerCommands(document)
    private val media = EditorMedia(application, document)
    private val loader = ProjectLoader(application, document, operations, media)
    private val importer = ImportCoordinator(document, operations, media)
    private val motion = MotionCoordinator(application, document, operations)
    private val exporter = ExportCoordinator(application, document, operations)

    val player: Player get() = preview.engine.player
    val previewClip get() = preview.engine.activeClip
    val previewSourceMs get() = preview.engine.activeSourceMs
    fun motionAnalysis(uri: String) = document.analyses[uri]
    fun message(text: String?) = document.message(text)
    fun open(project: Project) = loader.open(project)
    fun undo() = document.undo()
    fun redo() = document.redo()

    fun previewQuality(quality: PreviewQuality) = preview.quality(quality)
    fun scrubbing(enabled: Boolean) = preview.engine.scrubbing(enabled)
    fun suspendPreview() = preview.engine.pauseForProcessing()
    fun pausePreview() = preview.engine.pause()
    fun compare() = preview.compare()
    fun mute() = preview.mute()
    fun select(index: Int) = preview.select(index)
    fun focus(index: Int) = preview.focus(index)
    fun seek(ms: Long) = preview.seek(ms)
    fun togglePlay() = preview.togglePlay()

    fun projectSettings(ratio: Float, export: ExportSettings) =
        commands.projectSettings(ratio, export)
    fun clipSettings(clip: Clip) = commands.clipSettings(clip)
    fun trim(start: Long, end: Long) = commands.trim(start, end)
    fun trimClip(original: Clip, trimmed: Clip): Boolean {
        if (!commands.trimClip(original, trimmed)) return false
        val current = state.value
        val index = current.project.clips.indexOfFirst { it.id == original.id }
        val offset = current.project.clips.take(index).sumOf { it.durationMs }
        preview.seek(current.positionMs.coerceIn(offset, offset + trimmed.durationMs - 1))
        return true
    }
    fun moveClip(id: String, target: Int) = commands.moveClip(id, target)
    fun separateAudio(track: Int = 0) = commands.separateAudio(track)
    fun split() = commands.split()
    fun remove() = commands.remove()
    fun move(delta: Int) = commands.move(delta)
    fun settings(settings: Stabilization) = commands.settings(settings)

    fun selectLayer(id: String) = layers.select(id)
    fun clearLayer() = layers.select(null)
    fun text(layer: TextLayer) = layers.text(layer)
    fun audio(layer: AudioClip) = layers.audio(layer)
    fun overlay(layer: VideoOverlay) = layers.overlay(layer)
    fun removeLayer() = layers.remove()
    fun moveLayer(id: String, offset: Long, snapping: Boolean) = layers.move(id, offset, snapping)
    fun trimOverlay(original: VideoOverlay, trimmed: VideoOverlay) =
        layers.trimOverlay(original, trimmed)
    fun trimAudio(original: AudioClip, trimmed: AudioClip) = layers.trimAudio(original, trimmed)

    fun import(uris: List<Uri>) = importer.videos(uris)
    fun importAudio(uris: List<Uri>, track: Int) = importer.layers(uris, track, video = false)
    fun importOverlay(uris: List<Uri>, track: Int) = importer.layers(uris, track, video = true)
    fun analyze() = motion.analyze()
    fun export(destination: Uri) = exporter.export(destination)
    fun cancel() = operations.cancel()
    fun reconnectMedia(oldUri: String, newUri: Uri) = loader.reconnect(oldUri, newUri)
    fun clearMissing() = document.update { it.copy(missingMedia = emptyList()) }
    fun restoreProjectVersion(stamp: Long) = loader.restoreVersion(stamp)
    fun projectVersions(): List<Long> = document.store.versions(state.value.project.id).map {
        it.first
    }

    override fun onCleared() {
        operations.cancel()
        preview.engine.release()
        super.onCleared()
    }
}
