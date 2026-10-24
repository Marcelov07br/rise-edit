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
    private val cuts = br.com.riseedit.editor.CutCommands(document, commands)
    private val layers = LayerCommands(document)
    private val media = EditorMedia(application, document)
    private val loader = ProjectLoader(application, document, operations, media)
    private val importer = ImportCoordinator(document, operations, media)
    private val conversion = br.com.riseedit.editor.ConversionCoordinator(application, document, operations, media)
    private val cutMedia = br.com.riseedit.editor.CutMediaCommands(application, document, operations, media)
    private val motion = MotionCoordinator(application, document, operations)
    private val reframer = br.com.riseedit.editor.ReframeCoordinator(application, document, operations)
    private val exporter = ExportCoordinator(application, document, operations)
    private val proxies = br.com.riseedit.editor.ProxyCoordinator(application, viewModelScope, document)
        .also { it.watch() }

    /** 0.24.0: "Usar proxies" in the preview (export always reads originals). */
    fun useProxies(on: Boolean) = document.attach(document.current.project.copy(useProxies = on))
    fun generateProxies() = proxies.scan(force = true)

    val player: Player get() = preview.engine.player
    val previewClip get() = preview.engine.activeClip
    val previewSourceMs get() = preview.engine.activeSourceMs
    val scrubPending get() = preview.engine.scrubPending
    val isScrubbing get() = preview.engine.isScrubbing
    fun motionAnalysis(uri: String) = document.analyses[uri]
    fun message(text: String?) = document.message(text)
    val appContext: android.content.Context get() = getApplication()
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
    fun stepFrame(deltaFrames: Int) {
        // 0.19: real (possibly NTSC fractional) frame duration, rounded per step from the start.
        val rate = state.value.project.export.frameRate.coerceIn(23.0, 60.0)
        val frame =
            (state.value.positionMs * rate / 1000.0).let { kotlin.math.round(it) } + deltaFrames
        val target = kotlin.math.round(frame * 1000.0 / rate).toLong()
            .coerceIn(0, state.value.project.durationMs)
        pausePreview()
        seek(target)
    }

    fun projectSettings(ratio: Float, export: ExportSettings) =
        commands.projectSettings(ratio, export)
    fun clipSettings(clip: Clip) = commands.clipSettings(clip)
    fun background(bg: br.com.riseedit.core.FrameBackground) = commands.background(bg)
    fun transformSelected(t: br.com.riseedit.core.TransformKeyframe) =
        commands.transformSelected(previewSourceMs, t)
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
    fun split() = cuts.split()
    fun remove() = commands.remove()
    fun duplicate() = cuts.duplicate()
    fun cutToCursor(left: Boolean) = cuts.cutToCursor(left)
    fun rippleTrim(toStart: Boolean) {
        cuts.rippleTrim(toStart)?.let { seek(it) }
    }
    fun copyClip() = cuts.copyClip()
    fun pasteClip() = cuts.pasteClip()
    fun nudge(edge: br.com.riseedit.core.editing.TrimEdge, frames: Int) = cuts.nudge(edge, frames)
    fun speed(value: Double, keepPitch: Boolean) = cuts.speed(value, keepPitch)

    // 0.21 cut tools.
    fun speedCurve(knots: List<br.com.riseedit.core.editing.SpeedCurve.Knot>) = cuts.speedCurve(knots)
    fun freezeFrame(durationMs: Long) = cutMedia.freezeFrame(durationMs)
    fun reverseClip() = cutMedia.reverse()
    fun replaceMedia(uri: Uri?) = cutMedia.replace(uri)

    fun convertForEditing() = conversion.convert()
    fun dismissConversion() = conversion.dismiss()

    /** Opens the app's own picker in "replace selected clip" mode (EditorDialogs). */
    val replacePicker = androidx.compose.runtime.mutableStateOf(false)

    /** 0.18: "Excluir" closes the gap on every track (ripple) when on; kept for the session. */
    private val ripple = androidx.compose.runtime.mutableStateOf(
        br.com.riseedit.settings.SettingsStore.current.ripple
    )
    var rippleMode: Boolean
        get() = ripple.value
        set(v) {
            ripple.value = v
        }
    fun deleteSelected() = if (rippleMode) rippleDelete() else remove()
    fun move(delta: Int) = commands.move(delta)
    fun settings(settings: Stabilization) = commands.settings(settings)
    fun copyAttributes() = commands.copyAttributes()
    fun pasteAttributes() = commands.pasteAttributes()
    fun confirmCopy(groups: Set<br.com.riseedit.core.editing.AttributeGroup>) =
        commands.confirmCopy(groups)
    fun confirmPaste(groups: Set<br.com.riseedit.core.editing.AttributeGroup>, all: Boolean) =
        commands.confirmPaste(groups, all)
    fun closeAttributeDialog() = commands.closeAttributeDialog()
    fun resetColor() = commands.resetColor()
    fun applyColorMatch(
        result: Map<String, Pair<Long, br.com.riseedit.core.ColorAdjust>>,
        reference: String
    ) = commands.applyColorMatch(result, reference)
    fun applyColorToAll() {
        val s = state.value
        val clip = s.project.clips.getOrNull(s.selected) ?: return
        commands.applyColorToAll(clip)
    }
    val hasClipboard get() = commands.hasClipboard
    fun rippleDelete() = commands.rippleDelete()
    fun toggleMarker() = commands.toggleMarker()
    fun jumpMarker(forward: Boolean) {
        val s = state.value
        val target = if (forward) {
            br.com.riseedit.core.editing.Markers.next(s.project, s.positionMs)
        } else {
            br.com.riseedit.core.editing.Markers.previous(s.project, s.positionMs)
        }
        if (target != null) preview.seek(target) else message("Nenhum marcador nessa direção.")
    }
    fun jlCut(audioId: String, leadMs: Long, tailMs: Long) = commands.jlCut(audioId, leadMs, tailMs)
    fun addSubtitles(layers: List<TextLayer>) = commands.addTexts(layers)

    fun selectLayer(id: String) = layers.select(id)
    fun clearLayer() = layers.select(null)
    fun text(layer: TextLayer) = layers.text(layer)
    fun audio(layer: AudioClip) = layers.audio(layer)
    fun overlay(layer: VideoOverlay) = layers.overlay(layer)
    fun removeLayer() = layers.remove()
    fun adjustmentLayer(layer: br.com.riseedit.core.AdjustmentLayer) = layers.adjustment(layer)
    fun addAdjustmentLayer() = layers.addAdjustment()
    fun trimAdjustment(
        original: br.com.riseedit.core.AdjustmentLayer,
        trimmed: br.com.riseedit.core.AdjustmentLayer
    ) = layers.trimAdjustment(original, trimmed)
    fun moveLayer(id: String, offset: Long, snapping: Boolean) = layers.move(id, offset, snapping)
    fun trimOverlay(original: VideoOverlay, trimmed: VideoOverlay) =
        layers.trimOverlay(original, trimmed)
    fun trimAudio(original: AudioClip, trimmed: AudioClip) = layers.trimAudio(original, trimmed)

    fun import(uris: List<Uri>) = importer.videos(uris)
    fun importAudio(uris: List<Uri>, track: Int) = importer.layers(uris, track, video = false)
    fun importOverlay(uris: List<Uri>, track: Int) = importer.layers(uris, track, video = true)
    fun analyze() = motion.analyze()
    fun autoReframe(sourceRatio: Float) = reframer.run(sourceRatio)
    fun export(destination: Uri) = exporter.export(destination)
    fun cancel() = operations.cancel()
    fun reconnectMedia(oldUri: String, newUri: Uri) = loader.reconnect(oldUri, newUri)
    fun clearMissing() = document.update { it.copy(missingMedia = emptyList()) }
    fun restoreProjectVersion(stamp: Long) = loader.restoreVersion(stamp)
    fun projectVersions(): List<Long> = document.store.versions(state.value.project.id).map {
        it.first
    }

    /** Activity onStop / leaving the editor: make sure the last edit is on disk. */
    fun flushSave() = document.flush()

    override fun onCleared() {
        operations.cancel()
        document.saves.close()
        preview.engine.release()
        super.onCleared()
    }
}
