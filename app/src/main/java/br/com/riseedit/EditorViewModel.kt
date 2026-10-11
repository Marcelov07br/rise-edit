@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class, androidx.media3.common.util.ExperimentalApi::class])
package br.com.riseedit

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.*
import androidx.media3.transformer.*
import androidx.media3.effect.MultipleInputVideoGraph
import br.com.riseedit.core.*
import br.com.riseedit.media.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class EditorState(
    val project: Project = Project(), val selected: Int = 0,
    val positionMs: Long = 0, val playing: Boolean = false,
    val busy: String? = null, val progress: Float = 0f,
    val message: String? = null, val analyzed: Set<String> = emptySet(),
    val canUndo: Boolean = false, val canRedo: Boolean = false, val selection: String? = null,
    val comparing: Boolean = false, val muted: Boolean = false
)

class EditorViewModel(application: Application) : AndroidViewModel(application) {
    private val store = ProjectStore(application)
    private val mutable = MutableStateFlow(EditorState())
    val state = mutable.asStateFlow()
    val player = CompositionPlayer.Builder(application).setVideoGraphFactory(MultipleInputVideoGraph.Factory()).build()
    private val analyses = mutableMapOf<String, MotionAnalysis>()
    private val undo = ArrayDeque<Project>(); private val redo = ArrayDeque<Project>()
    private var operation: Job? = null
    private var transformer: Transformer? = null
    private var exportFile: File? = null
    private var exportDestination: Uri? = null
    private val mediaFaults = mutableMapOf<String, MediaFault>()
    private fun mediaProgress(progress: Float, phase: String) { mutable.value = state.value.copy(progress = progress, busy = phase) }
    private fun report(error: Exception) = message((error as? MediaFault)?.displayMessage() ?: "Não foi possível ler este arquivo. Selecione uma cópia compatível e tente novamente.")
    private suspend fun finishOperation() { if (operation === currentCoroutineContext()[Job]) mutable.value = state.value.copy(busy = null) }
    private suspend fun trimProxyCache() {
        val history = (undo + redo).flatMap { it.clips + it.overlays.map { o -> o.clip } }.mapNotNull { it.proxyUri }
        val keep = withContext(Dispatchers.IO) { store.list().flatMap { it.clips + it.overlays.map { o -> o.clip } }.mapNotNull { it.proxyUri }.toSet() } + history
        ProxyTranscoder(getApplication()).trimCache(keep)
    }
    private suspend fun prepareClip(clip: Clip): Clip = try {
        MediaPreparation(getApplication()).prepare(clip, ::mediaProgress).also { mediaFaults.remove(clip.id) }
    } catch(e: CancellationException) { throw e }
    catch(e: Exception) { mediaFaults[clip.id] = e as? MediaFault ?: MediaFault.ImportFailed(cause=e); clip }

