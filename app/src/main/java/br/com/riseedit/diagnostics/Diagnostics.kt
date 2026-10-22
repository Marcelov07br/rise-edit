package br.com.riseedit.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Minimal on-device diagnostics: an in-memory ring buffer of app events that is mirrored to a
 * file, uncaught-crash capture, and a shareable report (ring buffer + crash files + this app's
 * logcat + native exit reasons). Marce tests without adb, so everything is exported from the app.
 */
object Diagnostics {
    private const val TAG = "RiseEdit"
    const val CAPACITY = 1_000
    private val ring = LogRing(CAPACITY)
    private var directory: File? = null
    private var installed = false
    private val stamp get() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())

    @Synchronized
    fun install(context: Context) {
        if (installed) return
        installed = true
        val dir = File(context.filesDir, "diagnostics").apply { mkdirs() }
        directory = dir
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { recordCrash(thread, error) }
            previous?.uncaughtException(thread, error)
        }
        log(
            "app",
            "início · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (${Build.VERSION.SDK_INT})"
        )
    }

    fun log(tag: String, message: String, error: Throwable? = null) {
        val line =
            "$stamp [$tag] $message" +
                (error?.let { " · ${it.javaClass.simpleName}: ${it.message}" } ?: "")
        ring.add(line)
        if (error != null) Log.w(TAG, "[$tag] $message", error) else Log.i(TAG, "[$tag] $message")
        if (error != null) flush()
    }

    fun lines(): List<String> = ring.snapshot()

    /** 0.19.2: a multi-line report (full stack trace) kept in its own file for the shared log. */
    fun record(tag: String, body: String) {
        ring.add(
            "$stamp [$tag] ${body.lineSequence().firstOrNull().orEmpty()} (detalhes em error-*.txt)"
        )
        val dir = directory ?: return
        runCatching {
            File(dir, "error-${System.currentTimeMillis()}.txt").writeText("$stamp [$tag]\n$body")
            dir.listFiles { f ->
                f.name.startsWith("error-")
            }?.sortedByDescending { it.name }?.drop(5)?.forEach { it.delete() }
        }
        flush()
    }

    @Synchronized
    fun flush() {
        val dir = directory ?: return
        runCatching { File(dir, "ring.log").writeText(ring.snapshot().joinToString("\n")) }
    }

    private fun recordCrash(thread: Thread, error: Throwable) {
        val dir = directory ?: return
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }
        ring.add("$stamp [crash] thread=${thread.name}")
        File(dir, "crash-${System.currentTimeMillis()}.txt").writeText(
            "Crash em $stamp · thread ${thread.name}\n$trace\n--- registro ---\n" +
                ring.snapshot().joinToString("\n")
        )
        flush()
        // Keep only the five newest crash reports.
        dir.listFiles { f ->
            f.name.startsWith("crash-")
        }?.sortedByDescending { it.name }?.drop(5)?.forEach { it.delete() }
    }

    /** Builds the report in cache and returns a share-sheet intent. */
    fun shareIntent(context: Context, version: String): Intent {
        flush()
        val report = File(context.cacheDir, "diagnostics").apply {
            mkdirs()
        }.resolve("rise-edit-registro.txt")
        report.writeText(buildReport(context, version))
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", report)
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "Rise Edit $version · registro de erro")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(
            send,
            "Exportar registro de erro"
        ).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun buildReport(context: Context, version: String): String = buildString {
        appendLine(
            "Rise Edit $version · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
        )
        appendLine("Gerado em $stamp")
        appendLine("Aparelho: ${ColorDiagnostics.device()}")
        val dir = directory ?: File(context.filesDir, "diagnostics")
        dir.listFiles { f ->
            f.name.startsWith("crash-")
        }?.sortedByDescending { it.name }?.forEach {
            appendLine("\n=== ${it.name} ===")
            appendLine(it.readText().take(64_000))
        }
        dir.listFiles { f ->
            f.name.startsWith("error-")
        }?.sortedByDescending { it.name }?.forEach {
            appendLine("\n=== ${it.name} ===")
            appendLine(it.readText().take(64_000))
        }
        appendLine("\n=== Saídas anteriores do processo ===")
        append(exitReasons(context))
        appendLine("\n=== Registro do app (sessão atual) ===")
        ring.snapshot().forEach(::appendLine)
        File(dir, "ring.log").takeIf { it.exists() }?.let {
            appendLine("\n=== Registro salvo ===")
            appendLine(it.readText().takeLast(64_000))
        }
        appendLine("\n=== logcat (este app) ===")
        appendLine(logcat())
    }

    private fun exitReasons(context: Context): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return "Indisponível neste Android.\n"
        return runCatching {
            val manager = context.getSystemService(ActivityManager::class.java)
            buildString {
                manager.getHistoricalProcessExitReasons(context.packageName, 0, 5).forEach { info ->
                    appendLine(
                        "${Date(info.timestamp)} · motivo ${info.reason} · ${info.description}"
                    )
                    if (info.reason == ApplicationExitInfo.REASON_CRASH_NATIVE ||
                        info.reason == ApplicationExitInfo.REASON_ANR
                    ) {
                        info.traceInputStream?.use {
                            appendLine(it.bufferedReader().readText().take(48_000))
                        }
                    }
                }
            }
        }.getOrElse { "Falha ao ler: ${it.message}\n" }
    }

    private fun logcat(): String = runCatching {
        val process = ProcessBuilder(
            "logcat",
            "-d",
            "-v",
            "threadtime",
            "-t",
            "4000"
        ).redirectErrorStream(true).start()
        process.inputStream.bufferedReader().use { it.readText() }.takeLast(400_000)
    }.getOrElse { "logcat indisponível: ${it.message}" }
}

/** Fixed-size, thread-safe ring of log lines. */
class LogRing(private val capacity: Int) {
    private val lines = ArrayDeque<String>()

    @Synchronized
    fun add(line: String) {
        if (lines.size == capacity) lines.removeFirst()
        lines.addLast(line)
    }

    @Synchronized
    fun snapshot(): List<String> = lines.toList()
}
