package br.com.riseedit.editor

import android.content.Context
import android.net.Uri
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal class ExportDestinationWriter(private val context: Context) {
    suspend fun write(file: File, destination: Uri) = withContext(Dispatchers.IO) {
        val job = currentCoroutineContext()
        job.ensureActive()
        context.contentResolver.openOutputStream(destination, "wt")?.use { output ->
            file.inputStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    job.ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
                job.ensureActive()
                output.flush()
            }
        } ?: error("Destino indisponível")
        job.ensureActive()
    }
}
