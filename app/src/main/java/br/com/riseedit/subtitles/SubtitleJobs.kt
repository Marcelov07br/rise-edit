package br.com.riseedit.subtitles

import android.content.Context
import br.com.riseedit.core.Project
import br.com.riseedit.core.SpeechSegment
import br.com.riseedit.core.Subtitles
import br.com.riseedit.core.TextLayer
import br.com.riseedit.core.WhisperModel
import br.com.riseedit.diagnostics.Diagnostics
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class SubtitleStatus(
    val running: Boolean = false,
    val text: String = "",
    val progress: Float = 0f
)

/** Model download + on-device transcription; survives the panel being closed. */
internal object SubtitleJobs {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutable = MutableStateFlow(SubtitleStatus())
    val status: StateFlow<SubtitleStatus> = mutable
    private var job: Job? = null

    fun modelFile(context: Context, model: WhisperModel) =
        File(File(context.filesDir, "whisper").apply { mkdirs() }, model.file)

    fun downloaded(context: Context) = WhisperModel.entries.filter {
        modelFile(context, it).exists()
    }

    fun cancel() {
        WhisperNative.abort()
        job?.cancel()
        mutable.value = SubtitleStatus(text = "Cancelado.")
    }

    fun delete(context: Context, model: WhisperModel) {
        modelFile(context, model).delete()
        mutable.value = SubtitleStatus(text = "Modelo ${model.label} removido.")
    }

    @Suppress("LongMethod")
    fun start(
        context: Context,
        project: Project,
        model: WhisperModel,
        language: String,
        done: (List<TextLayer>) -> Unit
    ) {
        if (job?.isActive == true) return
        val app = context.applicationContext
        job = scope.launch {
            try {
                val file = modelFile(app, model)
                if (!file.exists()) download(model, file)
                if (!WhisperNative.available) {
                    error(
                        "Biblioteca Whisper indisponível neste aparelho."
                    )
                }
                mutable.value = SubtitleStatus(true, "Carregando ${model.label}…")
                val handle = WhisperNative.init(file.absolutePath)
                if (handle == 0L) error("Modelo inválido. Apague e baixe de novo.")
                val segments = mutableListOf<SpeechSegment>()
                try {
                    var offset = 0L
                    project.clips.forEachIndexed { i, clip ->
                        ensureActive()
                        val label = "Clipe ${i + 1}/${project.clips.size}"
                        mutable.value =
                            SubtitleStatus(
                                true,
                                "$label: lendo áudio…",
                                i.toFloat() / project.clips.size
                            )
                        val pcm = runCatching {
                            AudioDecoder.decode(app, clip.uri, clip.startMs, clip.endMs)
                        }.onFailure { Diagnostics.log("legendas", "decodificação falhou", it) }
                            .getOrDefault(FloatArray(0))
                        if (pcm.size > 16_000 / 2) {
                            mutable.value =
                                SubtitleStatus(
                                    true,
                                    "$label: transcrevendo…",
                                    i.toFloat() / project.clips.size
                                )
                            val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
                            val raw = WhisperNative.transcribe(handle, pcm, language, threads)
                                ?.decodeToString() ?: ""
                            segments += Subtitles.mapClip(Subtitles.parse(raw), clip, offset)
                        }
                        offset += clip.durationMs
                    }
                } finally {
                    WhisperNative.free(handle)
                }
                ensureActive()
                val layers = Subtitles.layers(segments, project.durationMs)
                withContext(Dispatchers.Main) { done(layers) }
                mutable.value = SubtitleStatus(text = "Pronto: ${layers.size} legendas.")
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                Diagnostics.log("legendas", "falha", error)
                mutable.value =
                    SubtitleStatus(text = "Falhou: ${error.message ?: error.javaClass.simpleName}")
            }
        }
    }

    private suspend fun download(model: WhisperModel, target: File) = withContext(Dispatchers.IO) {
        val part = File(target.path + ".part")
        var url = URL(model.url)
        var connection: HttpURLConnection
        var redirects = 0
        while (true) {
            connection = url.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 20_000
            connection.readTimeout = 60_000
            val code = connection.responseCode
            if (code in 300..399 && redirects < 6) {
                url = URL(url, connection.getHeaderField("Location"))
                connection.disconnect()
                redirects++
                continue
            }
            if (code != 200) error("Download HTTP $code")
            break
        }
        val total = connection.contentLengthLong.takeIf { it > 0 } ?: (model.approxMb * 1_000_000L)
        connection.inputStream.use { input ->
            part.outputStream().use { output ->
                val buffer = ByteArray(1 shl 16)
                var read = 0L
                while (isActive) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    output.write(buffer, 0, n)
                    read += n
                    mutable.value = SubtitleStatus(
                        true,
                        "Baixando ${model.label}: ${read / 1_000_000}/${total / 1_000_000} MB",
                        (read.toFloat() / total).coerceIn(0f, 1f)
                    )
                }
            }
        }
        ensureActive()
        if (!part.renameTo(target)) error("Não foi possível salvar o modelo.")
    }
}
