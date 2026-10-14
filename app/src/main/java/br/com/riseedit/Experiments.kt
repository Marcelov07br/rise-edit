package br.com.riseedit

import android.content.Context
import android.net.Uri
import br.com.riseedit.core.motion.ByteSource
import br.com.riseedit.core.motion.TelemetryProbe
import br.com.riseedit.core.motion.TelemetryReport
import java.io.FileInputStream
import java.nio.ByteBuffer

/** Feature flags for experiments (Configurações → Experimentos). Off by default. */
internal object Experiments {
    private const val PREFS = "experiments"

    fun gyro(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("gyro", false)

    fun setGyro(context: Context, enabled: Boolean) = context.getSharedPreferences(
        PREFS,
        Context.MODE_PRIVATE
    ).edit().putBoolean("gyro", enabled).apply()

    /** Scans a clip's container for motion telemetry; reads boxes only, never decodes video. */
    fun telemetry(context: Context, uri: String): TelemetryReport? = runCatching {
        context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")?.use { pfd ->
            FileInputStream(pfd.fileDescriptor).channel.use { channel ->
                val source = ByteSource { position, length ->
                    val buffer = ByteBuffer.allocate(length)
                    channel.read(buffer, position)
                    buffer.array().copyOf(buffer.position())
                }
                TelemetryProbe.scan(source, channel.size())
            }
        }
    }.onFailure {
        br.com.riseedit.diagnostics.Diagnostics.log("giroscópio", "falha ao ler $uri", it)
    }
        .getOrNull()
}