    init {
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) { message("Falha na prévia: ${error.localizedMessage}") }
        })
        viewModelScope.launch {
            while (isActive) {
                mutable.value = mutable.value.copy(positionMs = player.currentPosition.coerceAtLeast(0), playing = player.isPlaying)
                delay(100)
            }
        }
    }
    fun message(text: String?) { mutable.value = mutable.value.copy(message = text) }
    private fun rebuild() {
        player.pause()
        try {
            if (state.value.project.clips.isEmpty()) player.stop()
            else {
                val project = if (state.value.comparing) state.value.project.copy(clips = state.value.project.clips.map { it.copy(stabilization = it.stabilization.copy(enabled = false)) }) else state.value.project
                player.setComposition(VideoEngine.composition(project, analyses, preview = true)); player.prepare()
                player.seekTo(state.value.positionMs.coerceIn(0, state.value.project.durationMs))
            }
        } catch (e: Exception) { message("Prévia indisponível: ${e.localizedMessage}") }
    }
    private fun edit(candidate: Project) {
        if (state.value.busy != null || candidate == state.value.project) return
        val project = candidate.copy(modifiedAt = System.currentTimeMillis())
        try { store.save(project) } catch (e: Exception) { message("Não foi possível salvar: ${e.localizedMessage}"); return }
        undo.addLast(state.value.project); if (undo.size > 30) undo.removeFirst(); redo.clear()
        mutable.value = state.value.copy(project = project, selected = state.value.selected.coerceIn(0, maxOf(0, project.clips.lastIndex)), canUndo = true, canRedo = false)
        rebuild()
    }
    fun undo() = history(undo, redo)
    fun redo() = history(redo, undo)
    fun open(project: Project) {
        if (state.value.busy != null) return
        player.pause(); undo.clear(); redo.clear(); analyses.clear(); mediaFaults.clear()
        mutable.value = EditorState(project = project, busy = "Abrindo projeto…")
        operation = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { (project.clips + project.overlays.map { it.clip }).map { it.uri }.distinct().forEach { uri -> store.loadAnalysis(uri)?.let { analyses[uri] = it } } }
                val prepared = project.copy(clips = project.clips.map { prepareClip(it) }, overlays = project.overlays.map { it.copy(clip = prepareClip(it.clip)) })
                val usable = prepared.copy(clips = prepared.clips.map { if (it.stabilization.enabled && !analyses.containsKey(it.uri)) it.copy(stabilization = it.stabilization.copy(enabled = false)) else it })
                if (usable != project) withContext(Dispatchers.IO) { store.save(usable) }
                mutable.value = state.value.copy(project = usable, busy = null, analyzed = analyses.keys.toSet())
                rebuild()
                mediaFaults.values.firstOrNull()?.let { message(it.displayMessage()) }
                trimProxyCache()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.value = state.value.copy(busy = null); report(e) }
        }
    }
    fun compare() { mutable.value = state.value.copy(comparing = !state.value.comparing); rebuild() }
    fun mute() { player.volume = if (player.volume == 0f) 1f else 0f; mutable.value = state.value.copy(muted = player.volume == 0f) }
    fun projectSettings(ratio: Float, export: ExportSettings) = edit(state.value.project.copy(aspectRatio = ratio, export = export))
    fun clipSettings(clip: Clip) { val s = state.value; if (s.project.clips.getOrNull(s.selected)?.id == clip.id) edit(s.project.replace(s.selected, clip)) }
    fun selectLayer(id: String) { mutable.value = state.value.copy(selection = id) }
    fun text(layer: TextLayer) { val p = state.value.project; edit(p.copy(texts = if (p.texts.any { it.id == layer.id }) p.texts.map { if (it.id == layer.id) layer else it } else p.texts + layer)); selectLayer(layer.id) }
    fun audio(layer: AudioClip) { val p = state.value.project; edit(p.copy(audio = p.audio.map { if (it.id == layer.id) layer else it })) }
    fun overlay(layer: VideoOverlay) { val p = state.value.project; edit(p.copy(overlays = p.overlays.map { if (it.id == layer.id) layer else it })) }
    fun removeLayer() { val p = state.value.project; val id = state.value.selection; edit(p.copy(audio = p.audio.filter { it.id != id }, texts = p.texts.filter { it.id != id }, overlays = p.overlays.filter { it.id != id })); mutable.value = state.value.copy(selection = null) }
    fun moveLayer(id: String, offset: Long, snapping: Boolean) {
        val p = state.value.project
        val boundaries = listOf(0L,p.durationMs) + p.clips.runningFold(0L) { t,c -> t+c.durationMs }
        val time = (if (snapping) br.com.riseedit.core.Timeline.snap(offset,boundaries) else offset).coerceIn(0,maxOf(0,p.durationMs-1))
        edit(p.copy(audio = p.audio.map { if (it.id == id) it.copy(offsetMs = time) else it },
            overlays = p.overlays.map { if (it.id == id) it.copy(offsetMs = time) else it },
            texts = p.texts.map { if (it.id == id) it.copy(startMs = time,endMs = time+it.endMs-it.startMs) else it }))
    }
    fun importAudio(uris: List<Uri>, track: Int) = importLayers(uris, track, false)
    fun importOverlay(uris: List<Uri>, track: Int) = importLayers(uris, track, true)
    private fun importLayers(uris: List<Uri>, track: Int, video: Boolean) {
        if (uris.isEmpty() || state.value.busy != null || state.value.project.clips.isEmpty()) return
        mutable.value = state.value.copy(busy = "Importando mídia…")
        operation = viewModelScope.launch {
            try {
                val kind = if (video) MediaKind.OVERLAY else MediaKind.AUDIO
                val media = importMedia(uris, kind)
                mutable.value = state.value.copy(busy = null)
                val p = state.value.project; var offset = state.value.positionMs.coerceIn(0,maxOf(0,p.durationMs-1))
                if (video) edit(p.copy(overlays = p.overlays + media.map { c -> VideoOverlay(clip=c,offsetMs=offset,track=track).also { offset += c.durationMs } }))
                else edit(p.copy(audio = p.audio + media.map { c -> AudioClip(uri=c.uri,name=c.name,sourceDurationMs=c.sourceDurationMs,offsetMs=offset,track=track).also { offset += c.durationMs } }))
                trimProxyCache()
            } catch (e: CancellationException) { throw e } catch (e: Exception) { report(e) }
            finally { finishOperation() }
        }
    }
    private fun history(from: ArrayDeque<Project>, to: ArrayDeque<Project>) {
        if (state.value.busy != null || from.isEmpty()) return
        val project = from.last().copy(modifiedAt = System.currentTimeMillis())
        try { store.save(project) } catch (e: Exception) { message("Falha ao salvar: ${e.localizedMessage}"); return }
        to.addLast(state.value.project); from.removeLast()
        mutable.value = state.value.copy(project = project, selected = 0, canUndo = undo.isNotEmpty(), canRedo = redo.isNotEmpty())
        rebuild()
    }
    fun import(uris: List<Uri>) {
        if (uris.isEmpty() || state.value.busy != null) return
        mutable.value = state.value.copy(busy = "Importando vídeos…", progress = 0f)
        operation = viewModelScope.launch {
            try {
                val clips = importMedia(uris, MediaKind.MAIN_VIDEO)
                mutable.value = state.value.copy(busy = null)
                edit(state.value.project.copy(clips = state.value.project.clips + clips))
                if (clips.size == uris.size) message("${clips.size} vídeo(s) importado(s). Projeto salvo no aparelho.")
                trimProxyCache()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { report(e) }
            finally { finishOperation() }
        }
    }
    private suspend fun importMedia(uris: List<Uri>, kind: MediaKind): List<Clip> {
        val clips = mutableListOf<Clip>()
        val faults = mutableListOf<String>()
        uris.forEachIndexed { index, uri ->
            try {
                mediaProgress(index.toFloat()/uris.size, "Verificando mídia ${index+1}/${uris.size}…")
                clips += MediaImporter.readClip(getApplication(), uri, kind, ::mediaProgress)
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { faults += (e as? MediaFault)?.displayMessage() ?: "Um arquivo não pôde ser importado. Selecione uma cópia compatível." }
        }
        if (faults.isNotEmpty()) message("${clips.size}/${uris.size} arquivo(s) importado(s). ${faults.distinct().joinToString(" ")}")
        return clips
    }
    fun select(index: Int) {
        mutable.value = state.value.copy(selected = index, selection = null)
        seek(state.value.project.clips.take(index).sumOf { it.durationMs })
    }
    fun seek(ms: Long) { val bounded = ms.coerceIn(0,state.value.project.durationMs); player.seekTo(bounded); mutable.value = state.value.copy(positionMs = bounded) }
    fun togglePlay() { if (player.isPlaying) player.pause() else { if (player.playbackState == Player.STATE_ENDED) player.seekTo(0); player.play() } }
    fun trim(start: Long, end: Long) {
        val s = state.value; val clip = s.project.clips.getOrNull(s.selected) ?: return
        runCatching { edit(s.project.replace(s.selected, clip.trim(start, end))) }.onFailure { message("O corte precisa ter duração maior que zero.") }
    }
    fun split() {
        val s = state.value; val clip = s.project.clips.getOrNull(s.selected) ?: return
        val relative = s.positionMs - s.project.clips.take(s.selected).sumOf { it.durationMs }
        if (relative <= 0 || relative >= clip.durationMs) { message("Posicione o cursor dentro do clipe selecionado."); return }
        edit(s.project.split(s.selected, relative))
    }
    fun remove() { val s = state.value; edit(s.project.copy(clips = s.project.clips.filterIndexed { i, _ -> i != s.selected })) }
    fun move(delta: Int) { val s = state.value; edit(s.project.move(s.selected, delta)); mutable.value = state.value.copy(selected = (s.selected + delta).coerceIn(0, maxOf(0, s.project.clips.lastIndex))) }
    fun settings(settings: Stabilization) {
        val s = state.value; val clip = s.project.clips.getOrNull(s.selected) ?: return
        if (settings.enabled && !analyses.containsKey(clip.uri)) { message("Analise o movimento antes de ativar."); return }
        edit(s.project.replace(s.selected, clip.copy(stabilization = settings)))
    }
    fun analyze() {
        val s = state.value; val clip = s.project.clips.getOrNull(s.selected) ?: return
        if (s.busy != null) return
        player.pause(); mutable.value = s.copy(busy = "Analisando movimento…", progress = 0f)
        operation = viewModelScope.launch {
            try {
                val analysis = withContext(Dispatchers.Default) {
                    MotionAnalyzer(getApplication()).analyze(clip) { progress -> mutable.value = mutable.value.copy(progress = progress) }
                }
                withContext(Dispatchers.IO) { store.saveAnalysis(clip.uri, analysis) }
                analyses[clip.uri] = analysis
                mutable.value = state.value.copy(busy = null, analyzed = analyses.keys.toSet())
                settings(clip.stabilization.copy(enabled = true))
                message("Análise pronta. ${analysis.unreliableFrames} amostras sem rastreamento; confira a prévia.")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message("Análise falhou: ${e.localizedMessage}") }
            finally { finishOperation() }
        }
    }
    fun export(destination: Uri) {
        if (state.value.busy != null || state.value.project.clips.isEmpty()) return
        val currentIds = (state.value.project.clips + state.value.project.overlays.map { it.clip }).map { it.id }.toSet()
        mediaFaults.entries.firstOrNull { it.key in currentIds }?.value?.let { message(it.displayMessage()); return }
        val p = state.value.project
        if ((p.clips.map { it.uri } + p.audio.map { it.uri } + p.overlays.map { it.clip.uri }).any { it == destination.toString() }) { message("Escolha um novo arquivo para preservar o original."); return }
        player.pause(); mutable.value = state.value.copy(busy = "Exportando MP4 ${p.export.height}p…", progress = 0f)
        val file = File(getApplication<Application>().cacheDir, "export-${System.nanoTime()}.mp4")
        exportFile = file; exportDestination = destination
        try {
            val engine = Transformer.Builder(getApplication())
                .setEncoderFactory(DefaultEncoderFactory.Builder(getApplication()).setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(p.export.bitrate).build()).build())
                .setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        transformer = null
                        operation = viewModelScope.launch {
                            try {
                                mutable.value = state.value.copy(busy = "Salvando vídeo…")
                                withContext(Dispatchers.IO) {
                                    getApplication<Application>().contentResolver.openOutputStream(destination, "w")?.use { output ->
                                        file.inputStream().use { it.copyTo(output) }
                                    } ?: error("Destino indisponível")
                                }
                                exportDestination = null; message("Vídeo exportado. Os arquivos originais foram preservados.")
                            } catch (e: Exception) { message("Não foi possível salvar o vídeo: ${e.localizedMessage}") }
                            finally { file.delete(); exportFile = null; mutable.value = state.value.copy(busy = null) }
                        }
                    }
                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        transformer = null; file.delete(); exportFile = null
                        mutable.value = state.value.copy(busy = null); message("Exportação falhou: ${exportException.localizedMessage}")
                    }
                }).build()
            transformer = engine
            engine.start(VideoEngine.composition(state.value.project, analyses), file.absolutePath)
            operation = viewModelScope.launch {
                val holder = ProgressHolder()
                while (transformer != null) {
                    if (engine.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) mutable.value = state.value.copy(progress = holder.progress / 100f)
                    delay(300)
                }
            }
        } catch (e: Exception) { transformer = null; file.delete(); exportFile = null; mutable.value = state.value.copy(busy = null); message("Exportação indisponível: ${e.localizedMessage}") }
    }
    fun cancel() { val job=operation; operation=null; job?.cancel(); transformer?.cancel(); transformer = null; exportFile?.delete(); exportFile = null; mutable.value = state.value.copy(busy = null); message("Operação cancelada.") }
    override fun onCleared() { transformer?.cancel(); player.release(); exportFile?.delete() }
}
