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
        job = scope.launch {
            try {
                val store = ProjectStore(applicationContext)
                val project = withContext(Dispatchers.IO) { store.load(projectId) }
                val analyses = withContext(Dispatchers.IO) {
                    (project.clips + project.overlays.map { it.clip }).map { it.uri }.distinct()
                        .mapNotNull { uri -> store.loadAnalysis(uri)?.let { uri to it } }.toMap()
                }
                val file = File.createTempFile("export-", ".mp4", cacheDir)
                try {
                    check(file.delete())
                    withContext(Dispatchers.Main) {
                        ExportRenderer(applicationContext).render(project, analyses, file) { progress ->
                            ExportBus.progress(progress)
                            notify(progress, "Exportando MP4 ${project.export.height}p…")
                        }
                    }
                    notify(1f, "Salvando vídeo…")
                    ExportDestinationWriter(applicationContext).write(file, destination)
                    ExportBus.finish(
                        message = "Vídeo exportado. Os arquivos originais foram preservados."
                    )
                } finally {
                    withContext(Dispatchers.IO) { file.delete() }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                ExportBus.finish(cancelled = true)
                throw cancelled
            } catch (error: Exception) {
                ExportBus.finish(
                    message = "Não foi possível exportar: ${error.localizedMessage}. Tente em 720p."
                )
            } finally {
                running.set(false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
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

/** In-process bus: the editor observes progress without owning the encode job. */
object ExportBus {
    data class State(
        val active: Boolean = false,
        val projectId: String? = null,
        val progress: Float = 0f,
        val message: String? = null
    )

    private val mutable = kotlinx.coroutines.flow.MutableStateFlow(State())
    val state = mutable.asStateFlow()

    fun start(projectId: String) {
        mutable.value = State(active = true, projectId = projectId, progress = 0f)
    }

    fun progress(value: Float) {
        mutable.value = mutable.value.copy(progress = value)
    }

    fun message(text: String) {
        mutable.value = mutable.value.copy(message = text)
    }

    fun finish(message: String? = null, cancelled: Boolean = false) {
        mutable.value = State(
            active = false,
            message = message ?: if (cancelled) "Exportação cancelada." else null
        )
    }
}
