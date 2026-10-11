@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class, androidx.media3.common.util.ExperimentalApi::class])
package br.com.riseedit

import android.app.Application
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.*
import androidx.media3.transformer.*
import br.com.riseedit.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class EditorState(
    val project: Project = Project(), val selected: Int = 0,
    val positionMs: Long = 0, val playing: Boolean = false,
    val busy: String? = null, val progress: Float = 0f,
    val message: String? = null, val analyzed: Set<String> = emptySet(),
    val canUndo: Boolean = false, val canRedo: Boolean = false
)

class EditorViewModel(application: Application) : AndroidViewModel(application) {
    private val store = ProjectStore(application)
    private val mutable = MutableStateFlow(EditorState(busy = "Abrindo projeto…"))
    val state = mutable.asStateFlow()
    val player = CompositionPlayer.Builder(application).build()
    private val analyses = mutableMapOf<String, MotionAnalysis>()
    private val undo = ArrayDeque<Project>(); private val redo = ArrayDeque<Project>()
    private var operation: Job? = null
    private var transformer: Transformer? = null
    private var exportFile: File? = null
    private var exportDestination: Uri? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) { message("Falha na prévia: ${error.localizedMessage}") }
        })
        viewModelScope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) {
                    val project = store.load()
                    project.clips.map { it.uri }.distinct().forEach { uri -> store.loadAnalysis(uri)?.let { analyses[uri] = it } }
                    project
                }
                mutable.value = mutable.value.copy(project = loaded, analyzed = analyses.keys.toSet(), busy = null)
                rebuild()
            } catch (e: Exception) { message("Projeto salvo não pôde ser aberto: ${e.localizedMessage}"); mutable.value = mutable.value.copy(busy = null) }
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
                player.setComposition(VideoEngine.composition(state.value.project, analyses)); player.prepare()
                player.seekTo(state.value.positionMs.coerceIn(0, state.value.project.durationMs))
            }
        } catch (e: Exception) { message("Prévia indisponível: ${e.localizedMessage}") }
    }
    private fun edit(project: Project) {
        if (state.value.busy != null || project == state.value.project) return
        try { store.save(project) } catch (e: Exception) { message("Não foi possível salvar: ${e.localizedMessage}"); return }
        undo.addLast(state.value.project); if (undo.size > 30) undo.removeFirst(); redo.clear()
        mutable.value = state.value.copy(project = project, selected = state.value.selected.coerceIn(0, maxOf(0, project.clips.lastIndex)), canUndo = true, canRedo = false)
        rebuild()
    }
    fun undo() = history(undo, redo)
    fun redo() = history(redo, undo)
    private fun history(from: ArrayDeque<Project>, to: ArrayDeque<Project>) {
        if (state.value.busy != null || from.isEmpty()) return
        val project = from.last()
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
                val clips = withContext(Dispatchers.IO) {
                    uris.map { uri ->
                        getApplication<Application>().contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        val retriever = MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(getApplication(), uri)
                            check(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes") { "O arquivo não contém vídeo." }
                            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
                            var name = "Vídeo"
                            getApplication<Application>().contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                                if (it.moveToFirst()) name = it.getString(0)
                            }
                            Clip(uri = uri.toString(), name = name, sourceDurationMs = duration)
                        } finally { retriever.release() }
                    }
                }
                mutable.value = state.value.copy(busy = null)
                edit(state.value.project.copy(clips = state.value.project.clips + clips))
                message("${clips.size} vídeo(s) importado(s). Projeto salvo no aparelho.")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message("Importação falhou: ${e.localizedMessage}") }
            finally { mutable.value = state.value.copy(busy = null) }
        }
    }
    fun select(index: Int) {
        mutable.value = state.value.copy(selected = index)
        seek(state.value.project.clips.take(index).sumOf { it.durationMs })
    }
    fun seek(ms: Long) { player.seekTo(ms); mutable.value = state.value.copy(positionMs = ms) }
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
            finally { mutable.value = state.value.copy(busy = null) }
        }
    }
    fun export(destination: Uri) {
        if (state.value.busy != null || state.value.project.clips.isEmpty()) return
        if (state.value.project.clips.any { it.uri == destination.toString() }) { message("Escolha um novo arquivo para preservar o original."); return }
        player.pause(); mutable.value = state.value.copy(busy = "Exportando MP4 720p…", progress = 0f)
        val file = File(getApplication<Application>().cacheDir, "export-${System.nanoTime()}.mp4")
        exportFile = file; exportDestination = destination
        try {
            val engine = Transformer.Builder(getApplication())
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
    fun cancel() { operation?.cancel(); transformer?.cancel(); transformer = null; exportFile?.delete(); exportFile = null; mutable.value = state.value.copy(busy = null); message("Operação cancelada.") }
    override fun onCleared() { transformer?.cancel(); player.release(); exportFile?.delete() }
}
