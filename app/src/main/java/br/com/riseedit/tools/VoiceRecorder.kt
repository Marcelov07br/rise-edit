package br.com.riseedit.tools

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import br.com.riseedit.diagnostics.Diagnostics
import java.io.File

/**
 * Owns one [MediaRecorder] at a time. Every failure path releases the recorder and deletes a
 * partial file; [release] is idempotent and never throws, so lifecycle teardown cannot crash.
 */
class VoiceRecorder(
    private val context: Context,
    private val directory: File = File(context.filesDir, "recordings"),
    private val factory: (Context) -> MediaRecorder = ::newRecorder
) {
    sealed interface Result {
        data class Saved(val file: File, val durationMs: Long) : Result
        data class Failed(val message: String) : Result
    }

    private var recorder: MediaRecorder? = null
    private var file: File? = null
    var startedAt: Long = 0L
        private set
    val recording: Boolean get() = recorder != null

    /** Returns null on success or a user-facing error message. */
    fun start(): String? {
        if (recorder != null) return null
        val target = directory.apply {
            mkdirs()
        }.resolve("narracao-${System.currentTimeMillis()}.m4a")
        var created: MediaRecorder? = null
        return try {
            created = factory(context)
            created.setAudioSource(MediaRecorder.AudioSource.MIC)
            created.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            created.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            created.setAudioEncodingBitRate(128_000)
            created.setAudioSamplingRate(44_100)
            created.setOutputFile(target.absolutePath)
            created.prepare()
            created.start()
            recorder = created
            file = target
            startedAt = SystemClock.elapsedRealtime()
            Diagnostics.log("gravação", "início ${target.name}")
            null
        } catch (error: Exception) {
            Diagnostics.log("gravação", "falha ao iniciar", error)
            created?.let(::safeRelease)
            target.delete()
            "Não foi possível gravar: ${error.message ?: error.javaClass.simpleName}"
        }
    }

    /** Stops and finalizes. A stop right after start (no audio yet) is reported, not thrown. */
    fun stop(): Result {
        val active = recorder ?: return Result.Failed("Nenhuma gravação ativa.")
        val target = file
        val duration = SystemClock.elapsedRealtime() - startedAt
        recorder = null
        file = null
        val stopped = try {
            active.stop()
            true
        } catch (error: RuntimeException) {
            // MediaRecorder.stop throws when no valid audio was captured (e.g. stop right after start).
            Diagnostics.log("gravação", "stop falhou após ${duration}ms", error)
            false
        }
        safeRelease(active)
        Diagnostics.log("gravação", "fim ${target?.name} · ${duration}ms · ok=$stopped")
        return if (stopped && target != null && target.length() > 0) {
            Result.Saved(target, duration)
        } else {
            target?.delete()
            Result.Failed("Gravação curta demais. Grave por pelo menos um segundo.")
        }
    }

    /** Stops whatever is active; used when the panel closes or the app goes to background. */
    fun release(): Result? = if (recorder != null) stop() else null

    private fun safeRelease(target: MediaRecorder) {
        try {
            target.reset()
        } catch (error: Exception) {
            Diagnostics.log("gravação", "reset falhou", error)
        }
        try {
            target.release()
        } catch (error: Exception) {
            Diagnostics.log("gravação", "release falhou", error)
        }
    }

    private companion object {
        fun newRecorder(context: Context): MediaRecorder = if (Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.S
        ) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
    }
}
