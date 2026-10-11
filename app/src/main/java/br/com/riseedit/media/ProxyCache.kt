package br.com.riseedit.media

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import br.com.riseedit.core.ProxySpec
import java.io.File
import java.security.MessageDigest

internal class ProxyCache(private val context: Context, private val limitBytes: Long) {
    private val root = File(context.cacheDir, "proxies").apply { mkdirs() }

    fun file(uri: Uri, target: ProxySpec): File {
        var size = -1L
        var modified = -1L
        if (uri.scheme == "file") {
            File(requireNotNull(uri.path)).let { size = it.length(); modified = it.lastModified() }
        } else {
            runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED), null, null, null)?.use {
                    if (it.moveToFirst()) {
                        val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                        val modifiedIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                        if (sizeIndex >= 0 && !it.isNull(sizeIndex)) size = it.getLong(sizeIndex)
                        if (modifiedIndex >= 0 && !it.isNull(modifiedIndex)) modified = it.getLong(modifiedIndex)
                    }
                }
            }
        }
        val bytes = "$uri|$modified|$size|$target|sdr-v1".toByteArray(Charsets.UTF_8)
        val key = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        return File(root, "$key.mp4")
    }

    fun trim(keep: Set<String>) {
        val files = root.listFiles().orEmpty().filter { it.extension == "mp4" && !it.name.contains("partial") }
        var size = files.sumOf { it.length() }
        files.sortedBy { it.lastModified() }.forEach {
            if (size > limitBytes && Uri.fromFile(it).toString() !in keep) {
                val removed = it.length()
                if (it.delete()) size -= removed
            }
        }
    }

    fun deleteUnused(keep: Set<String>) {
        root.listFiles().orEmpty().filter { it.extension == "mp4" && !it.name.contains("partial") }
            .filter { Uri.fromFile(it).toString() !in keep }.forEach { it.delete() }
    }
}
