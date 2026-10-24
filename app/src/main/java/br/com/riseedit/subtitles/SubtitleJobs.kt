package br.com.riseedit.subtitles

import android.content.Context
import br.com.riseedit.core.Project
import br.com.riseedit.core.SpeechChunks
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

    /** Marker written after the SHA-256 matched (models from before 0.24.0 are checked once). */
    private fun verifiedMarker(file: File) = File(file.path + ".sha256-ok")

    private suspend fun verify(model: WhisperModel, file: File) = withContext(Dispatchers.IO) {
        if (verifiedMarker(file).exists()) return@withContext
        mutable.value = SubtitleStatus(true, "Verificando ${model.label} (SHA-256)…", 0f)
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        if (hex != model.sha256) {
            file.delete()
            error("Modelo corrompido (SHA-256 não confere). Baixe de novo.")
        }
        verifiedMarker(file).writeText(hex)
    }

    fun cancel() {
        WhisperNative.abort()
        job?.cancel()
        mutable.value = SubtitleStatus(text = "Cancelado — legendas já transcritas foram mantidas.")
    }

    fun delete(context: Context, model: WhisperModel) {
        modelFile(context, model).delete()
        verifiedMarker(modelFile(context, model)).delete()
        mutable.value = SubtitleStatus(text = "Modelo ${model.label} removido.")
    }

    /**
     * 0.25.0: progressive. Each clip's audio goes through the energy VAD; silence is cut and
     * the speech is transcribed in ≤28 s glued chunks, in order. After every chunk [partial]
     * gets all captions so far (shown at once, no undo step); [done] commits them once (one
     * undo step) — also after a cancel or a failure, keeping what was already transcribed.
     */
    @Suppress("LongMethod", "CyclomaticComplexMethod")
    fun start(
        context: Context,
        project: Project,
        model: WhisperModel,
        language: String,
        partial: (List<TextLayer>) -> Unit = {},
        done: (List<TextLayer>) -> Unit
    ) {
        if (job?.isActive == true) return
        val app = context.applicationContext
        val group = java.util.UUID.randomUUID().toString()
        job = scope.launch {
            val segments = mutableListOf<SpeechSegment>()
            var finished = false
            fun captions() = Subtitles.layers(segments, project.durationMs, group)
            try {
                val file = modelFile(app, model)
                if (!file.exists()) download(model, file)
                verify(model, file)
                if (!WhisperNative.cpuSupported) {
                    error("Este processador não tem ARMv8.2 (dotprod/fp16) exigido pelo Whisper.")
                }
                if (!WhisperNative.available) {
                    error(
                        "Biblioteca Whisper indisponível neste aparelho."
                    )
                }
                mutable.value = SubtitleStatus(true, "Carregando ${model.label}…")
                val handle = WhisperNative.init(file.absolutePath)
                if (handle == 0L) error("Modelo inválido. Apague e baixe de novo.")
                val started = System.nanoTime()
                val threads = WhisperNative.threads
                try {
                    var offset = 0L
                    project.clips.forEachIndexed { i, clip ->
                        ensureActive()
                        val label = "Clipe ${i + 1}/${project.clips.size}"
                        val clipShare = 1f / project.clips.size
                        mutable.value =
                            SubtitleStatus(true, "$label: lendo áudio…", i * clipShare)
                        val pcm = runCatching {
                            AudioDecoder.decode(app, clip.uri, clip.startMs, clip.endMs)
                        }.onFailure { Diagnostics.log("legendas", "decodificação falhou", it) }
                            .getOrDefault(FloatArray(0))
                        val chunks = if (pcm.size > 16_000 / 2) {
                            SpeechChunks.plan(SpeechChunks.voiced(pcm))
                        } else {
                            emptyList()
                        }
                        chunks.forEachIndexed { c, chunk ->
                            ensureActive()
                            mutable.value = SubtitleStatus(
                                true,
                                "$label: transcrevendo trecho ${c + 1}/${chunks.size} · ${segments.size} legendas",
                                (i + c.toFloat() / chunks.size) * clipShare
                            )
                            val audio = SpeechChunks.glued(pcm, chunk)
                            val raw = WhisperNative.transcribe(handle, audio, language, threads)
                                ?.decodeToString() ?: ""
                            ensureActive()
                            val local = SpeechChunks.remap(Subtitles.parse(raw), chunk)
                            segments += Subtitles.mapClip(local, clip, offset)
                            val sofar = captions()
                            withContext(Dispatchers.Main) { partial(sofar) }
                        }
                        offset += clip.durationMs
                    }
                } finally {
                    WhisperNative.free(handle)
                }
                ensureActive()
                val layers = captions()
                finished = true
                withContext(Dispatchers.Main) { done(layers) }
                val seconds = (System.nanoTime() - started) / 1e9
                Diagnostics.log("legendas", "%s: %d legendas em %.1f s, %d threads".format(model.file, layers.size, seconds, threads))
                mutable.value = SubtitleStatus(
                    text = "Pronto: ${layers.size} legendas em %.0f s · modelo SHA-256 verificado.".format(seconds)
                )
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                Diagnostics.log("legendas", "falha", error)
                mutable.value =
                    SubtitleStatus(text = "Falhou: ${error.message ?: error.javaClass.simpleName}")
            } finally {
                if (!finished) {
                    val kept = captions()
                    withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main) { done(kept) }
                }
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
        val total = connection.contentLengthLong.takeIf { it > 0 } ?: model.bytes
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        connection.inputStream.use { input ->
            part.outputStream().use { output ->
                val buffer = ByteArray(1 shl 16)
                var read = 0L
                while (isActive) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    output.write(buffer, 0, n)
                    digest.update(buffer, 0, n)
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
        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        if (hex != model.sha256) {
            part.delete()
            error("Download corrompido (SHA-256 não confere). Tente de novo.")
        }
        if (!part.renameTo(target)) error("Não foi possível salvar o modelo.")
        verifiedMarker(target).writeText(hex)
    }
}
