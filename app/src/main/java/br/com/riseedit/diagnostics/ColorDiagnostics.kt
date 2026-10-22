package br.com.riseedit.diagnostics

import android.content.Context
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.util.Log
import br.com.riseedit.core.Project
import java.io.PrintWriter
import java.io.StringWriter

/**
 * 0.19.2 (tag RiseColor): everything needed to diagnose a preview/export GL failure on a device
 * we don't have: full stack trace with causes, device identity, track formats and the decoders
 * MediaCodecList offers. Every device API is wrapped: custom ROMs may return empty lists or throw.
 */
object ColorDiagnostics {
    const val TAG = "RiseColor"

    fun device(): String = buildString {
        append(
            "${Build.MANUFACTURER} / ${Build.BRAND} / ${Build.MODEL} (${Build.DEVICE}, ${Build.HARDWARE})"
        )
        if (Build.VERSION.SDK_INT >=
            31
        ) {
            append(" · SoC ${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}")
        }
        append(" · Android ${Build.VERSION.RELEASE} SDK ${Build.VERSION.SDK_INT}")
        append(" · build ${Build.DISPLAY} · fingerprint ${Build.FINGERPRINT}")
    }

    fun trace(error: Throwable): String = StringWriter().also {
        error.printStackTrace(PrintWriter(it))
    }.toString()

    /** Root cause class + message (the "real" error, not the Media3 wrapper). */
    fun rootCause(error: Throwable): Throwable {
        var root = error
        var depth = 0
        while (depth++ < 16) root = root.cause?.takeIf { it !== root } ?: break
        return root
    }

    fun describeVideoTrack(format: MediaFormat): String {
        fun int(key: String) = runCatching {
            if (format.containsKey(key)) format.getInteger(key) else null
        }.getOrNull()
        val mime = format.getString(MediaFormat.KEY_MIME)
        return listOfNotNull(
            "mime=$mime",
            int(MediaFormat.KEY_WIDTH)?.let { w -> "${w}x${int(MediaFormat.KEY_HEIGHT)}" },
            int(MediaFormat.KEY_PROFILE)?.let { "profile=$it" },
            int(MediaFormat.KEY_LEVEL)?.let { "level=$it" },
            int(MediaFormat.KEY_COLOR_STANDARD)?.let { "colorStandard=$it" },
            int(MediaFormat.KEY_COLOR_TRANSFER)?.let { "colorTransfer=$it" },
            int(MediaFormat.KEY_COLOR_RANGE)?.let { "colorRange=$it" },
            bitDepth(mime, int(MediaFormat.KEY_PROFILE))?.let { "bitDepth=$it" },
            int(MediaFormat.KEY_FRAME_RATE)?.let { "fps=$it" }
        ).joinToString(" ")
    }

    /** Bit depth from the codec profile when the container does not say (null = unknown). */
    fun bitDepth(mime: String?, profile: Int?): Int? = when {
        profile == null -> null
        mime == MediaFormat.MIMETYPE_VIDEO_AVC -> if (profile == 0x10 ||
            profile == 0x20 ||
            profile == 0x40
        ) {
            10
        } else {
            8 // High10/422/444
        }
        mime == MediaFormat.MIMETYPE_VIDEO_HEVC -> if (profile == 1) 8 else 10
        mime?.contains("apv") == true -> 10
        else -> null
    }

    fun decoders(format: MediaFormat): String = runCatching {
        val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: return@runCatching "sem mime"
        val chosen = runCatching { list.findDecoderForFormat(format) }.getOrNull() ?: "nenhum"
        val all = list.codecInfos.orEmpty().filter { info ->
            !info.isEncoder &&
                info.supportedTypes.orEmpty().any { it.equals(mime, ignoreCase = true) }
        }.map { info ->
            val hw = if (Build.VERSION.SDK_INT >=
                29
            ) {
                (if (info.isHardwareAccelerated) "hw" else "sw")
            } else {
                "?"
            }
            "${info.name}($hw)"
        }
        "decodificador provável=$chosen · disponíveis=${all.ifEmpty {
            listOf("lista vazia")
        }.joinToString()}"
    }.getOrElse { "MediaCodecList falhou: ${it.javaClass.simpleName}: ${it.message}" }

    private fun tracks(context: Context, uri: String): String = runCatching {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, Uri.parse(uri), null)
            val index = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(
                    it
                ).getString(MediaFormat.KEY_MIME)?.startsWith("video/") ==
                    true
            } ?: return@runCatching "sem faixa de vídeo"
            val format = extractor.getTrackFormat(index)
            "${describeVideoTrack(format)} · ${decoders(format)}"
        } finally {
            extractor.release()
        }
    }.getOrElse { "não lido: ${it.javaClass.simpleName}: ${it.message}" }

    /** Logs the full report off the main thread (extractors do IO). */
    fun report(
        context: Context,
        where: String,
        error: Throwable,
        project: Project?,
        extra: String = ""
    ) {
        val app = context.applicationContext
        Thread({
            val clips = project?.let { (it.clips + it.overlays.map { o -> o.clip }) }.orEmpty()
            val body = buildString {
                appendLine("falha em $where $extra".trim())
                appendLine(
                    "causa raiz: ${rootCause(error).let {
                        "${it.javaClass.name}: ${it.message}"
                    }}"
                )
                appendLine("aparelho: ${device()}")
                appendLine(
                    "modo compatível de quadros: ${br.com.riseedit.media.FrameCompat.tolerant}"
                )
                clips.distinctBy { it.effectiveUri(true) }.take(6).forEach { clip ->
                    appendLine("clipe \"${clip.name}\": ${tracks(app, clip.effectiveUri(true))}")
                }
                append(trace(error))
            }
            Log.e(TAG, body)
            Diagnostics.record(TAG, body)
        }, "RiseColorDiag").start()
    }
}
