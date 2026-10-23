package br.com.riseedit.transcode

import android.content.Context
import android.net.Uri
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** 0.21: FFmpeg prores_ks (LGPL, software) — decode → YUV422P10LE → ProRes 422 → .mov. */
object ProresNative {
    fun interface Progress { fun onProgress(ptsUs: Long): Boolean }

    /** 0 = ok, 1 = cancelled, <0 = FFmpeg error. [profile] 3 = HQ (apch), 2 = standard (apcn). */
    @JvmStatic external fun nativeEncode(fd: Int, outPath: String, profile: Int, pcm: Boolean, progress: Progress): Int
}

class ProresExporter(private val context: Context) {
    suspend fun encode(source: Uri, out: File, profile: Int, pcm: Boolean, durationUs: Long, progress: (Float) -> Unit) =
        withContext(Dispatchers.IO) {
            check(FfmpegIngest.available) { "ProRes indisponível neste aparelho." }
            val partial = File(out.parentFile, out.name + ".partial.mov").apply { delete() }
            val ctx = coroutineContext
            val pfd = context.contentResolver.openFileDescriptor(source, "r") ?: error("Arquivo inacessível.")
            try {
                val r = ProresNative.nativeEncode(pfd.fd, partial.absolutePath, profile, pcm) { pts ->
                    if (durationUs > 0) progress((pts.toFloat() / durationUs).coerceIn(0f, 1f))
                    ctx.isActive
                }
                if (r == 1) throw kotlinx.coroutines.CancellationException("ProRes cancelado")
                check(r == 0 && partial.length() > 0) { "Falha no ProRes ($r)." }
                check(partial.renameTo(out)) { "Não foi possível salvar o .mov." }
                out
            } finally { pfd.close(); partial.delete() }
        }
}
