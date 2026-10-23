@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package br.com.riseedit.editor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media3.common.MimeTypes
import androidx.core.content.ContextCompat
import br.com.riseedit.MainActivity
import br.com.riseedit.ProjectStore
import br.com.riseedit.core.Project
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground export: the editor stays interactive while the service encodes to a temp file and
 * writes the chosen destination. Progress and result go through [ExportBus].
 */
class ExportService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            job?.cancel()
            ExportBus.finish(cancelled = true)
            stopSelf()
            return START_NOT_STICKY
        }
        val projectId = intent?.getStringExtra(EXTRA_PROJECT_ID) ?: return stop()
        val destination = intent.getStringExtra(EXTRA_DESTINATION)?.let(Uri::parse) ?: return stop()
        if (!running.compareAndSet(false, true)) {
            ExportBus.message("Já existe uma exportação em andamento.")
            return START_NOT_STICKY
        }
        ensureChannel()
        startForegroundCompat(buildNotification(0f, "Exportando…"))
        ExportBus.start(projectId)
        ExportBus.open()
        job = scope.launch {
            try {
                val store = ProjectStore(applicationContext)
                val project = withContext(Dispatchers.IO) { store.load(projectId) }
                val analyses = withContext(Dispatchers.IO) {
                    (project.clips + project.overlays.map { it.clip }).map { it.uri }.distinct()
                        .mapNotNull { uri -> store.loadAnalysis(uri)?.let { uri to it } }.toMap()
                }
                val e = project.export
                val route = br.com.riseedit.core.ExportOptions.route(e)
                val support = EncoderSupportCache.get()
                val tenBitHevc = e.bitDepth == 10 && support.hevc10
                val file = File.createTempFile("export-", ".mp4", cacheDir)
                val finished = File(cacheDir, "export-final-${System.nanoTime()}." +
                    br.com.riseedit.core.ExportOptions.extension(e))
                try {
                    check(file.delete())
                    // Stage 1: Transformer (all effects). DIRECT writes the final file; the 4:2:2
                    // routes write a high-bitrate HEVC intermediate (10-bit when the phone has Main10).
                    val spec = if (route == br.com.riseedit.core.ExportRoute.DIRECT) {
                        ExportRenderer.Spec(
                            if (e.codec == br.com.riseedit.core.VideoCodec.HEVC) MimeTypes.VIDEO_H265 else MimeTypes.VIDEO_H264,
                            e.bitrate,
                            keepHdr = e.bitDepth == 10 && e.codec == br.com.riseedit.core.VideoCodec.HEVC && support.hevc10
                        )
                    } else {
                        ExportRenderer.Spec(
                            MimeTypes.VIDEO_H265,
                            (e.bitrate * 2).coerceIn(80_000_000, br.com.riseedit.core.ExportSettings.MAX_BITRATE),
                            keepHdr = tenBitHevc
                        )
                    }
                    val weight = if (route == br.com.riseedit.core.ExportRoute.DIRECT) 1f else STAGE1
                    ExportBus.phase(if (weight < 1f) "Renderizando efeitos" else "Renderizando")
                    withContext(Dispatchers.Main) {
                        ExportRenderer(applicationContext).render(project, analyses, file, spec) { p ->
                            ExportBus.progress(p * weight)
                            notify(p * weight, "Exportando ${e.height}p · ${e.codec.label}…")
                        }
                    }
                    val output = when (route) {
                        br.com.riseedit.core.ExportRoute.DIRECT -> file
                        br.com.riseedit.core.ExportRoute.PRORES -> {
                            ExportBus.phase("Codificando ProRes 422 ${e.prores.label}")
                            br.com.riseedit.transcode.ProresExporter(applicationContext).encode(
                                Uri.fromFile(file), finished, e.prores.ffmpeg,
                                e.movAudio == br.com.riseedit.core.MovAudio.PCM, project.durationMs * 1000
                            ) { p -> stage2(p) }
                        }
                        br.com.riseedit.core.ExportRoute.HARDWARE_422 -> {
                            val kind = when (e.codec) {
                                br.com.riseedit.core.VideoCodec.APV -> br.com.riseedit.transcode.EncoderProbe.Kind.APV
                                br.com.riseedit.core.VideoCodec.H264 -> br.com.riseedit.transcode.EncoderProbe.Kind.AVC422
                                else -> br.com.riseedit.transcode.EncoderProbe.Kind.HEVC422
                            }
                            val size = br.com.riseedit.transcode.FfmpegIngest.probeSize(applicationContext, file)
                            val target = br.com.riseedit.transcode.EncoderProbe.target(kind, size.first, size.second, e.bitrate)
                                ?: error("o codificador ${e.codec.label} 4:2:2 não aceita ${size.first}x${size.second}")
                            ExportBus.phase("Codificando ${e.codec.label} 10 bits 4:2:2")
                            br.com.riseedit.transcode.ProxyConverter(applicationContext)
                                .convert(Uri.fromFile(file), finished, target) { p -> stage2(p) }.file
                        }
                    }
                    ExportBus.phase("Salvando")
                    notify(1f, "Salvando vídeo…")
                    ExportDestinationWriter(applicationContext).write(output, destination)
                    ExportBus.finish(
                        message = "Vídeo exportado. Os arquivos originais foram preservados.",
                        result = destination
                    )
                } finally {
                    withContext(Dispatchers.IO) { file.delete(); finished.delete() }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                ExportBus.finish(cancelled = true)
                throw cancelled
            } catch (error: Exception) {
                val code = (error as? androidx.media3.transformer.ExportException)?.errorCode
                ExportBus.finish(
                    message = if (code != null && code in 5000..5999) {
                        "A exportação falhou no processamento de imagem deste aparelho (${(error as androidx.media3.transformer.ExportException).errorCodeName}, $code). Nada foi salvo. Envie o registro em Configurações › Sobre."
                    } else {
                        "Não foi possível exportar: ${error.localizedMessage}. Tente em 720p."
                    }
                )
            } finally {
                running.set(false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun stage2(p: Float) {
        val v = STAGE1 + (1f - STAGE1) * p
        ExportBus.progress(v)
        notify(v, "Finalizando ${(v * 100).toInt()}%…")
    }

    override fun onDestroy() {
        scope.cancel()
        running.set(false)
        super.onDestroy()
    }

    private fun stop(): Int {
        stopSelf()
        return START_NOT_STICKY
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Exportação", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun notify(progress: Float, text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(progress, text))
    }

    private fun buildNotification(progress: Float, text: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cancel = PendingIntent.getService(
            this,
            1,
            Intent(this, ExportService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("Rise Edit")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, (progress * 100).toInt().coerceIn(0, 100), progress <= 0f)
            .setContentIntent(open)
            .addAction(0, "Cancelar", cancel)
            .build()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        /** 0.21: share of the progress bar for the Transformer pass when a finishing pass follows. */
        private const val STAGE1 = 0.55f
        private const val CHANNEL = "rise_export"
        private const val NOTIFICATION_ID = 42
        private const val EXTRA_PROJECT_ID = "projectId"
        private const val EXTRA_DESTINATION = "destination"
        private const val ACTION_CANCEL = "br.com.riseedit.export.CANCEL"
        private val running = AtomicBoolean(false)

        fun start(context: Context, project: Project, destination: Uri) {
            // Persist first so the service loads the same snapshot the user just confirmed.
            ProjectStore(context).save(project)
            val intent = Intent(context, ExportService::class.java)
                .putExtra(EXTRA_PROJECT_ID, project.id)
                .putExtra(EXTRA_DESTINATION, destination.toString())
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancel(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ExportService::class.java).setAction(ACTION_CANCEL)
            )
        }

        val isRunning: Boolean get() = running.get()
    }
}

/** 0.21: probing MediaCodecList is not free; once per process is enough. */
object EncoderSupportCache {
    @Volatile private var cached: br.com.riseedit.core.EncoderSupport? = null
    fun get(): br.com.riseedit.core.EncoderSupport =
        cached ?: br.com.riseedit.transcode.EncoderProbe.probe().also { cached = it }
}

/**
 * In-process bus: the editor observes progress without owning the encode job.
 * 0.21: also drives the render tab and the floating bubble ([panel]/[minimized]) across screens.
 */
object ExportBus {
    data class State(
        val active: Boolean = false,
        val projectId: String? = null,
        val progress: Float = 0f,
        val message: String? = null,
        val phase: String = "",
        val startedAt: Long = 0L,
        /** Render tab visible (full panel). */
        val panel: Boolean = false,
        /** Shrunk into the bubble. */
        val minimized: Boolean = false,
        /** Finished successfully; [result] opens the file. */
        val done: Boolean = false,
        val result: String? = null
    ) {
        /** Estimated remaining milliseconds, or null until there is enough progress. */
        fun remainingMs(now: Long): Long? {
            if (!active || progress < 0.03f || startedAt == 0L) return null
            val elapsed = now - startedAt
            return (elapsed * (1f - progress) / progress).toLong()
        }
    }

    private val mutable = kotlinx.coroutines.flow.MutableStateFlow(State())
    val state = mutable.asStateFlow()

    fun start(projectId: String) {
        mutable.value = State(active = true, projectId = projectId, progress = 0f,
            startedAt = System.currentTimeMillis(), phase = "Preparando")
    }

    fun progress(value: Float) {
        mutable.value = mutable.value.copy(progress = value)
    }

    fun phase(text: String) {
        mutable.value = mutable.value.copy(phase = text)
    }

    fun message(text: String) {
        mutable.value = mutable.value.copy(message = text)
    }

    fun open() { mutable.value = mutable.value.copy(panel = true, minimized = false) }

    fun minimize() { mutable.value = mutable.value.copy(panel = false, minimized = true) }

    /** Closes the bubble/tab after a finished (or failed) export. */
    fun dismiss() {
        if (!mutable.value.active) mutable.value = State() else minimize()
    }

    /** Test/preview hook. */
    fun set(state: State) { mutable.value = state }

    fun finish(message: String? = null, cancelled: Boolean = false, result: Uri? = null) {
        val old = mutable.value
        mutable.value = if (result != null) {
            State(active = false, message = message, progress = 1f, phase = "Concluído",
                panel = old.panel, minimized = old.minimized || !old.panel, done = true, result = result.toString())
        } else {
            State(active = false, message = message ?: if (cancelled) "Exportação cancelada." else null)
        }
    }
}
